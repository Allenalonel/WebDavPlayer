# 02: Enable R8 Minification and Resource Shrinking

**What to build:**
Enable full R8 code minification and resource shrinking in `app/build.gradle.kts` for the `release` build type (`isMinifyEnabled = true` and `isShrinkResources = true`). Formulate comprehensive, hardened keep rules in `app/proguard-rules.pro` protecting Room DAOs/entities, Media3 Player/Session/ExoPlayer callbacks, OkHttp network models, Coroutines internal reflection, and Native C++ JNI bridge methods (`FfmpegAudioDecoder`, `AsfExtractor`, `ffmpeg_jni`).

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] Configure `isMinifyEnabled = true` and `isShrinkResources = true` in `buildTypes.release` in `app/build.gradle.kts`.
- [ ] Author explicit keep rules for Room database entities, DAOs, and type converters in `app/proguard-rules.pro`.
- [ ] Author explicit keep rules for Media3 session callbacks, MediaItem extras, and ExoPlayer renderers.
- [ ] Author native keep rules protecting all JNI method signatures and callbacks:
  `-keepclasseswithmembernames class * { native <methods>; }`
  `-keep class androidx.media3.decoder.ffmpeg.** { *; }`
  `-keep class com.webdav.player.data.player.** { *; }`
- [ ] Verify that release compilation completes without unresolved ProGuard warnings (`-dontwarn` scoped precisely where necessary).
