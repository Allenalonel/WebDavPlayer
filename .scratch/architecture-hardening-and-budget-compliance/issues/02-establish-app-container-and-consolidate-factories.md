# 02: Establish AppContainer and Consolidate ViewModel Factories

**What to build:**
Eliminate scattered factory boilerplate and purge the dangerous 50-line rogue "shadow dependency graph" from `DirectoryBrowserViewModelFactory.kt`.
Establish a unified `AppContainer` dependency interface and a centralized `AppViewModelFactory`:
1. **Define `AppContainer` Interface**:
   - Create `AppContainer` interface in `com.webdav.player.di` (or root package) declaring accessors for:
     `serverRepository`, `directoryRepository`, `librarySearchRepository`, `playlistRepository`,
     `trackMetadataRepository`, `cueSheetResolver`, `playerEngine`, `musicPlayerAppSession`, `sessionStore`, `webDavClient`.
   - Make `WebDavApplication` implement `AppContainer`.
2. **Implement Unified `AppViewModelFactory`**:
   - Provide a type-safe `AppViewModelFactory(container: AppContainer)`:
     - Dispatches `DirectoryBrowserViewModel`
     - Dispatches `ServerManagementViewModel`
     - Dispatches `PlaylistListViewModel`
     - Dispatches `PlaylistDetailViewModel` (taking `playlistId`)
3. **Purge Rogue Shadow Dependency Graphs**:
   - Remove the fallback `run { AppDatabase.getInstance(context); ... }` in `DirectoryBrowserViewModelFactory.kt` and `ServerManagementViewModelFactory.kt` which creates duplicate in-memory databases and repositories.
   - Deprecate or replace the 4 separate factory classes with clean delegations to `AppViewModelFactory`.
4. **Update MainActivity & Tests**:
   - Update `MainActivity` viewmodel creation to consume `app.container`.
   - Ensure `DirectoryBrowserViewModelTest`, `ServerManagementViewModelTest`, `PlaylistListViewModelTest`, and `PlaylistDetailViewModelTest` run green.

**Blocked by:** None

**Status:** open

- [ ] Define `AppContainer` interface and implement on `WebDavApplication`
- [ ] Create unified `AppViewModelFactory` supporting all 4 ViewModels
- [ ] Purge fallback shadow instantiation in `DirectoryBrowserViewModelFactory`
- [ ] Refactor `MainActivity` to instantiate ViewModels via unified container
- [ ] Verify all ViewModel unit tests pass without regression
