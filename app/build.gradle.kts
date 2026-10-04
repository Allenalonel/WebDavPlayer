import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.webdav.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.webdav.player"
        minSdk = 29
        targetSdk = 34
        versionCode = 3
        versionName = "2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    ndkVersion = "27.0.12077973"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE*"
        }
        jniLibs {
            useLegacyPackaging = true
        }
        dex {
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // AndroidX & Lifecycle
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.02.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Jetpack DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Media3
    val media3Version = "1.2.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.register("verifyReleasePackaging") {
    group = "verification"
    description = "Enforces binary thinning and release packaging size budget invariants."
    dependsOn("assembleRelease")

    doLast {
        val apkFile = file("build/outputs/apk/release/app-release.apk")
        check(apkFile.exists()) { "Release APK not found at ${apkFile.absolutePath}" }

        val maxApkSizeBytes = 7_864_320L // 7.5 MB
        val maxDexUncompressedBytes = 4_194_304L // 4.0 MB
        val expectedNativeLibs: Set<String> = setOf(
            "lib/arm64-v8a/libavcodec.so",
            "lib/arm64-v8a/libavformat.so",
            "lib/arm64-v8a/libavutil.so",
            "lib/arm64-v8a/libffmpegJNI.so",
            "lib/arm64-v8a/libswresample.so",
        )

        val apkSize = apkFile.length()
        check(apkSize <= maxApkSizeBytes) {
            "Release APK size ($apkSize bytes) exceeds budget of $maxApkSizeBytes bytes (7.5 MB)"
        }

        val zip = ZipFile(apkFile)
        try {
            val entries: List<ZipEntry> = zip.entries().toList()
            val dexEntries = entries.filter { entry -> entry.name.endsWith(".dex") }
            check(dexEntries.size == 1) {
                "Expected exactly 1 classes.dex file, found ${dexEntries.size}: ${dexEntries.map { it.name }}"
            }

            val dex = dexEntries.first()
            check(dex.name == "classes.dex") {
                "Expected classes.dex, found ${dex.name}"
            }
            check(dex.size <= maxDexUncompressedBytes) {
                "classes.dex uncompressed size (${dex.size} bytes) exceeds budget of $maxDexUncompressedBytes bytes (4.0 MB)"
            }

            val nativeLibEntries = entries.filter { entry -> entry.name.startsWith("lib/") && entry.name.endsWith(".so") }
            val nativeLibNames: Set<String> = nativeLibEntries.map { it.name }.toSet()

            val unexpectedLibs = nativeLibNames.minus(expectedNativeLibs)
            check(unexpectedLibs.isEmpty()) {
                "Unexpected native libraries in APK: $unexpectedLibs"
            }

            val missingLibs = expectedNativeLibs.minus(nativeLibNames)
            check(missingLibs.isEmpty()) {
                "Missing expected native libraries in APK: $missingLibs"
            }

            val nonArm64Libs = nativeLibEntries.filter { entry -> !entry.name.startsWith("lib/arm64-v8a/") }
            check(nonArm64Libs.isEmpty()) {
                "Found non-arm64-v8a native libraries: ${nonArm64Libs.map { it.name }}"
            }

            println("============================================================")
            println("RELEASE PACKAGING VERIFICATION PASSED")
            println("============================================================")
            println("Total APK size:        $apkSize bytes (${String.format(Locale.US, "%.2f", apkSize / 1024.0 / 1024.0)} MB) [Budget: <= 7.5 MB]")
            println("classes.dex size:      ${dex.size} bytes (${String.format(Locale.US, "%.2f", dex.size / 1024.0 / 1024.0)} MB) [Budget: <= 4.0 MB]")
            println("classes.dex deflated:  ${dex.compressedSize} bytes (${String.format(Locale.US, "%.2f", dex.compressedSize / 1024.0 / 1024.0)} MB)")
            println("Native architecture:   lib/arm64-v8a/ only (5 libraries, 0 32-bit)")
            println("============================================================")
        } finally {
            zip.close()
        }
    }
}

