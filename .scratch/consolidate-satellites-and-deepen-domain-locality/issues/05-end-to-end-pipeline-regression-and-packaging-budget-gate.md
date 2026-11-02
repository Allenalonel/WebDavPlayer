# 05: End-to-End Pipeline Regression and Packaging Budget Gate

**What to build:** Full integration verification confirming that consolidating the playlist track row, CUE sheet resolver, playback queue bottom sheet, and domain models introduces zero functional or visual regressions. Verify that release packaging strictly complies with binary thinning size budgets (total APK <= 7.5 MB, single uncompressed classes.dex <= 4.0 MB, arm64-v8a 64-bit architecture only) without any R8 minification or ProGuard regressions.

**Blocked by:** 01 (Consolidate PlaylistTrackRow and Eliminate Satellite Collaborators), 02 (Internalize Audio Heuristic Matching into Deep CUE Sheet Resolver and Align Test Surface), 03 (Collapse Single-Caller QueueTrackItemRow into PlaybackQueueBottomSheet), 04 (Consolidate Domain Micro-Models into Owning Concept Files)

**Status:** ready-for-agent

- [ ] All end-to-end integration tests (`EndToEndPlaybackPipelineIntegrationTest`, `PackagingSeamVerificationTest`, `EndToEndCacheResilienceIntegrationTest`) pass with 100% pass rate
- [ ] Entire automated unit test suite (`./gradlew testDebugUnitTest`) passes with 0 failures
- [ ] Release build packaging verification task (`./gradlew verifyReleasePackaging`) passes
- [ ] Total release APK size remains <= 7.5 MB
- [ ] Uncompressed classes.dex remains <= 4.0 MB
- [ ] Native library packaging contains only `lib/arm64-v8a/*.so` without 32-bit legacy binaries
