# 04: End-to-End Pipeline Regression and Packaging Budget Gate

**What to build:** Comprehensive regression verification ensuring that cross-server credential synchronization, bounded streaming retries, and foreground service elevation operate harmoniously across the full audio playback pipeline. Enforce release packaging budgets to guarantee that APK size remains <= 7.5 MB, single uncompressed classes.dex remains <= 4.0 MB, and native binaries contain only 64-bit arm64-v8a architectures without R8 or ProGuard regressions.

**Blocked by:** 01 (Cross-Server Gapless Credential Synchronization), 02 (Bounded Retry and Thread Release in Streaming Data Source), 03 (Foreground Service Elevation and Recovery)

**Status:** ready-for-agent

- [ ] All end-to-end integration tests (`EndToEndPlaybackPipelineIntegrationTest`, `EndToEndSystemFocusAndRouteRegressionTest`, `PackagingSeamVerificationTest`) pass with 100% pass rate
- [ ] Entire automated unit test suite (`./gradlew testDebugUnitTest`) passes with 0 failures
- [ ] Release build packaging verification task (`./gradlew verifyReleasePackaging`) passes
- [ ] Total release APK size remains <= 7.5 MB
- [ ] Uncompressed classes.dex remains <= 4.0 MB
- [ ] Native library packaging contains only `lib/arm64-v8a/*.so` without 32-bit legacy binaries
