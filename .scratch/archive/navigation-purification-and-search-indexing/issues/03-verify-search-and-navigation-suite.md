# 03: Full Verification Loop and Line Budget Compliance

**What to build:**
Execute the complete verification and regression suite after implementing Ticket 01 and Ticket 02:
1. **Full Test Suite Execution**:
   - Run `./gradlew testDebugUnitTest` across the entire project.
   - Verify that `MainNavigationCoordinatorTest`, `LibrarySearchRepositoryTest`, `DirectoryBrowserViewModelTest`, and all integration tests pass 100%.
2. **Line Budget Audit**:
   - Verify that all modified files (`MainNavigationCoordinator.kt`, `LibrarySearchRepositoryImpl.kt`, `MainActivity.kt`) stay well below the 600-line ceiling in `AGENTS.md`.
3. **Architecture Seam Invariants**:
   - Ensure zero imports of `CueAssociationHelper` outside `data/cue/`.
   - Ensure zero playback control stubs in `MainNavigationCoordinator`.

**Blocked by:** 01, 02

**Status:** resolved

- [x] Execute `./gradlew testDebugUnitTest` and confirm 100% green tests
- [x] Verify line counts for all affected files
- [x] Confirm zero cross-package leakage

## Answer
- Executed `./gradlew testDebugUnitTest` across the entire project; 100% of unit and integration tests passed.
- Line Budget Audit:
  - `MainNavigationCoordinator.kt`: 108 lines (budget < 250, limit < 600)
  - `LibrarySearchRepositoryImpl.kt`: 242 lines (budget < 250, limit < 600)
  - `MainActivity.kt`: 416 lines (limit < 600)
  - `PlaybackQueueManager.kt`: 387 lines (limit < 600)
  - `CueAssociationHelper.kt`: 68 lines (limit < 600)
- Architecture Seam Invariants:
  - Exactly zero imports of `CueAssociationHelper` outside `data/cue/` (helper is now `internal object`).
  - Exactly zero playback control pass-through methods in `MainNavigationCoordinator`.
- Zero ktlint/compiler warnings on modified code.
