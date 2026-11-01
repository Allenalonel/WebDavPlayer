# 02: Deepen Search Indexing and Encapsulate CUE Association

**What to build:**
Refactor `data/repository/LibrarySearchRepositoryImpl.kt` to optimize the search pipeline and seal the CUE internal seam:
1. **Encapsulate CUE Virtual Track Association**:
   - Completely remove `import com.webdav.player.data.cue.CueAssociationHelper` from `LibrarySearchRepositoryImpl.kt`.
   - Delegate CUE sheet detection and virtual track association strictly to `CueSheetResolver` (using `resolveDirectoryCueAlbums` or `getCachedVirtualTracks`).
2. **Optimize Directory Search Pipeline**:
   - Prevent unnecessary full-table JSON deserialization during rapid typing.
   - For matching `DirectoryCacheEntity` items returned by `searchDirectoryCacheFlow`, verify if `path` or `cleanQuery` matches before deep parsing.
   - Cache deserialized directory trees transiently to prevent duplicate JSON tree parsing when combining with track metadata flows.
3. **Preserve Complete Search Coverage**:
   - Directory matches: directory name and full path.
   - Subdirectory matches.
   - Regular audio tracks: title, artist, album, file name, path.
   - CUE virtual tracks: virtual track title, performer, album.

**Blocked by:** None

**Status:** resolved

- [x] Remove `CueAssociationHelper` import from `LibrarySearchRepositoryImpl.kt`
- [x] Refactor CUE virtual track search to route strictly through `CueSheetResolver`
- [x] Optimize search pipeline to prevent redundant JSON deserialization
- [x] Run `LibrarySearchRepositoryTest` to confirm all 11 test cases pass 100%

## Answer
- Made `CueAssociationHelper` an `internal object` in package `com.webdav.player.data.cue`.
- Eliminated all imports of `CueAssociationHelper` outside `data/cue/` by having `PlaybackQueueManager` route CUE resolution via `CueSheetResolver.resolveInitialCueAlbums`.
- Added transient in-memory LRU caching (`directoryCache`) and pre-filtering in `LibrarySearchRepositoryImpl` to eliminate redundant JSON deserialization during typing.
- Streamlined `LibrarySearchRepositoryImpl.kt` down to 242 lines (well below the 250-line target and 600-line ceiling).
- All unit tests in `LibrarySearchRepositoryTest` pass 100%.
