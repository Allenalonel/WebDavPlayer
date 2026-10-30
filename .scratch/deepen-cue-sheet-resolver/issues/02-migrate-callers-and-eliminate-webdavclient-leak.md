# 02: Migrate Callers and Eliminate WebDavClient Leak

**What to build:** Refactor `DirectoryBrowserViewModel`, `MusicPlayerAppSessionImpl`, and `LibrarySearchRepositoryImpl` to consume `CueSheetResolver`, eliminating direct `WebDavClient` dependencies and redundant CUE parsing pipelines from UI and session layers. Deprecate or remove global static `CueTextCache`.

**Blocked by:** 01 (Build Deep CueSheetResolver with Bounded LRU Cache)

**Status:** open

- [ ] Update `DirectoryBrowserViewModel`:
  - Replace constructor parameter `webDavClient: WebDavClient?` with `cueSheetResolver: CueSheetResolver?`.
  - Refactor `processDirectoryCueAlbums` to delegate directly to `cueSheetResolver.resolveDirectoryCueAlbums(...)`.
- [ ] Update `MusicPlayerAppSessionImpl`:
  - Replace constructor parameter `webDavClient: WebDavClient?` with `cueSheetResolver: CueSheetResolver?`.
  - Refactor virtual track offset resolution in `playDirectoryTrack` and `transitionToQueueIndex` to delegate to `cueSheetResolver.resolveVirtualTracks(...)`.
- [ ] Update `LibrarySearchRepositoryImpl`:
  - Replace constructor parameter `cueTextProvider: ((Long, String) -> String?)?` with `cueSheetResolver: CueSheetResolver?`.
  - Delegate CUE virtual track matching in `search` to `cueSheetResolver.resolveVirtualTracks(...)`.
- [ ] Update `WebDavApplication` and `DirectoryBrowserViewModelFactory` to instantiate and inject `DefaultCueSheetResolver`.
- [ ] Remove public usage of global static `CueTextCache` across production code.
- [ ] Update existing tests in `DirectoryBrowserViewModelTest`, `MusicPlayerAppSessionTest`, and `LibrarySearchRepositoryTest` to mock or provide `CueSheetResolver`.

## Comments
