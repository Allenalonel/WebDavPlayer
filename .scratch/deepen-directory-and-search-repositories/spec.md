# Specification: Deepen Directory and Search Repositories and Slim DirectoryBrowserViewModel

## Problem Statement

Following the successful decomposition of the domain session God class (ADR-0023) and cross-server playlist self-healing (ADR-0022), the primary remaining architectural friction in WebDavPlayer resides in the media library and search presentation layer:

1. **`DirectoryRepository` is Shallow**: The existing repository only returns raw `RemoteDirectory` file trees (`ListDirectoryResult`), completely oblivious to CUE sheet virtual tracks and track metadata tags.
2. **`DirectoryBrowserViewModel` is a General Contractor**: Because the repository fails to provide enriched domain content, `DirectoryBrowserViewModel` (697 lines, 7 dependencies) is forced to orchestrate low-level network fetching, CUE virtual track resolution (`CueSheetResolver`), asynchronous HTTP Range metadata extraction (`TrackMetadataRepository`), and manual reconciliation of three disparate data dictionaries across four separate coroutine job handles (`metadataObserverJob`, `metadataResolutionJob`, `cueResolutionJob`, `activeRefreshJob`).
3. **`SearchHistoryStore` is a Shallow Hypothetical Seam**: Search history persistence is segregated behind a 4-method interface with a single 30-line SharedPreferences implementation. Calling code is forced to manage two independent handles (searching via `LibrarySearchRepository` and recording history via `SearchHistoryStore`).

## Solution

1. **Internalize Search History into `LibrarySearchRepository`**:
   - Eliminate `SearchHistoryStore` as an independent seam (passing the deletion test).
   - Deepen `LibrarySearchRepository` to manage persistent search history alongside instant full-text library queries, exposing explicit query recording and management methods (`recordSearchQuery`, `getSearchHistory`, `removeSearchHistory`, `clearSearchHistory`).
2. **Deepen `DirectoryRepository` with Content Enrichment**:
   - Introduce the unified domain model `DirectoryContent` aggregating the remote directory, parsed CUE albums, track metadata tags, and an `isEnriching` lifecycle flag.
   - Add `observeDirectoryContent(server, path, forceRefresh)` to `DirectoryRepository`, encapsulating `CueSheetResolver` and `TrackMetadataRepository` behind the repository seam.
   - SWR Emission Lifecycle: Emit cached directory content immediately (0ms); emit freshly fetched remote file lists with `isEnriching = true`; and emit fully enriched snapshots with `isEnriching = false` upon completion of CUE parsing and metadata resolution.
3. **Slim `DirectoryBrowserViewModel` into a Lean State Projector**:
   - Remove `trackMetadataRepository`, `cueSheetResolver`, and `searchHistoryStore` from `DirectoryBrowserViewModel`, reducing constructor dependencies from 7 to 4.
   - Eliminate the 4 manual coroutine job handles and the 80-line `processDirectoryCueAlbums` coordinator in the ViewModel.
   - The ViewModel directly projects `DirectoryContent` emissions into `DirectoryBrowserUiState`, shrinking from ~700 lines to under 350 lines.

## User Stories

1. As a music listener browsing a remote WebDAV directory, I want to see album cover thumbnails, track metadata (artist, album, duration), and CUE virtual track albums seamlessly populated, so that directory browsing feels instant and complete.
2. As a music listener performing a pull-to-refresh on a directory, I want the refresh indicator to animate smoothly while remote directory changes and new metadata are being resolved, and dismiss cleanly when enrichment finishes.
3. As a music listener searching my music library, I want my submitted search queries to be remembered and presented as recent history chips, so that I can easily repeat past searches without accumulating incomplete search fragments while typing.
4. As an Android developer and maintainer, I want directory content assembly (files + CUE sheets + metadata) to be encapsulated behind a single domain repository seam, so that future UI screens (e.g. tablet split view or folder picker) can consume enriched directory contents with zero duplicated coordination code.
5. As an Android developer and maintainer, I want `DirectoryBrowserViewModel` to be a pure state projector with minimal dependencies, so that UI tests require only 3 focused mocks instead of 6 intertwined dependencies.

## Implementation Decisions

1. **Explicit Search History Recording**:
   To prevent intermediate typing keystrokes from polluting search history, `LibrarySearchRepository` provides explicit methods:
   - `fun getSearchHistory(): List<String>`
   - `fun recordSearchQuery(query: String): List<String>`
   - `fun removeSearchHistory(query: String): List<String>`
   - `fun clearSearchHistory()`
   The UI invokes `recordSearchQuery` when a query is submitted or selected from recent searches.
2. **Unified `DirectoryContent` Domain Model**:
   ```kotlin
   data class DirectoryContent(
       val directory: RemoteDirectory,
       val cueAlbums: List<CueAlbumItem> = emptyList(),
       val metadataMap: Map<String, TrackMetadata> = emptyMap(),
       val isEnriching: Boolean = false,
   )
   ```
   Keeps existing Compose UI models (`AudioTrackItemRow`, `CueAlbumCard`) 100% compatible with minimal churn.
3. **SWR Lifecycle with `isEnriching`**:
   - Cache hit: Immediately emits cached `DirectoryContent` (0ms).
   - Network revalidation: Emits updated directory tree with `isEnriching = true`.
   - Enrichment complete: Launches background CUE resolution and metadata extraction; on completion, emits updated `DirectoryContent` with `isEnriching = false`.
   - Errors: Transient network errors during background revalidation do not suppress previously emitted cached content.
4. **Lean ViewModel Projection**:
   - Constructor parameters:
     ```kotlin
     class DirectoryBrowserViewModel(
         private val serverRepository: ServerRepository? = null,
         private val directoryRepository: DirectoryRepository,
         val musicPlayerAppSession: MusicPlayerAppSession? = null,
         val librarySearchRepository: LibrarySearchRepository? = null,
     )
     ```
   - Drops `trackMetadataRepository`, `cueSheetResolver`, and `searchHistoryStore`.
   - Single reactive collection on `directoryRepository.observeDirectoryContent(...)`.

## Testing Decisions

- **LibrarySearchRepositoryTest**: Add dedicated tests verifying query recording, LRU capacity limit (20 items), duplicate avoidance, item removal, and full history clearing.
- **DirectoryRepositoryTest**: Add tests verifying SWR emission sequence: cached content emission (0ms) -> remote update emission (`isEnriching = true`) -> background enrichment emission (`isEnriching = false`, CUE albums and metadata populated).
- **DirectoryBrowserViewModelTest**: Refactor existing tests to mock `DirectoryRepository.observeDirectoryContent` and `LibrarySearchRepository`, removing mocks for CUE resolver, metadata repository, and search history store.
- **End-to-End Regression**: Ensure `EndToEndPlaylistAndSearchPipelineTest` and full unit test suite remain 100% green.

## Out of Scope

- Changing audio playback engine or Media3 streaming pipeline (governed by ADR-0016 / ADR-0019).
- Modifying cross-server playlist management or `PlaylistRepository` (governed by ADR-0020 / ADR-0022).
- Modifying `MusicPlayerAppSessionImpl` internal collaborators (governed by ADR-0023).

## Further Notes

- Governed by ADR-0008 (SWR Persistent Directory Cache), ADR-0020 (Instant Library Search), ADR-0021 (CueSheetResolver), and the upcoming ADR-0024.
- Adheres to the 600-line single-file cohesion budget established in `AGENTS.md`.
