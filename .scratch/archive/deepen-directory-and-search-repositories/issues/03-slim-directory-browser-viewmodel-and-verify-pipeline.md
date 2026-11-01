# 03: Slim DirectoryBrowserViewModel and Verify Pipeline

**What to build:** Refactor `DirectoryBrowserViewModel` to consume the deepened `DirectoryRepository` and `LibrarySearchRepository`. Remove `trackMetadataRepository`, `cueSheetResolver`, and `searchHistoryStore` from the ViewModel's constructor, reducing dependencies from 7 to 4. Eliminate the 4 manual coroutine job handles and `processDirectoryCueAlbums` coordinator, turning `DirectoryBrowserViewModel` into a lean state projector (< 350 lines). Update `DirectoryBrowserViewModelFactory`, refactor `DirectoryBrowserViewModelTest`, run full regression test suites, and record ADR-0024.

**Blocked by:** 01, 02

**Status:** resolved

- [x] Update `DirectoryBrowserViewModel` constructor to accept only 4 dependencies: `serverRepository`, `directoryRepository`, `musicPlayerAppSession`, `librarySearchRepository`
- [x] Replace multi-stream job choreographing with a single reactive collection on `directoryRepository.observeDirectoryContent`
- [x] Bind search and search history operations directly to `librarySearchRepository`
- [x] Update `DirectoryBrowserViewModelFactory` to remove stripped dependencies
- [x] Refactor `DirectoryBrowserViewModelTest` to test against the deepened repository seams (removing stale mocks)
- [x] Verify `DirectoryBrowserViewModel.kt` is under 350 lines (strictly complying with the 600-line budget)
- [x] Verify all project unit tests pass cleanly (`./gradlew testDebugUnitTest`)
- [x] Record ADR-0024 documenting the deepening of DirectoryRepository and SearchRepository

## Answer
Refactored `DirectoryBrowserViewModel` to consume the deepened `DirectoryRepository` and `LibrarySearchRepository`, reducing constructor dependencies from 7 down to 4. Eliminated 4 manual coroutine job handles and the `processDirectoryCueAlbums` coordinator, shrinking `DirectoryBrowserViewModel.kt` from 697 lines to 346 lines (< 350 lines, well within the 600-line budget). Updated `DirectoryBrowserViewModelFactory`, deleted `SearchHistoryStore.kt`, updated `DirectoryBrowserViewModelTest` and `EndToEndCacheResilienceIntegrationTest`, verified all unit tests pass 100% green, and recorded ADR-0024.
