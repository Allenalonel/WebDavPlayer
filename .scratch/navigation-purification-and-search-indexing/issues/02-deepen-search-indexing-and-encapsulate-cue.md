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

**Status:** open

- [ ] Remove `CueAssociationHelper` import from `LibrarySearchRepositoryImpl.kt`
- [ ] Refactor CUE virtual track search to route strictly through `CueSheetResolver`
- [ ] Optimize search pipeline to prevent redundant JSON deserialization
- [ ] Run `LibrarySearchRepositoryTest` to confirm all 11 test cases pass 100%
