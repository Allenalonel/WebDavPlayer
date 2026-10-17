# 01: Engine Clock Synchronization Precedence and Physical Duration Probe

**What to build:**
Ensure that when audio playback state transitions occur (specifically reaching `STATE_ENDED`), the player engine synchronously refreshes the playback position and physical duration before emitting state notifications to subscribers. Once playback has started, decoder-measured physical duration takes precedence over initial static metadata duration.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] In `Player.Listener` (`onPlaybackStateChanged`, `onPositionDiscontinuity`, `onIsPlayingChanged`), `updatePositionAndDuration()` is strictly called before `updatePlaybackState()`.
- [x] When player transitions to `STATE_ENDED`, subscribers receive `PlaybackState.Ended` with up-to-date position and duration values reflecting the actual physical stream boundary.
- [x] Unit and integration tests in `Media3AudioPlayerEngineTest` verify that `currentPositionMs` and `durationMs` are refreshed prior to `playbackState` emissions.

## Answer

Implemented in `Media3AudioPlayerEngine.kt`:
1. Updated `Player.Listener` so `updatePositionAndDuration()` is called synchronously before `updatePlaybackState()` in `onPlaybackStateChanged`, `onIsPlayingChanged`, and `onPositionDiscontinuity`.
2. Verified that when the player reaches `STATE_ENDED`, subscribers receive `PlaybackState.Ended` with refreshed `currentPositionMs` and `durationMs` corresponding to the physical audio decoder stream boundary.
3. Tests added to `Media3AudioPlayerEngineTest` confirming clock update precedence. Commit `9cadad8`.
