# Map: Deepen Directory and Search Repositories and Slim DirectoryBrowserViewModel

## Overview
Refactoring the media library and search presentation layers by deepening `DirectoryRepository` and `LibrarySearchRepository`, eliminating the shallow `SearchHistoryStore`, and slimming `DirectoryBrowserViewModel` into a lean state projector.

## Issues Graph
- [ ] **01-internalize-search-history-into-library-search-repository** (Unblocked)
- [ ] **02-deepen-directory-repository-with-content-enrichment** (Unblocked)
- [ ] **03-slim-directory-browser-viewmodel-and-verify-pipeline** (Blocked by 01, 02)

## Decisions So Far
- **Decision 1 (Explicit Search History)**: `LibrarySearchRepository` exposes `getSearchHistory`, `recordSearchQuery`, `removeSearchHistory`, `clearSearchHistory` rather than recording automatically on every search query emission to prevent incomplete typing fragments from polluting history.
- **Decision 2 (Domain Model Shape)**: Introduced `DirectoryContent(directory, cueAlbums, metadataMap, isEnriching)` aggregating directory files, CUE sheets, and track metadata into an atomic stream emission.
- **Decision 3 (SWR isEnriching Lifecycle)**: SWR stream emits cached snapshot at 0ms; emits newly fetched remote files with `isEnriching = true`; and emits fully resolved metadata and CUE albums with `isEnriching = false`, providing clean spinner termination.
- **Decision 4 (Lean Projector)**: `DirectoryBrowserViewModel` shrinks from 697 lines to under 350 lines, shedding 3 dependencies (from 7 down to 4) and removing 4 manual coroutine job handles.
