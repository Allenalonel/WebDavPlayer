# 03: Aggregate FullPlayerView Playback Intents into PlaybackActions

**What to build:**
Solve the parameter explosion in `ui/player/FullPlayerView.kt` (currently **17 parameters**, with 8 individual callback lambdas passing through to `PlayerTransportControls` and `PlaybackQueueBottomSheet`).
Aggregate playback intents into a clean, reusable `PlaybackActions` contract:
1. **Define `PlaybackActions` Interface**:
   - Create `ui/player/PlaybackActions.kt`:
     ```kotlin
     interface PlaybackActions {
         fun togglePlayPause()
         fun seekTo(positionMs: Long)
         fun skipToNext()
         fun skipToPrevious()
         fun cyclePlaybackMode()
         fun playQueueIndex(index: Int)
         fun removeQueueTrack(index: Int)
         fun retryPlayback()
     }
     ```
   - Provide a convenient `rememberPlaybackActions(session: MusicPlayerAppSession?): PlaybackActions` helper.
2. **Refactor `FullPlayerView` Signature**:
   - Collapse individual callback parameters (`onTogglePlayPause`, `onSeek`, `onSkipToNext`, `onSkipToPrevious`, `onCyclePlaybackMode`, `onPlayQueueIndex`, `onRemoveQueueTrack`, `onRetryPlayback`) into `playbackActions: PlaybackActions`.
   - Shrink `FullPlayerView` parameters from **17 down to 5~6**.
3. **Simplify `MainActivity` Call Site**:
   - Replace the verbose 8-line lambda wiring block in `MainActivity.kt` with a single `playbackActions` argument.
4. **Update Previews & Unit Tests**:
   - Provide `PlaybackActions.EMPTY` / `MockPlaybackActions` for Compose previews and tests.

**Blocked by:** None

**Status:** resolved

- [x] Create `ui/player/PlaybackActions.kt` interface and `rememberPlaybackActions` helper
- [x] Refactor `FullPlayerView.kt` to accept `playbackActions: PlaybackActions`
- [x] Update `MainActivity.kt` to pass `playbackActions`
- [x] Update `FullPlayerView` preview declarations
- [x] Verify Compose previews and Robolectric unit tests pass cleanly

## Answer
- Created `PlaybackActions` interface and `rememberPlaybackActions` Compose helper in `com.webdav.player.ui.player`.
- Refactored `FullPlayerView.kt` to accept `playbackActions: PlaybackActions = PlaybackActions.EMPTY`, collapsing 8 individual playback callback parameters.
- Simplified `MainActivity.kt` call site from 11 lines of callback forwarding to a single `playbackActions` parameter.
- Full test suite passed with 100% pass rate.
