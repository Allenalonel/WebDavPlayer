# 04: End-to-End P0 Regression and Verification

**What to build:** Execute and consolidate the end-to-end regression test suite verifying that Service lifecycle startup complies with Android foreground contracts and authenticated WebDAV streaming remains continuous across background metadata updates.

**Blocked by:** 01-immediate-service-lifecycle-elevation, 03-non-disruptive-track-metadata-enrichment

**Status:** ready-for-agent

- [ ] Execute `./gradlew.bat testDebugUnitTest --offline` to verify that all existing unit and integration tests pass without regression.
- [ ] Verify that `PlaybackSessionHostTest` and `WebDavMediaServiceTest` pass with zero `RemoteServiceException` warnings or lifecycle race failures.
- [ ] Verify that `Media3AudioPlayerEngineTest` and `WebDavStreamingPlaybackTest` pass with authenticated endpoints and metadata updates.
- [ ] Verify that `EndToEndPlaybackPipelineIntegrationTest` executes cleanly from directory browsing through authenticated playback and session suspension.
