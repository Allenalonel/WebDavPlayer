# 03: Offline Playlist Reachability Self-Healing on Network Restoration

**What to build:** Automatic recovery of dimmed playlist tracks that were marked unreachable during a network outage. When network connectivity is restored, the playlist repository asynchronously scans for tracks flagged as `SERVER_UNREACHABLE`, collects the unique WebDAV servers, and dispatches lightweight reachability probes (`PROPFIND Depth: 0`). For servers that respond successfully, their associated playlist tracks are batch-restored back to active status, automatically re-enabling them without requiring the user to manually click each failed track or reload the app.

**Blocked by:** 01 (System Network Connectivity Reactive Observer with Debounce)

**Status:** resolved

- [x] `PlaylistDao` provides batch queries for unreachable server IDs and batch updates to clear `SERVER_UNREACHABLE` flags
- [x] `PlaylistRepository` implements `triggerReachabilitySelfHealing()` dispatching concurrent lightweight connection tests
- [x] Successful server connection test updates the database and causes playlist UI to reactively re-enable dimmed tracks
- [x] Reachability self-healing runs asynchronously in the background with concurrency deduplication
- [x] Unit tests in `PlaylistRepositoryTest` verify dimmed tracks are restored to active status upon successful network probe

## Comments
- Added `getDistinctServerIdsForInactiveReason(reason: String)` and `clearServerUnreachableStatus(serverId: Long, reason: String)` batch operations to `PlaylistDao`.
- Implemented `triggerReachabilitySelfHealing()` on `PlaylistRepository` / `PlaylistRepositoryImpl` with `AtomicBoolean` deduplication and concurrent async reachability probes via `webDavClient.testConnection`.
- Clears database status and synchronizes `_inactiveTracksFlow` to reactively re-enable dimmed tracks in UI.
- Injected `webDavClient` into `PlaylistRepositoryImpl` in `WebDavApplication`.
- Comprehensive unit tests added in `PlaylistRepositoryTest` covering DAO batch methods and repository self-healing, all passing.
