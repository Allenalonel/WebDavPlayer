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

**Status:** claimed

- [ ] Create `ui/player/PlaybackActions.kt` interface and `rememberPlaybackActions` helper
- [ ] Refactor `FullPlayerView.kt` to accept `playbackActions: PlaybackActions`
- [ ] Update `MainActivity.kt` to pass `playbackActions`
- [ ] Update `FullPlayerView` preview declarations
- [ ] Verify Compose previews and Robolectric unit tests pass cleanly
