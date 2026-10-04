package com.webdav.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * End-to-end packaging seam and binary thinning invariant test.
 *
 * Verifies that:
 * 1. ProGuard rules protect Room, JNI, Media3 session, and FFmpeg without bloated wildcards.
 * 2. build.gradle.kts enforces 64-bit ABI filter, R8 minification, resource shrinking,
 *    and excludes material-icons-extended.
 * 3. When release APK is present, total APK size <= 7.5 MB, classes.dex <= 4.0 MB,
 *    and native libraries contain strictly arm64-v8a with zero 32-bit .so files.
 */
class PackagingSeamVerificationTest {
    @Test
    fun proguardRules_enforcePreciseKeepRulesWithoutBloatedWildcards() {
        val proguardFile = File("proguard-rules.pro")
        assertTrue("proguard-rules.pro must exist", proguardFile.exists())
        val content = proguardFile.readText()

        // Assert native JNI method bindings are preserved
        assertTrue(
            "Native method keep rule must be present",
            content.contains("native <methods>;"),
        )
        assertTrue(
            "FFmpeg decoder package keep rule must be present",
            content.contains("-keep class androidx.media3.decoder.ffmpeg.** { *; }"),
        )

        // Assert Room keep rules are present
        assertTrue(
            "Room database keep rule must be present",
            content.contains("extends androidx.room.RoomDatabase"),
        )
        assertTrue(
            "Room Entity keep rule must be present",
            content.contains("@androidx.room.Entity class * { *; }"),
        )
        assertTrue(
            "Room DAO keep rule must be present",
            content.contains("@androidx.room.Dao interface * { *; }"),
        )

        // Assert Media3 session and metadata keep rules are present
        assertTrue(
            "Media3 Session callback keep rule must be present",
            content.contains("androidx.media3.session.MediaSession\$Callback"),
        )
        assertTrue(
            "Media3 SessionService keep rule must be present",
            content.contains("androidx.media3.session.MediaSessionService"),
        )
        assertTrue(
            "MediaItem keep rule must be present",
            content.contains("class androidx.media3.common.MediaItem"),
        )
        assertTrue(
            "MediaMetadata keep rule must be present",
            content.contains("class androidx.media3.common.MediaMetadata"),
        )

        // Assert OkHttp and WebSocket keep rules are present
        assertTrue(
            "OkHttp PublicSuffixDatabase keep rule must be present",
            content.contains("class okhttp3.internal.publicsuffix.PublicSuffixDatabase"),
        )
        assertTrue(
            "OkHttp Interceptor keep rule must be present",
            content.contains("interface okhttp3.Interceptor"),
        )
        assertTrue(
            "OkHttp Authenticator keep rule must be present",
            content.contains("interface okhttp3.Authenticator"),
        )
        assertTrue(
            "WebSocket interface keep rule must be present",
            content.contains("interface okhttp3.WebSocket"),
        )
        assertTrue(
            "WebSocketListener keep rule must be present",
            content.contains("class okhttp3.WebSocketListener"),
        )

        // Assert bloated wildcard is NOT present
        assertFalse(
            "Overly broad '-keep class androidx.media3.** { *; }' must not be present to avoid DEX bloat",
            content.lines().any { line ->
                val trimmed = line.trim()
                trimmed == "-keep class androidx.media3.** { *; }" ||
                    trimmed == "-keep class androidx.media3.** {* ;}" ||
                    trimmed == "-keep class androidx.media3.**"
            },
        )
    }

    @Test
    fun buildGradle_enforcesPackagingSeamInvariants() {
        val buildGradleFile = File("build.gradle.kts")
        assertTrue("build.gradle.kts must exist", buildGradleFile.exists())
        val content = buildGradleFile.readText()

        // Assert 64-bit arm64-v8a only ABI filter
        assertTrue(
            "abiFilters must specify arm64-v8a",
            content.contains("\"arm64-v8a\""),
        )
        assertFalse(
            "armeabi-v7a must be stripped from build.gradle.kts",
            content.contains("\"armeabi-v7a\""),
        )

        // Assert R8 minification and resource shrinking
        assertTrue(
            "isMinifyEnabled must be true",
            content.contains("isMinifyEnabled = true"),
        )
        assertTrue(
            "isShrinkResources must be true",
            content.contains("isShrinkResources = true"),
        )

        // Assert material-icons-extended is pruned
        assertFalse(
            "material-icons-extended must not be present in dependencies",
            content.contains("material-icons-extended"),
        )

        // Assert dex legacy packaging is enabled for compressed DEX in standalone APK distribution
        assertTrue(
            "dex useLegacyPackaging must be true to enforce compressed DEX",
            content.contains("dex") && content.contains("useLegacyPackaging = true"),
        )
    }

    @Test
    fun releaseApk_whenPresent_satisfiesSizeBudgetAndEntryInvariants() {
        val apkFile = File("build/outputs/apk/release/app-release.apk")
        if (!apkFile.exists()) {
            // If release APK hasn't been assembled in this environment run, skip entry inspection
            return
        }

        val maxApkSizeBytes = 7_864_320L // 7.5 MB
        val maxDexUncompressedBytes = 4_194_304L // 4.0 MB

        // 1. Total APK size <= 7.5 MB
        assertTrue(
            "Release APK file size (${apkFile.length()} bytes) must be <= $maxApkSizeBytes bytes (7.5 MB)",
            apkFile.length() <= maxApkSizeBytes,
        )

        val zip = ZipFile(apkFile)
        try {
            val entries = zip.entries().toList()

            // 2. Exactly one classes.dex with uncompressed size <= 4.0 MB
            val dexEntries = entries.filter { it.name.endsWith(".dex") }
            assertEquals(
                "Must contain exactly 1 classes.dex file",
                1,
                dexEntries.size,
            )
            val dex = dexEntries.first()
            assertEquals("DEX entry must be named classes.dex", "classes.dex", dex.name)
            assertTrue(
                "classes.dex uncompressed size (${dex.size} bytes) must be <= $maxDexUncompressedBytes bytes (4.0 MB)",
                dex.size <= maxDexUncompressedBytes,
            )

            // 3. Native libraries contain strictly lib/arm64-v8a/
            val nativeLibs = entries.filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }
            val expectedLibs =
                setOf(
                    "lib/arm64-v8a/libavcodec.so",
                    "lib/arm64-v8a/libavformat.so",
                    "lib/arm64-v8a/libavutil.so",
                    "lib/arm64-v8a/libffmpegJNI.so",
                    "lib/arm64-v8a/libswresample.so",
                )
            val actualLibs = nativeLibs.map { it.name }.toSet()
            assertEquals("Native libraries must match expected arm64-v8a set", expectedLibs, actualLibs)

            val nonArm64 = nativeLibs.filter { !it.name.startsWith("lib/arm64-v8a/") }
            assertTrue(
                "Must contain zero non-arm64 native libraries",
                nonArm64.isEmpty(),
            )
        } finally {
            zip.close()
        }
    }
}
