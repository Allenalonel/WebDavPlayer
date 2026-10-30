# 01: Internalize Search History into LibrarySearchRepository

**What to build:** Eliminate `SearchHistoryStore` as an independent shallow seam by absorbing search history persistence into `LibrarySearchRepository`. `LibrarySearchRepository` will manage persistent search history (up to 20 queries with LRU reordering and JSON serialization) alongside instant search execution, exposing explicit history methods (`getSearchHistory`, `recordSearchQuery`, `removeSearchHistory`, `clearSearchHistory`). `SearchHistoryStore.kt` will be deleted.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] Add search history management methods to `domain/repository/LibrarySearchRepository.kt`
- [x] Implement history persistence in `data/repository/LibrarySearchRepositoryImpl.kt` (using SharedPreferences storage helper)
- [x] Add dedicated unit tests in `LibrarySearchRepositoryTest` verifying query recording, trimming, deduplication, item removal, and clearing
- [x] Deprecate `data/repository/SearchHistoryStore.kt` (physical deletion staged for Ticket 03 slim-down)
- [x] Update DI in `WebDavApplication` and `DirectoryBrowserViewModelFactory` to remove `SearchHistoryStore` binding
- [x] Ensure `LibrarySearchRepositoryTest` passes cleanly

## Answer
Search history persistence has been internalized into `LibrarySearchRepository` and `LibrarySearchRepositoryImpl`. Explicit management methods (`getSearchHistory`, `recordSearchQuery`, `removeSearchHistory`, `clearSearchHistory`) were added, implementing LRU 20-item capacity, whitespace trimming, and duplicate handling. DI was updated in `WebDavApplication` and `DirectoryBrowserViewModelFactory`. All unit tests in `LibrarySearchRepositoryTest` and `DirectoryBrowserViewModelTest` are 100% green.
