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

**Status:** resolved

- [x] Refactor `MiniPlayer.kt` signature to take `playbackActions: PlaybackActions`
- [x] Delete `DockedMiniPlayer` pass-through wrapper
- [x] Update `MainActivity.kt` to invoke `MiniPlayer` directly
- [x] Update `MiniPlayerTest` unit tests to supply `PlaybackActions`
- [x] Verify line counts for all files in `app/src/main/java` are strictly under 600 lines
- [x] Run `./gradlew testDebugUnitTest` and confirm 100% pass rate

## Answer
- Refactored `MiniPlayer` to directly consume `PlaybackActions`, matching the architecture of `FullPlayerView`. Connected transport controls (play/pause toggle, skip next, retry banner) directly to the unified intent contract.
- Deleted the redundant `DockedMiniPlayer` pass-through wrapper in `MiniPlayer.kt` and updated `MainActivity.kt` to call `MiniPlayer` directly.
- Added tests in `MiniPlayerPresentationTest.kt` verifying `MiniPlayer` signature against `PlaybackActions` and verifying reflection absence of `DockedMiniPlayer`.
- Audited line counts across `app/src/main/java`: all business logic and UI files strictly adhere to the < 600 lines budget.
- Executed the full unit test suite (`./gradlew testDebugUnitTest`), achieving 100% pass rate with zero errors.
