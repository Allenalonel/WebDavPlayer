# 01: Internalize Search History into LibrarySearchRepository

**What to build:** Eliminate `SearchHistoryStore` as an independent shallow seam by absorbing search history persistence into `LibrarySearchRepository`. `LibrarySearchRepository` will manage persistent search history (up to 20 queries with LRU reordering and JSON serialization) alongside instant search execution, exposing explicit history methods (`getSearchHistory`, `recordSearchQuery`, `removeSearchHistory`, `clearSearchHistory`). `SearchHistoryStore.kt` will be deleted.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Add search history management methods to `domain/repository/LibrarySearchRepository.kt`
- [ ] Implement history persistence in `data/repository/LibrarySearchRepositoryImpl.kt` (using SharedPreferences storage helper)
- [ ] Add dedicated unit tests in `LibrarySearchRepositoryTest` verifying query recording, trimming, deduplication, item removal, and clearing
- [ ] Delete `data/repository/SearchHistoryStore.kt`
- [ ] Update DI in `WebDavApplication` and `DirectoryBrowserViewModelFactory` to remove `SearchHistoryStore` binding
- [ ] Ensure `LibrarySearchRepositoryTest` passes cleanly
