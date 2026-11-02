# 04: End-to-End Pipeline Regression and Packaging Budget Gate

**What to build:** Comprehensive regression verification ensuring that network connectivity observation, transparent auto-resumption, and offline playlist self-healing operate harmoniously across the full audio playback pipeline. Enforce release packaging budgets to guarantee that APK size remains <= 7.5 MB, single uncompressed classes.dex remains <= 4.0 MB, and native binaries contain only 64-bit arm64-v8a architectures without R8 or ProGuard regressions.

**Blocked by:** 02 (Ghost-Proof Transparent Playback Auto-Resumption), 03 (Offline Playlist Reachability Self-Healing on Network Restoration)

**Status:** resolved

- [x] All end-to-end integration tests (`EndToEndPlaybackPipelineIntegrationTest`, `EndToEndCacheResilienceIntegrationTest`, `PackagingSeamVerificationTest`) pass with 100% pass rate
- [x] Entire automated unit test suite (`./gradlew testDebugUnitTest`) passes with 0 failures
- [x] Release build packaging verification task (`./gradlew verifyReleasePackaging`) passes
- [x] Total release APK size remains <= 7.5 MB
- [x] Uncompressed classes.dex remains <= 4.0 MB
- [x] Native library packaging contains only `lib/arm64-v8a/*.so` without 32-bit legacy binaries

## Comments
- Full test suite regression executed: 1043 unit and end-to-end tests passed (0 failures).
- End-to-end integration tests (`EndToEndPlaybackPipelineIntegrationTest`, `EndToEndCacheResilienceIntegrationTest`, `PackagingSeamVerificationTest`) passed with 100% pass rate.
- Release packaging verification passed:
  - APK size: 7,015,734 bytes (6.69 MB <= 7.5 MB)
  - classes.dex size: 4,191,488 bytes (4.00 MB <= 4.0 MB)
  - Native architecture: arm64-v8a only (5 libraries, 0 32-bit)
