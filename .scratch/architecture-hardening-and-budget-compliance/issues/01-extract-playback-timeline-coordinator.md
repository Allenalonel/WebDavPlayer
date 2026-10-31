# 01: Extract PlaybackTimelineCoordinator and Restore Session File Budget

**What to build:**
Resolve the critical budget violation in `domain/session/MusicPlayerAppSessionImpl.kt` (currently **789 lines**, exceeding the 600-line limit by 31.5%) in accordance with ADR-0023 and `AGENTS.md`.
Extract `internal class PlaybackTimelineCoordinator` to encapsulate all engine event observation and progress pipeline arithmetic:
1. **Engine Event Observation Pipelines**:
   - `startPlaybackStateObservation`: Handles `PlaybackState.Ended`, `Buffering`, `Playing`, and `Error`.
   - `startTrackTransitionsObservation`: Collects Exoplayer gapless track transitions and advances queue indices.
   - `startProgressPipeline`: High-frequency polling on `progressDispatcher`, combining position, duration, and buffer.
   - `startDurationObservation`: Observes physical stream duration updates for uncalibrated audio files.
   - `startServerObservation`: Observes active server changes from `ServerRepository`.
2. **Timeline Arithmetic**:
   - `mapEngineProgress(pos, dur, buf)`: Maps physical ExoPlayer timeline to relative virtual track timeline using `VirtualTimelineEngine`.
3. **Session Orchestrator Slimming**:
   - `MusicPlayerAppSessionImpl` delegates all observation lifecycle and progress calculation to `PlaybackTimelineCoordinator`.
   - Shrinks `MusicPlayerAppSessionImpl.kt` from **789 lines to ≤400 lines**.
4. **Verification**:
   - `MusicPlayerAppSession` public interface remains **100% stable**.
   - All tests in `MusicPlayerAppSessionTest.kt` pass with zero regressions.

**Blocked by:** None

**Status:** open

- [ ] Create `domain/session/PlaybackTimelineCoordinator.kt` as an internal collaborator
- [ ] Move the 5 coroutine observation jobs and `mapEngineProgress` into `PlaybackTimelineCoordinator`
- [ ] Integrate `PlaybackTimelineCoordinator` in `MusicPlayerAppSessionImpl`
- [ ] Verify `MusicPlayerAppSessionImpl.kt` line count is ≤400 lines (strictly complying with 600-line budget)
- [ ] Run `MusicPlayerAppSessionTest` to confirm 100% test pass rate
