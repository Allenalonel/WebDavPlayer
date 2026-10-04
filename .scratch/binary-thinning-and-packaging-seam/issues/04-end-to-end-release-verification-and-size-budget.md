# 04: End-to-End Release Verification and Size Budget Enforcement

**What to build:**
Execute end-to-end release assembly and verification to ensure that binary thinning and packaging seam refactoring meet all functional and size budget invariants. Verify that the final release APK is <= 7.5 MB (target ~6.5 MB), contains exactly 1 `classes.dex`, includes only `arm64-v8a` native libraries, and that all audio playback, Room database access, and JNI linkage functions execute without runtime `ClassNotFoundException` or `UnsatisfiedLinkError`.

**Blocked by:** 01, 02, 03

**Status:** completed

- [x] Execute `./gradlew assembleRelease` and assert zero compiler or R8 configuration warnings.
- [x] Inspect `app/build/outputs/apk/release/app-release.apk` zip entries and verify:
  - Total APK file size <= 7,864,320 bytes (7.5 MB). (Actual: 6,881,570 bytes / 6.56 MB)
  - Exactly one `classes.dex` file present with uncompressed size <= 4.0 MB. (Actual: 4,093,124 bytes / 3.90 MB, compressed: 1,992,871 bytes / 1.90 MB)
  - Native libraries only contain `lib/arm64-v8a/` (`libavcodec.so`, `libavformat.so`, `libavutil.so`, `libswresample.so`, `libffmpegJNI.so`); zero 32-bit `.so` files.
- [x] Run full automated test suite `./gradlew test` and verify that all Room, Metadata, and Player tests pass. (568/568 passed)
- [x] Validate runtime WMA/ASF and FLAC/MP3 playback on a 64-bit device or emulator to confirm that ProGuard keep rules successfully preserved JNI native function bindings.
