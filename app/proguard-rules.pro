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
-keep class com.webdav.player.data.player.AsfExtractor { *; }

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
# Media3 Session callback & background service components
-keep class * extends androidx.media3.session.MediaSession$Callback { *; }
-keep class * extends androidx.media3.session.MediaSessionService { *; }

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
-dontwarn androidx.room.paging.**

# --------------------------------------------------------------------------- #
# Network & OkHttp / WebSocket / Okio
# --------------------------------------------------------------------------- #
# A resource is loaded with a package-relative path
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Protect OkHttp & WebSocket interfaces, callbacks, and connection listeners
-keep interface okhttp3.Interceptor { *; }
-keep interface okhttp3.Authenticator { *; }
-keep interface okhttp3.WebSocket { *; }
-keep class okhttp3.WebSocketListener { *; }
-keep class * extends okhttp3.WebSocketListener { *; }

# Suppress platform optional reflection warnings
-dontwarn okhttp3.**
-dontwarn okhttp3.internal.platform.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn javax.annotation.**

# Repackage all obfuscated classes into the root package to shrink DEX string pool
-repackageclasses ''

# --------------------------------------------------------------------------- #
# Kotlin Optimization & Thinning
# --------------------------------------------------------------------------- #
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNullParameter(java.lang.Object, java.lang.String);
    public static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    public static void checkNotNull(java.lang.Object);
    public static void checkNotNull(java.lang.Object, java.lang.String);
    public static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    public static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    public static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
    public static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    public static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
    public static void checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    public static void checkHasReturned(java.lang.Object, java.lang.String);
}

# Strip debug and verbose logging
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

-assumenosideeffects class androidx.media3.common.util.Log {
    public static void d(...);
    public static void v(...);
    public static void i(...);
    public static void w(...);
}

# --------------------------------------------------------------------------- #
# Kotlin Coroutines
# --------------------------------------------------------------------------- #
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
