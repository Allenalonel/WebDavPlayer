# 04: End-to-End Pipeline Regression and Packaging Budget Gate

**What to build:** Full integration verification demonstrating that path encoding symmetry, artwork probe filtering, and Digest authentication operate harmoniously across the entire streaming playback pipeline and directory browser. Verify that release packaging strictly complies with binary thinning size budgets (total APK <= 7.5 MB, single uncompressed classes.dex <= 4.0 MB, arm64-v8a 64-bit architecture only) without any R8 minification or ProGuard regressions.

**Blocked by:** 01 (RFC 3986 Path Decoding and Symmetric Plus Sign Resilience), 02 (Manifest-Aware In-Memory Artwork Candidate Pre-Filtering), 03 (Integrated HTTP Digest Authentication and Fast Fallback)

**Status:** resolved

- [x] All end-to-end integration tests (`EndToEndPlaybackPipelineIntegrationTest`, `EndToEndCacheResilienceIntegrationTest`, `PackagingSeamVerificationTest`) pass with 100% pass rate
- [x] Entire unit test suite (`./gradlew testDebugUnitTest`) passes with 0 failures
- [x] Release build packaging verification task (`./gradlew verifyReleasePackaging`) passes
- [x] Total release APK size remains <= 7.5 MB
- [x] Uncompressed classes.dex remains <= 4.0 MB
- [x] Native library packaging contains only `lib/arm64-v8a/*.so` without 32-bit legacy binaries

## Answer
执行全量集成回归测试与质量门禁验证：
1. 端到端测试套件（`EndToEndPlaybackPipelineIntegrationTest`、`EndToEndCacheResilienceIntegrationTest`、`PackagingSeamVerificationTest`）通过率 100%。
2. 全量单元测试 `./gradlew testDebugUnitTest` 全部通过（0 失败）。
3. 执行 `./gradlew verifyReleasePackaging`，各项体积与架构指标全部合规：
   - Total APK size: 7,004,982 bytes (6.68 MB) <= 7.5 MB 预算
   - classes.dex uncompressed: 4,170,560 bytes (3.98 MB) <= 4.0 MB 预算
   - classes.dex deflated: 2,106,752 bytes (2.01 MB)
   - Native architecture: 纯净包含 5 个 64 位 `lib/arm64-v8a/*.so`，无任何 32 位遗留文件。
4. R8 混淆、资源压缩及 Media3 流媒体接缝无反向依赖倒置与内存泄漏。
