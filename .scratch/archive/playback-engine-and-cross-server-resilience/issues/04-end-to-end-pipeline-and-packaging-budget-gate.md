# 04: End-to-End Pipeline Regression and Packaging Budget Gate

**What to build:** Comprehensive regression verification ensuring that cross-server credential synchronization, bounded streaming retries, and foreground service elevation operate harmoniously across the full audio playback pipeline. Enforce release packaging budgets to guarantee that APK size remains <= 7.5 MB, single uncompressed classes.dex remains <= 4.0 MB, and native binaries contain only 64-bit arm64-v8a architectures without R8 or ProGuard regressions.

**Blocked by:** 01 (Cross-Server Gapless Credential Synchronization), 02 (Bounded Retry and Thread Release in Streaming Data Source), 03 (Foreground Service Elevation and Recovery)

**Status:** resolved

- [x] All end-to-end integration tests (`EndToEndPlaybackPipelineIntegrationTest`, `EndToEndSystemFocusAndRouteRegressionTest`, `PackagingSeamVerificationTest`) pass with 100% pass rate
- [x] Entire automated unit test suite (`./gradlew testDebugUnitTest`) passes with 0 failures
- [x] Release build packaging verification task (`./gradlew verifyReleasePackaging`) passes
- [x] Total release APK size remains <= 7.5 MB
- [x] Uncompressed classes.dex remains <= 4.0 MB
- [x] Native library packaging contains only `lib/arm64-v8a/*.so` without 32-bit legacy binaries

## Answer
1. 全量端到端测试套件（`EndToEndPlaybackPipelineIntegrationTest`、`EndToEndSystemFocusAndRouteRegressionTest`、`PackagingSeamVerificationTest`）100% 通过。
2. 全量单元测试套件（`./gradlew testDebugUnitTest`）共 1025 个测试用例全部执行通过，0 错误 0 失败。
3. 发布包构建验证任务（`./gradlew verifyReleasePackaging`）顺利通过质量门禁：
   - Total APK size: 7,004,794 bytes (6.68 MB) <= 7.5 MB 预算；
   - classes.dex size: 4,172,520 bytes (3.98 MB) <= 4.0 MB 预算；
   - classes.dex deflated: 2,108,434 bytes (2.01 MB)；
   - 原生库仅打包 `lib/arm64-v8a/` 架构（共 5 个 .so 库，0 个 32 位遗留文件）。
