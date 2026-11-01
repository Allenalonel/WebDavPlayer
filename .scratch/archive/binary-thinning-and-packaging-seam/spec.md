# Binary Thinning and Packaging Seam Specification

Status: completed

## Problem Statement

In the current release build, WebDavPlayer produces an excessively large APK (**56.8 MB**), which is approximately 8 times larger than comparable minimalist players (such as Folder-Player at **7.08 MB**). Inspection of the binary breakdown reveals three primary packaging leaks and shallow engineering seams:

1. **DEX Codebase Bloat via `material-icons-extended` without R8 (44.56 MB DEX)**:
   The application depends on `androidx.compose.material:material-icons-extended` while keeping `isMinifyEnabled = false` in `app/build.gradle.kts`. Because tree-shaking is disabled, tens of thousands of unused vector icon classes and drawing paths are compiled directly into the release binary, generating 3 classes.dex files totalling **44.56 MB**.
2. **Dual-Architecture Native Library Redundancy (18.14 MB uncompressed / ~9 MB compressed SO)**:
   The application bundles native C++ FFmpeg libraries (`libavcodec.so`, `libavformat.so`, `libavutil.so`, `libswresample.so`, `libffmpegJNI.so`) for both `arm64-v8a` and `armeabi-v7a` into a single universal APK. In modern Android ecosystems (where `minSdk = 29`), 32-bit `armeabi-v7a` support is obsolete and bloats the download size by 100%.
3. **Absence of Resource Shrinking (`isShrinkResources`)**:
   Unused drawable resources, strings, and metadata remain unpruned in release distribution artifacts.

## Solution

Establish an authoritative, leak-free **Packaging Seam** and execute comprehensive binary thinning:

1. **Prune `material-icons-extended` & Establish Dedicated `AppIcons` Seam**:
   - Delete the dependency on `androidx.compose.material:material-icons-extended` from `app/build.gradle.kts`.
   - Create an explicit, localized `AppIcons` object containing only the ~15-20 icons actually used across the UI (using base `Icons.Default` or custom vector definitions).
   - This eliminates the source of the 40+ MB DEX bloat at the root.
2. **Enable Full R8 Code Minification and Resource Shrinking**:
   - Set `isMinifyEnabled = true` and `isShrinkResources = true` in `buildTypes.release`.
   - Author strict, battle-tested ProGuard keep rules for Room DAOs/entities, Media3 player/session, OkHttp/WebSocket, and JNI/Native C++ bindings (`FfmpegAudioDecoder`, `AsfExtractor`, `ffmpeg_jni`).
3. **Enforce 64-Bit Only (`arm64-v8a`) Native ABI Filtering**:
   - Restrict `ndk.abiFilters` in release packaging to `listOf("arm64-v8a")`, dropping obsolete 32-bit `armeabi-v7a`.
   - Strip debug symbols cleanly from native libraries.
4. **Enforce Compressed DEX via Legacy Packaging (`dex.useLegacyPackaging = true`)**:
   - In `app/build.gradle.kts`, configure `packaging.dex.useLegacyPackaging = true` to force DEFLATE compression of `classes.dex` (~1.90 MB compressed vs 4.09 MB uncompressed).
   - This directly eliminates a +2.19 MB download size inflation for standalone GitHub Release APK distribution, ensuring the package meets the <= 7.5 MB budget.
5. **Establish Size Budget and Verification Gate**:
   - Establish an automated release size budget: **Total Release APK <= 7.5 MB** (targeting ~6.5 MB, an 88% reduction).

## User Stories

1. As a mobile user downloading WebDavPlayer on cellular or metered data, I want the APK download to be under 8 MB, so that the install is nearly instantaneous and consumes negligible mobile data.
2. As a low-end device user, I want the app cold start and DEX class-loading time to be dramatically faster, so that launching the player from the launcher or notification is snappy.
3. As a developer modifying UI components, I want an explicit `AppIcons` catalog, so that I can see all icon assets in one local place without importing megabytes of unused material icons.
4. As a continuous delivery engineer, I want release builds to enforce a strict ProGuard rule set that protects Media3, Room, and JNI boundaries, so that R8 minification never causes runtime `ClassNotFoundException` or reflection breakages.
5. As an open-source maintainer, I want our architectural footprint to match or outperform peer players like Folder-Player while preserving our superior industrial Media3 streaming pipeline.

## Implementation Decisions

1. **ABI Target Selection**:
   - Android 10 (`minSdk = 29`) is the minimum baseline for this project. Virtually 100% of Android 10+ devices in active use feature 64-bit ARM CPUs (`arm64-v8a`). Dropping `armeabi-v7a` is safe, immediately cuts native package footprint by 50%, and avoids managing complex multi-APK split pipelines.
2. **Icon Catalog Locality (`AppIcons`)**:
   - Instead of relying on R8 tree-shaking to clean up a 30 MB library dependency, we apply the deletion test: completely removing `material-icons-extended` concentrates control locally and makes our UI dependencies completely explicit.
3. **ProGuard / R8 Safety Boundary**:
   - Room reflection, Media3 session callbacks, and JNI method signatures must be protected with precision:
     ```proguard
     -keep class com.webdav.player.data.local.** { *; }
     -keep class androidx.media3.decoder.ffmpeg.** { *; }
     -keepclasseswithmembernames class * { native <methods>; }
     ```
4. **DEX Packaging Mode (Legacy Compression vs Uncompressed STORE)**:
   - Modern AGP defaults to uncompressed DEX for Play Store AAB distributions to enable zero-extraction mmap on Android 9+. However, for independent GitHub Release APK distribution, the direct download size (network transfer size) is the primary user-facing metric. Compressing DEX inside the APK saves over 2 MB of download bandwidth without perceptible install-time performance impact on modern 64-bit devices.

## Test & Verification Matrix

| Verification Item | Command / Step | Success Criteria |
| :--- | :--- | :--- |
| **Release Compilation** | `./gradlew assembleRelease` | Build succeeds with R8 minification enabled |
| **APK Size Budget** | Zip inspection of `app-release.apk` | File size <= 7.5 MB (Expected: ~6.5 MB) |
| **DEX Count & Size** | Inspection of `classes.dex` entries | Exactly 1 `classes.dex`, total DEX size <= 4.0 MB |
| **Native SO Verification** | Inspection of `lib/` directory | Only `lib/arm64-v8a/` present; no `armeabi-v7a` |
| **Playback Regression** | Unit & Robolectric test suite | All existing repository, player, and metadata tests pass |
| **JNI Symbol Survival** | Test WMA & ASF playback | FFmpeg native demuxing and decoding functions resolve cleanly without `UnsatisfiedLinkError` |

## File Changes

- `app/build.gradle.kts`: Enable `isMinifyEnabled = true`, `isShrinkResources = true`, configure `dex.useLegacyPackaging = true`, restrict `abiFilters` to `arm64-v8a`, remove `material-icons-extended`.
- `app/proguard-rules.pro`: Add comprehensive keep rules for Media3, Room, OkHttp, Coroutines, and JNI native bindings.
- `app/src/main/java/com/webdav/player/ui/theme/AppIcons.kt`: New localized icon seam providing the explicit set of icons needed by the UI.
- `app/src/main/java/com/webdav/player/ui/**/*.kt`: Update icon imports from `Icons.Filled.*` to `AppIcons.*` or `Icons.Default.*`.
- `docs/adr/0013-64bit-only-abi-and-r8-binary-thinning.md`: Architectural decision record documenting the 64-bit ABI filter and R8 packaging seam.
