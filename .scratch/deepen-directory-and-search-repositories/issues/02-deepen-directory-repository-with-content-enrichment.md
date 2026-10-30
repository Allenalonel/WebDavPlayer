# 02: Deepen DirectoryRepository with Content Enrichment

**What to build:** Deepen `DirectoryRepository` by adding a unified `observeDirectoryContent(server, path, forceRefresh)` seam that encapsulates background CUE resolution and metadata extraction behind the repository seam. Introduce domain model `DirectoryContent` containing the remote directory, parsed CUE albums, track metadata tags, and an `isEnriching` lifecycle flag. `DirectoryRepositoryImpl` will take `CueSheetResolver` and `TrackMetadataRepository` as internal collaborators to produce enriched reactive directory streams.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] Create `DirectoryContent` domain model in `domain/model/DirectoryContent.kt`
- [x] Add `observeDirectoryContent(server: WebDavServer, path: String, forceRefresh: Boolean = false): Flow<DirectoryContent>` to `DirectoryRepository`
- [x] Implement `observeDirectoryContent` in `DirectoryRepositoryImpl`, orchestrating `CueSheetResolver` and `TrackMetadataRepository`
- [x] Support SWR lifecycle: emit cached content immediately (0ms), emit remote file update with `isEnriching = true`, and emit enriched snapshot with `isEnriching = false` once background CUE & metadata resolution completes
- [x] Add unit tests in `DirectoryRepositoryTest` verifying SWR emissions, CUE album integration, and metadata population
- [x] Ensure all `DirectoryRepositoryTest` tests pass cleanly

## Answer
Deepened `DirectoryRepository` by adding `observeDirectoryContent(server, path, forceRefresh): Flow<DirectoryContent>` seam. Introduced the unified domain model `DirectoryContent` aggregating files, CUE albums, track metadata tags, and the `isEnriching` flag. `DirectoryRepositoryImpl` encapsulates `CueSheetResolver` and `TrackMetadataRepository` as internal collaborators, managing the complete SWR lifecycle (0ms cached hit -> remote update -> enriched completion with `isEnriching = false`). Comprehensive unit tests in `DirectoryRepositoryTest` verify the entire lifecycle and pass cleanly.
