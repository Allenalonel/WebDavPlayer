# 04: End-to-End Pipeline Regression and Packaging Budget Gate

**What to build:** Full integration verification demonstrating that path encoding symmetry, artwork probe filtering, and Digest authentication operate harmoniously across the entire streaming playback pipeline and directory browser. Verify that release packaging strictly complies with binary thinning size budgets (total APK <= 7.5 MB, single uncompressed classes.dex <= 4.0 MB, arm64-v8a 64-bit architecture only) without any R8 minification or ProGuard regressions.

**Blocked by:** 01 (RFC 3986 Path Decoding and Symmetric Plus Sign Resilience), 02 (Manifest-Aware In-Memory Artwork Candidate Pre-Filtering), 03 (Integrated HTTP Digest Authentication and Fast Fallback)

**Status:** ready-for-agent

- [ ] All end-to-end integration tests (`EndToEndPlaybackPipelineIntegrationTest`, `EndToEndCacheResilienceIntegrationTest`, `PackagingSeamVerificationTest`) pass with 100% pass rate
- [ ] Entire unit test suite (`./gradlew testDebugUnitTest`) passes with 0 failures
- [ ] Release build packaging verification task (`./gradlew verifyReleasePackaging`) passes
- [ ] Total release APK size remains <= 7.5 MB
- [ ] Uncompressed classes.dex remains <= 4.0 MB
- [ ] Native library packaging contains only `lib/arm64-v8a/*.so` without 32-bit legacy binaries
