# 04: End-to-End Pipeline Regression and Packaging Budget Gate

**What to build:** Full integration verification confirming that consolidating the library search views, directory file item row, and server list screen introduces zero visual, behavioral, or navigation regressions. Verify that release packaging strictly complies with binary thinning size budgets (total APK <= 7.5 MB, single uncompressed classes.dex <= 4.0 MB, arm64-v8a 64-bit architecture only) without any R8 minification or ProGuard regressions.

**Blocked by:** 01 (Consolidate Library Search Presentation Module), 02 (Internalize Directory File Row Satellites), 03 (Fold Delete Confirmation Dialog into Server List Screen)

**Status:** resolved

- [x] All end-to-end integration tests (`EndToEndPlaybackPipelineIntegrationTest`, `PackagingSeamVerificationTest`, `EndToEndCacheResilienceIntegrationTest`) pass with 100% pass rate
- [x] Entire automated unit test suite (`./gradlew testDebugUnitTest`) passes with 0 failures
- [x] Release build packaging verification task (`./gradlew verifyReleasePackaging`) passes
- [x] Total release APK size remains <= 7.5 MB
- [x] Uncompressed classes.dex remains <= 4.0 MB
- [x] Native library packaging contains only `lib/arm64-v8a/*.so` without 32-bit legacy binaries
