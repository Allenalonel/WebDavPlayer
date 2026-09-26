# 03: Lifecycle-Aware Cover Art Storage and Cascade Deletion

**What to build:** Locally extracted cover art thumbnails are stored in the application's cache directory with automatic quota management, and deleting a WebDAV server cascades into the physical removal of its associated thumbnail files from disk.

**Blocked by:** None (can start immediately)

**Status:** completed

- [x] Migrate thumbnail directory location from `context.filesDir/covers/` to `context.cacheDir/covers/` in `CoverArtStorageImpl`.
- [x] Add `deleteServerCovers(serverId: Long)` to `CoverArtStorage` interface and implementation, deleting all disk files matching the server prefix pattern.
- [x] Update `ServerRepositoryImpl.deleteServer(serverId)` (or session orchestration) to trigger `coverArtStorage.deleteServerCovers(serverId)`, ensuring physical cleanup accompanies Room database row deletion.
- [x] Add disk quota management in `CoverArtStorageImpl` (e.g. 50MB quota check, pruning the oldest files by last-modified time when threshold is exceeded).
- [x] Unit and repository tests verify thumbnail creation, retrieval, disk quota pruning, and cascading server deletion.
