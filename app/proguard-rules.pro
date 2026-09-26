# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# WebDAV / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Native JNI
-keepclasseswithmembernames class * {
    native <methods>;
}
