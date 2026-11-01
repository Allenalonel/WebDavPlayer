# 01: Purify MainNavigationCoordinator and Eliminate Playback Stubs

**What to build:**
Refactor `ui/navigation/MainNavigationCoordinator.kt` to eliminate the unnecessary dependency on `MusicPlayerAppSession`:
1. **Remove Playback Pass-Through Stubs**:
   - Delete `togglePlayPause()` and `skipToNext()` from `MainNavigationCoordinator`.
   - Remove `musicPlayerAppSession: MusicPlayerAppSession? = null` from its primary constructor.
2. **Purify Navigation State Responsibility**:
   - `MainNavigationCoordinator` focuses 100% on navigation state machine (`currentDestination`, `selectedPlaylistId`, `isFullPlayerExpanded`, `isMiniPlayerVisible`).
   - The initial mini-player visibility is driven cleanly via `onSessionStateChanged(sessionState: PlayerSessionState)` or `initialIsMiniPlayerVisible: Boolean = false`.
3. **Update Call Sites**:
   - In `MainActivity.kt`, simplify `MainNavigationCoordinator` instantiation (no longer passing `musicSession`).
   - In `MainNavigationCoordinatorTest.kt`, remove obsolete `togglePlayPause` / `skipToNext` assertions and update constructor calls.

**Blocked by:** None

**Status:** resolved

- [x] Remove `musicPlayerAppSession` and playback stubs from `MainNavigationCoordinator.kt`
- [x] Update `MainActivity.kt` call site
- [x] Update `MainNavigationCoordinatorTest.kt` unit tests
- [x] Verify clean compilation and 100% test pass rate for navigation

## Answer
- Removed `musicPlayerAppSession` parameter and pass-through stubs (`togglePlayPause`, `skipToNext`) from `MainNavigationCoordinator.kt`.
- Provided clean reactive initialization with `initialIsMiniPlayerVisible` parameter while state continues to be updated by `onSessionStateChanged(sessionState)`.
- Simplified `remember` instantiation in `MainActivity.kt` without session dependency.
- Updated `MainNavigationCoordinatorTest.kt`, `FullPlayerPresentationTest.kt`, and `PlayerSheetExpansionTest.kt` to construct `MainNavigationCoordinator` purely.
- All unit tests pass 100%. MainNavigationCoordinator stands at 96 lines (well below the 600-line budget).
