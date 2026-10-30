# 02: Deepen DirectoryRepository with Content Enrichment

**What to build:** Deepen `DirectoryRepository` by adding a unified `observeDirectoryContent(server, path, forceRefresh)` seam that encapsulates background CUE resolution and metadata extraction behind the repository seam. Introduce domain model `DirectoryContent` containing the remote directory, parsed CUE albums, track metadata tags, and an `isEnriching` lifecycle flag. `DirectoryRepositoryImpl` will take `CueSheetResolver` and `TrackMetadataRepository` as internal collaborators to produce enriched reactive directory streams.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Create `DirectoryContent` domain model in `domain/model/DirectoryContent.kt`
- [ ] Add `observeDirectoryContent(server: WebDavServer, path: String, forceRefresh: Boolean = false): Flow<DirectoryContent>` to `DirectoryRepository`
- [ ] Implement `observeDirectoryContent` in `DirectoryRepositoryImpl`, orchestrating `CueSheetResolver` and `TrackMetadataRepository`
- [ ] Support SWR lifecycle: emit cached content immediately (0ms), emit remote file update with `isEnriching = true`, and emit enriched snapshot with `isEnriching = false` once background CUE & metadata resolution completes
- [ ] Add unit tests in `DirectoryRepositoryTest` verifying SWR emissions, CUE album integration, and metadata population
- [ ] Ensure all `DirectoryRepositoryTest` tests pass cleanly
