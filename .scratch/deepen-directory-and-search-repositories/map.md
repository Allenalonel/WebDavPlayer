# Map: Deepen Directory and Search Repositories and Slim DirectoryBrowserViewModel

## Overview
Refactoring the media library and search presentation layers by deepening `DirectoryRepository` and `LibrarySearchRepository`, eliminating the shallow `SearchHistoryStore`, and slimming `DirectoryBrowserViewModel` into a lean state projector.

## Issues Graph
- [x] **01-internalize-search-history-into-library-search-repository** (Resolved)
- [x] **02-deepen-directory-repository-with-content-enrichment** (Resolved)
- [x] **03-slim-directory-browser-viewmodel-and-verify-pipeline** (Resolved)

## Decisions So Far
- **Decision 1 (Explicit Search History)**: `LibrarySearchRepository` exposes `getSearchHistory`, `recordSearchQuery`, `removeSearchHistory`, `clearSearchHistory` rather than recording automatically on every search query emission to prevent incomplete typing fragments from polluting history. ([01-internalize-search-history-into-library-search-repository.md](issues/01-internalize-search-history-into-library-search-repository.md))
- **Decision 2 (Domain Model Shape)**: Introduced `DirectoryContent(directory, cueAlbums, metadataMap, isEnriching)` aggregating directory files, CUE sheets, and track metadata into an atomic stream emission. ([02-deepen-directory-repository-with-content-enrichment.md](issues/02-deepen-directory-repository-with-content-enrichment.md))
- **Decision 3 (SWR isEnriching Lifecycle)**: SWR stream emits cached snapshot at 0ms; emits newly fetched remote files with `isEnriching = true`; and emits fully resolved metadata and CUE albums with `isEnriching = false`, providing clean spinner termination. ([02-deepen-directory-repository-with-content-enrichment.md](issues/02-deepen-directory-repository-with-content-enrichment.md))
- **Decision 4 (Lean Projector)**: `DirectoryBrowserViewModel` shrinks from 697 lines to under 350 lines, shedding 3 dependencies (from 7 down to 4) and removing 4 manual coroutine job handles. ([03-slim-directory-browser-viewmodel-and-verify-pipeline.md](issues/03-slim-directory-browser-viewmodel-and-verify-pipeline.md))
