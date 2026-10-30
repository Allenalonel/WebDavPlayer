# 01: Build Deep CueSheetResolver with Bounded LRU Cache

**What to build:** Establish `CueSheetResolver` as an independent deep domain module that encapsulates CUE text fetching, bounded in-memory LRU caching, candidate parent audio matching, and AST track parsing behind a small, highly-leveraged interface.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] Create `CueSheetResolver` interface under `domain/cue/CueSheetResolver.kt` with methods:
  - `suspend fun resolveDirectoryCueAlbums(server: WebDavServer, directory: RemoteDirectory, parentDurationsByPath: Map<String, Long> = emptyMap()): List<CueAlbumItem>`
  - `suspend fun resolveVirtualTracks(server: WebDavServer, cuePath: String, parentAudioPath: String, totalDurationMs: Long? = null): List<VirtualTrack>`
  - `fun clearCache()`
- [x] Implement `DefaultCueSheetResolver` under `data/cue/DefaultCueSheetResolver.kt` taking `WebDavClient` and optional `CoroutineDispatcher`.
- [x] Internalize a thread-safe, bounded in-memory LRU cache (capacity 50) for transient CUE text, strictly adhering to ADR-0002 zero-disk persistence.
- [x] Internalize `CueAssociationHelper` and `CueParser` within the resolver's pipeline, handling CUE FILE directives, base name matching, single-pair directory fallback, and duration calculation.
- [x] Write comprehensive unit tests in `DefaultCueSheetResolverTest` verifying directory album resolution, virtual track resolution, LRU eviction, and network fallback.

## Comments

Built `CueSheetResolver` domain interface and `DefaultCueSheetResolver` with internal 50-item synchronized bounded LRU cache, encapsulating network fetching, association heuristics, and AST virtual track parsing. Verified with 14 unit tests in `DefaultCueSheetResolverTest`.

## Answer

Implemented `CueSheetResolver` interface in `domain/cue` and `DefaultCueSheetResolver` in `data/cue` with thread-safe 50-entry bounded LRU memory cache, encapsulating network fetching, association heuristics, and AST virtual track parsing. 14 unit tests in `DefaultCueSheetResolverTest` pass 100%.
