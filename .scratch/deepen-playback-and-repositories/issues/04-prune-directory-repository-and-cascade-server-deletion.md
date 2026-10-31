# 04: Prune DirectoryRepository and Cascade Server Deletion

**What to build:**
1. Prune unused legacy interfaces from `DirectoryRepository`: remove `observeDirectory(server, path)` and `listDirectory(server, path)` and `ListDirectoryResult`. The primary seam is `observeDirectoryContent(server, path, forceRefresh): Flow<DirectoryContent>`.
2. Encapsulate initial CUE skeleton generation inside `CueSheetResolver` (e.g. `resolveInitialCueAlbums(directory: RemoteDirectory): List<CueAlbumItem>`), removing the direct `CueAssociationHelper` cross-package import from `DirectoryRepositoryImpl`.
3. In `ServerRepositoryImpl.deleteServer(id)`, execute an atomic cascade deletion to clean up `directory_cache` and `track_metadata` Room records for that `serverId`.
4. Run full test suite across the repository and verify strict adherence to the 600-line budget.

**Blocked by:** 03

**Status:** resolved

- [x] Add `resolveInitialCueAlbums(directory: RemoteDirectory): List<CueAlbumItem>` to `CueSheetResolver` & `DefaultCueSheetResolver`
- [x] Refactor `DirectoryRepositoryImpl` to call `cueSheetResolver.resolveInitialCueAlbums`, removing `CueAssociationHelper` import
- [x] Prune unused `observeDirectory` and `listDirectory` from `DirectoryRepository` & `DirectoryRepositoryImpl`
- [x] Update `ServerRepositoryImpl.deleteServer(id)` to cascade delete from `directoryCacheDao` and `trackMetadataDao`
- [x] Migrate/update tests in `DirectoryRepositoryTest`, `ServerRepositoryTest`, and integration suites
- [x] Run full test suite (`gradle test`) to verify 100% pass rate
- [x] Check file line counts to verify all files are < 600 lines

## Answer
Pruned legacy directory interfaces, encapsulated initial CUE skeleton, and cascaded server deletion:
1. Added `resolveInitialCueAlbums(directory: RemoteDirectory): List<CueAlbumItem>` to `CueSheetResolver` and implemented in `DefaultCueSheetResolver`.
2. Refactored `DirectoryRepositoryImpl` to delegate initial CUE placeholder generation to `CueSheetResolver.resolveInitialCueAlbums`, eliminating the cross-package import of `CueAssociationHelper`.
3. Pruned unused legacy methods `observeDirectory` and `listDirectory` from `DirectoryRepository` and `DirectoryRepositoryImpl`.
4. Updated `ServerRepositoryImpl.deleteServer(id)` to cascade delete records from `directoryCacheDao` and `trackMetadataDao`, wiping orphan caches upon server deletion.
5. Migrated and cleaned up `DirectoryRepositoryTest`, `ServerRepositoryTest`, and integration suites.
6. Ran full test suite with 100% test pass rate and verified all files remain strictly under the 600-line budget.
