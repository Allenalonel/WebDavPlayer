# 03: Align MiniPlayer to PlaybackActions and Verify Full Suite

**What to build:**
Align the docked mini-player to the cohesive `PlaybackActions` contract and execute the full codebase regression verification:
1. **Refactor `MiniPlayer` Component Signature**:
   - Update `ui/player/MiniPlayer.kt` to accept `playbackActions: PlaybackActions` directly:
     ```kotlin
     @Composable
     fun MiniPlayer(
         sessionState: PlayerSessionState,
         playbackActions: PlaybackActions,
         modifier: Modifier = Modifier,
         onMiniPlayerClick: () -> Unit = {},
         playbackProgress: PlaybackProgress = PlaybackProgress.ZERO,
     )
     ```
   - Connect transport buttons (`PlayPauseToggleButton`, `SkipNext`, `MiniPlayerRetryBanner`) to `playbackActions.togglePlayPause()`, `playbackActions.skipToNext()`, and `playbackActions.retryPlayback()`.
2. **Eliminate `DockedMiniPlayer` Redundant Wrapper**:
   - Delete the 12-line pass-through function `DockedMiniPlayer` in `MiniPlayer.kt`.
   - In `MainActivity.kt`, invoke `MiniPlayer(...)` directly, simplifying the bottom bar container.
3. **Update Test Call Sites**:
   - Update `MiniPlayerTest.kt` (or related UI tests) to supply `PlaybackActions` (using a mock or `PlaybackActions.EMPTY`).
4. **Full Verification Loop & Budget Compliance**:
   - Verify every file in `app/src/main/java` strictly adheres to `AGENTS.md` (lines < 600).
   - Execute the entire unit test suite (`./gradlew testDebugUnitTest`) to ensure 100% green tests.

**Blocked by:** 01, 02

**Status:** open

- [ ] Refactor `MiniPlayer.kt` signature to take `playbackActions: PlaybackActions`
- [ ] Delete `DockedMiniPlayer` pass-through wrapper
- [ ] Update `MainActivity.kt` to invoke `MiniPlayer` directly
- [ ] Update `MiniPlayerTest` unit tests to supply `PlaybackActions`
- [ ] Verify line counts for all files in `app/src/main/java` are strictly under 600 lines
- [ ] Run `./gradlew testDebugUnitTest` and confirm 100% pass rate
