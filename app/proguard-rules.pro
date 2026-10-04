# =========================================================================== #
# WebDavPlayer ProGuard / R8 Rules
# =========================================================================== #

# --------------------------------------------------------------------------- #
# Native C++ JNI Bridge & Decoder Bindings
# --------------------------------------------------------------------------- #
# Protect all native method declarations across the app
-keepclasseswithmembernames class * {
    native <methods>;
}

# FFmpeg audio decoders, renderers, and bridges loaded by ExoPlayer / JNI
-keep class androidx.media3.decoder.ffmpeg.** { *; }

# Player engine, extractors (AsfExtractor), and native audio sources
-keep class com.webdav.player.data.player.** { *; }

# ExtractorInput interface methods invoked by native C++ (ffmpeg_demuxer.cc)
-keep interface androidx.media3.extractor.ExtractorInput {
    int read(byte[], int, int);
    long getPosition();
    long getLength();
    void skipFully(int);
}

# --------------------------------------------------------------------------- #
# Media3 (Session, ExoPlayer, Renderers, MediaItem, Metadata)
# --------------------------------------------------------------------------- #
-keep class androidx.media3.** { *; }

# Media3 Session callback & background service components
-keep class com.webdav.player.data.service.** { *; }

# Ensure MediaItem extras and bundle-based metadata survive
-keepclassmembers class androidx.media3.common.MediaItem { *; }
-keepclassmembers class androidx.media3.common.MediaMetadata { *; }

# --------------------------------------------------------------------------- #
# Room Database, DAOs, Entities, and Type Converters
# --------------------------------------------------------------------------- #
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.EntityDeletionOrUpdateAdapter { *; }
-keep class * extends androidx.room.SharedSQLiteStatement { *; }
-keep class * extends androidx.room.TypeConverter { *; }
-keep class com.webdav.player.data.local.** { *; }
-dontwarn androidx.room.paging.**

# --------------------------------------------------------------------------- #
# Domain Models (Playback state, AudioTrack, Server configuration)
# --------------------------------------------------------------------------- #
-keep class com.webdav.player.domain.model.** { *; }

# --------------------------------------------------------------------------- #
# Network & OkHttp / Okio
# --------------------------------------------------------------------------- #
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn javax.annotation.**

# --------------------------------------------------------------------------- #
# Kotlin Coroutines
# --------------------------------------------------------------------------- #
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
