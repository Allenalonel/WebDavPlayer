# 03: Slim DirectoryBrowserViewModel and Verify Pipeline

**What to build:** Refactor `DirectoryBrowserViewModel` to consume the deepened `DirectoryRepository` and `LibrarySearchRepository`. Remove `trackMetadataRepository`, `cueSheetResolver`, and `searchHistoryStore` from the ViewModel's constructor, reducing dependencies from 7 to 4. Eliminate the 4 manual coroutine job handles and `processDirectoryCueAlbums` coordinator, turning `DirectoryBrowserViewModel` into a lean state projector (< 350 lines). Update `DirectoryBrowserViewModelFactory`, refactor `DirectoryBrowserViewModelTest`, run full regression test suites, and record ADR-0024.

**Blocked by:** 01, 02

**Status:** ready-for-agent

- [ ] Update `DirectoryBrowserViewModel` constructor to accept only 4 dependencies: `serverRepository`, `directoryRepository`, `musicPlayerAppSession`, `librarySearchRepository`
- [ ] Replace multi-stream job choreographing with a single reactive collection on `directoryRepository.observeDirectoryContent`
- [ ] Bind search and search history operations directly to `librarySearchRepository`
- [ ] Update `DirectoryBrowserViewModelFactory` to remove stripped dependencies
- [ ] Refactor `DirectoryBrowserViewModelTest` to test against the deepened repository seams (removing stale mocks)
- [ ] Verify `DirectoryBrowserViewModel.kt` is under 350 lines (strictly complying with the 600-line budget)
- [ ] Verify all project unit tests pass cleanly (`./gradlew testDebugUnitTest`)
- [ ] Record ADR-0024 documenting the deepening of DirectoryRepository and SearchRepository
