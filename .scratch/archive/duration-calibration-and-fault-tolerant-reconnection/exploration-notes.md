# Exploration Notes: Physical Duration Calibration and Fault-Tolerant Reconnection

## Architecture Overview
- **Audio Engine**: `Media3AudioPlayerEngine.kt` wraps ExoPlayer/Media3 `Player`. Emits state via `StateFlow` (`playbackState`, `currentPositionMs`, `durationMs`, etc.).
- **Session Layer**: `MusicPlayerAppSessionImpl.kt` orchestrates queue, playback mode, seamless pre-roll, duration guard, buffering watchdog, and error handling. State represented by `PlayerSessionState.kt`.
- **Streaming Layer**: `ResilientWebDavDataSource.kt` wraps Media3 `HttpDataSource` with exponential backoff and transparent ranged reconnection.
- **UI Layer**: Compose-based `MiniPlayer.kt` and `FullPlayerView.kt`.

---

## Ticket 01: Engine Clock Synchronization Precedence and Physical Duration Probe
- **Target Files**:
  - `app/src/main/java/com/webdav/player/data/player/Media3AudioPlayerEngine.kt`
  - `app/src/test/java/com/webdav/player/data/player/Media3AudioPlayerEngineTest.kt`
- **Key Findings**:
  - In `Player.Listener`:
    - `onPlaybackStateChanged(state: Int)` currently calls `updatePlaybackState()` BEFORE `updatePositionAndDuration()`.
    - Fix: Strictly call `updatePositionAndDuration()` BEFORE `updatePlaybackState()`.
    - In `onIsPlayingChanged(isPlaying: Boolean)`: when `!isPlaying`, also call `updatePositionAndDuration()` before or synchronously ensuring consistent state.
    - In `onPositionDiscontinuity`: already calls `updatePositionAndDuration()`.
  - In `updatePositionAndDuration()`:
    - `val dur = player.duration`
    - When `dur != C.TIME_UNSET && dur > 0`, it updates `_durationMs.value = dur`.
    - Ensure subscribers receive `PlaybackState.Ended` with up-to-date position and duration values reflecting the physical stream boundary.

---

## Ticket 02: Multi-Tier Completion Arbitration and Uninterrupted Queue Progression
- **Target Files**:
  - `app/src/main/java/com/webdav/player/domain/session/MusicPlayerAppSessionImpl.kt`
  - `app/src/test/java/com/webdav/player/domain/session/MusicPlayerAppSessionTest.kt`
- **Key Findings**:
  - `handlePlaybackEnded()`:
    - Constant: `DURATION_COMPLETION_TOLERANCE_MS` (currently e.g. 1000ms - 5000ms).
    - Threshold: 95% (`COMPLETION_PROGRESS_THRESHOLD = 0.95`).
    - Tier 1 (Physical Match): `currentPos >= duration - DURATION_COMPLETION_TOLERANCE_MS` -> natural completion.
    - Tier 2 (Progress Threshold): `duration > 0L && (currentPos.toDouble() / duration.toDouble()) >= 0.95` -> natural completion (handles metadata tag inflation like MP3 TLEN / VBR estimates).
    - Tier 3 (Premature EOF): If neither Tier 1 nor Tier 2 is met, treat as premature cut-off. Do NOT advance queue. Keep current index, seek to `currentPos`, transition to `PlaybackState.Buffering`, and resume streaming in-place.
    - If natural completion is confirmed:
      - `PlaybackMode.SINGLE_LOOP`: seekTo(0L), play(), replay from beginning without entering buffering loop.
      - `PlaybackMode.LIST_LOOP` / `PlaybackMode.SHUFFLE`: advance queue via `transitionToQueueIndex(...)`.

---

## Ticket 03: Non-206 HTTP Response Interception and Confirmation Flow
- **Target Files**:
  - `app/src/main/java/com/webdav/player/data/player/ResilientWebDavDataSource.kt`
  - `app/src/main/java/com/webdav/player/domain/model/PlayerSessionState.kt`
  - `app/src/main/java/com/webdav/player/domain/session/MusicPlayerAppSession.kt`
  - `app/src/main/java/com/webdav/player/domain/session/MusicPlayerAppSessionImpl.kt`
  - `app/src/main/java/com/webdav/player/ui/player/FullPlayerView.kt` / `MiniPlayer.kt` / dialog components
  - `app/src/test/java/com/webdav/player/data/player/ResilientWebDavDataSourceTest.kt`
- **Key Findings**:
  - Create `UnsupportedRangeReconnectionException(message: String) : IOException(message)`.
  - In `ResilientWebDavDataSource.kt`:
    - In `openRangeSource()`: after `nextSource.open(rangeSpec)`, if `currentOffset > 0L`:
      - Check response status code. If `responseCode == 200`:
        - Throw `UnsupportedRangeReconnectionException`.
    - In `isNonRetryable(throwable)`: mark `UnsupportedRangeReconnectionException` as non-retryable so it aborts immediately.
  - In `PlayerSessionState.kt`:
    - Add `val isUnsupportedRange: Boolean = false`.
  - In `MusicPlayerAppSession`:
    - Add actions: `confirmUnsupportedRangeReplay()` and `dismissUnsupportedRange()`.
    - Catch `UnsupportedRangeReconnectionException` from engine error emissions: pause player, set `isUnsupportedRange = true`, retain position.
    - When confirmed: seek to `0L`, play(), clear `isUnsupportedRange`.
    - When dismissed: clear `isUnsupportedRange`, remain paused.
  - In UI:
    - Display confirmation dialog when `sessionState.isUnsupportedRange` is true:
      - Title/Text: "当前服务器不支持断点续传，是否从头重新播放此曲？"
      - Confirm: calls `confirmUnsupportedRangeReplay()`.
      - Dismiss: calls `dismissUnsupportedRange()`.

---

## Ticket 04: Suspended In-Place State and Persistent Retry Banner
- **Target Files**:
  - `app/src/main/java/com/webdav/player/domain/session/MusicPlayerAppSessionImpl.kt`
  - `app/src/main/java/com/webdav/player/ui/player/MiniPlayer.kt`
  - `app/src/main/java/com/webdav/player/ui/player/FullPlayerView.kt`
  - `app/src/test/java/com/webdav/player/ui/player/MiniPlayerPresentationTest.kt`
  - `app/src/test/java/com/webdav/player/ui/player/FullPlayerPresentationTest.kt`
- **Key Findings**:
  - In `MusicPlayerAppSessionImpl`: Watchdog already handles quiet window exhaustion (30s: 15s weak, 15s canManualRetry).
  - Ensure banner visibility:
    - MiniPlayer: When `sessionState.isWeakNetwork && sessionState.canManualRetry`, display a persistent retry banner or prominent prompt "网络连接受阻，点击重试" with clickable retry action.
    - FullPlayerView: Display the persistent banner with "网络连接受阻，点击重试" and retry button.
    - Clicking retry calls in-place resumption from current position.
