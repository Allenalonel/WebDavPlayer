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

**Status:** open

- [ ] Execute `./gradlew testDebugUnitTest` and confirm 100% green tests
- [ ] Verify line counts for all affected files
- [ ] Confirm zero cross-package leakage
