# 04: Consolidate Session Orchestrator & Verify Budget

**What to build:** Finalize the consolidation of `MusicPlayerAppSessionImpl` into a lean domain orchestrator that delegates to `SessionPersistenceCoordinator`, `PlaybackQueueManager`, `PlaybackFaultTolerance`, and `VirtualTimelineEngine`. Enforce the architectural 600-line cohesion budget on `MusicPlayerAppSessionImpl.kt`, verify that the external `MusicPlayerAppSession` contract remains 100% intact, and execute the complete regression test suite.

**Blocked by:** 03: Extract PlaybackFaultTolerance

**Status:** ready-for-agent

- [ ] Clean up redundant private fields and unused helpers in `MusicPlayerAppSessionImpl`
- [ ] Verify that `MusicPlayerAppSessionImpl.kt` line count is strictly within the target (< 700 lines, target ~550 lines)
- [ ] Confirm zero modifications to external callers (Compose UI, MediaSession Callback, Foreground Service)
- [ ] Run full project unit test suite (`gradlew testDebugUnitTest`) and ensure 100% passing
- [ ] Verify release APK packaging size budget remains <= 7.5 MB
