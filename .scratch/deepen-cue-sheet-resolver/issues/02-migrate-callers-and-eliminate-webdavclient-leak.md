# 02: Migrate Callers and Eliminate WebDavClient Leak

**What to build:** Refactor `DirectoryBrowserViewModel`, `MusicPlayerAppSessionImpl`, and `LibrarySearchRepositoryImpl` to consume `CueSheetResolver`, eliminating direct `WebDavClient` dependencies and redundant CUE parsing pipelines from UI and session layers. Deprecate or remove global static `CueTextCache`.

**Blocked by:** 01 (Build Deep CueSheetResolver with Bounded LRU Cache)

**Status:** resolved

- [x] Update `DirectoryBrowserViewModel`:
  - Replace constructor parameter `webDavClient: WebDavClient?` with `cueSheetResolver: CueSheetResolver?`.
  - Refactor `processDirectoryCueAlbums` to delegate directly to `cueSheetResolver.resolveDirectoryCueAlbums(...)`.
- [x] Update `MusicPlayerAppSessionImpl`:
  - Replace constructor parameter `webDavClient: WebDavClient?` with `cueSheetResolver: CueSheetResolver?`.
  - Refactor virtual track offset resolution in `playDirectoryTrack` and `transitionToQueueIndex` to delegate to `cueSheetResolver.resolveVirtualTracks(...)`.
- [x] Update `LibrarySearchRepositoryImpl`:
  - Replace constructor parameter `cueTextProvider: ((Long, String) -> String?)?` with `cueSheetResolver: CueSheetResolver?`.
  - Delegate CUE virtual track matching in `search` to `cueSheetResolver.resolveVirtualTracks(...)`.
- [x] Update `WebDavApplication` and `DirectoryBrowserViewModelFactory` to instantiate and inject `DefaultCueSheetResolver`.
- [x] Remove public usage of global static `CueTextCache` across production code.
- [x] Update existing tests in `DirectoryBrowserViewModelTest`, `MusicPlayerAppSessionTest`, and `LibrarySearchRepositoryTest` to mock or provide `CueSheetResolver`.

## Comments

Migrated DirectoryBrowserViewModel, MusicPlayerAppSessionImpl, and LibrarySearchRepositoryImpl to consume CueSheetResolver. Eliminated WebDavClient leaks and global CueTextCache usage across production layers. All test suites updated and passing 100%.
