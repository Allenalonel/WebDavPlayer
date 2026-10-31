# 04: Prune DirectoryRepository and Cascade Server Deletion

**What to build:**
1. Prune unused legacy interfaces from `DirectoryRepository`: remove `observeDirectory(server, path)` and `listDirectory(server, path)` and `ListDirectoryResult`. The primary seam is `observeDirectoryContent(server, path, forceRefresh): Flow<DirectoryContent>`.
2. Encapsulate initial CUE skeleton generation inside `CueSheetResolver` (e.g. `resolveInitialCueAlbums(directory: RemoteDirectory): List<CueAlbumItem>`), removing the direct `CueAssociationHelper` cross-package import from `DirectoryRepositoryImpl`.
3. In `ServerRepositoryImpl.deleteServer(id)`, execute an atomic cascade deletion to clean up `directory_cache` and `track_metadata` Room records for that `serverId`.
4. Run full test suite across the repository and verify strict adherence to the 600-line budget.

**Blocked by:** 03

**Status:** open

- [ ] Add `resolveInitialCueAlbums(directory: RemoteDirectory): List<CueAlbumItem>` to `CueSheetResolver` & `DefaultCueSheetResolver`
- [ ] Refactor `DirectoryRepositoryImpl` to call `cueSheetResolver.resolveInitialCueAlbums`, removing `CueAssociationHelper` import
- [ ] Prune unused `observeDirectory` and `listDirectory` from `DirectoryRepository` & `DirectoryRepositoryImpl`
- [ ] Update `ServerRepositoryImpl.deleteServer(id)` to cascade delete from `directoryCacheDao` and `trackMetadataDao`
- [ ] Migrate/update tests in `DirectoryRepositoryTest`, `ServerRepositoryTest`, and integration suites
- [ ] Run full test suite (`gradle test`) to verify 100% pass rate
- [ ] Check file line counts to verify all files are < 600 lines
