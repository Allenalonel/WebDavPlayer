# 03: Consolidate Lyrics into TrackMetadataRepository

**What to build:**
Absorb dual-source lyrics resolution into `TrackMetadataRepository`.
Add `suspend fun resolveLyrics(server: WebDavServer, track: AudioTrack): Lyrics` to `TrackMetadataRepository` and implement in `TrackMetadataRepositoryImpl` (remote `.lrc` probing + embedded metadata fallback + `LrcParser` parsing).
Delete `LyricsRepository.kt` and `LyricsRepositoryImpl.kt` and remove the redundant 100-entry secondary LRU cache.
Update `PlaybackMetadataCoordinator` and `MusicPlayerAppSessionImpl` to only inject `TrackMetadataRepository`.
Migrate tests and update `WebDavApplication`.

**Blocked by:** 02

**Status:** resolved

- [x] Add `resolveLyrics` to `TrackMetadataRepository` interface
- [x] Implement dual-source lyrics resolution and caching in `TrackMetadataRepositoryImpl`
- [x] Delete `domain/repository/LyricsRepository.kt` and `data/repository/LyricsRepositoryImpl.kt`
- [x] Simplify `PlaybackMetadataCoordinator` to eliminate `lyricsRepository` constructor parameter
- [x] Simplify `MusicPlayerAppSessionImpl` constructor to eliminate `lyricsRepository`
- [x] Migrate `LyricsRepositoryTest` to test `TrackMetadataRepository.resolveLyrics`
- [x] Update `WebDavApplication` and integration test mocks
- [x] Ensure all tests pass cleanly

## Answer
Consolidated dual-source lyrics resolution into `TrackMetadataRepository`:
1. Added `suspend fun resolveLyrics(server: WebDavServer, track: AudioTrack): Lyrics` to `TrackMetadataRepository` with a default implementation returning `Lyrics.EMPTY`.
2. Implemented dual-source lyrics resolution in `TrackMetadataRepositoryImpl`: remote `.lrc` probing via `webDavClient`, falling back to Room cached / on-demand embedded metadata, parsed through `LrcParser`.
3. Deleted `domain/repository/LyricsRepository.kt` and `data/repository/LyricsRepositoryImpl.kt`, eliminating the separate 100-entry secondary LRU cache.
4. Simplified `PlaybackMetadataCoordinator` and `MusicPlayerAppSessionImpl` to eliminate all `lyricsRepository` parameters, routing all lyrics and metadata through `TrackMetadataRepository`.
5. Migrated `LyricsRepositoryTest` to directly verify `TrackMetadataRepositoryImpl.resolveLyrics`.
6. Updated `WebDavApplication` and integration suites. 100% test pass rate with all files strictly within the 600-line budget.
