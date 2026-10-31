# 03: Consolidate Lyrics into TrackMetadataRepository

**What to build:**
Absorb dual-source lyrics resolution into `TrackMetadataRepository`.
Add `suspend fun resolveLyrics(server: WebDavServer, track: AudioTrack): Lyrics` to `TrackMetadataRepository` and implement in `TrackMetadataRepositoryImpl` (remote `.lrc` probing + embedded metadata fallback + `LrcParser` parsing).
Delete `LyricsRepository.kt` and `LyricsRepositoryImpl.kt` and remove the redundant 100-entry secondary LRU cache.
Update `PlaybackMetadataCoordinator` and `MusicPlayerAppSessionImpl` to only inject `TrackMetadataRepository`.
Migrate tests and update `WebDavApplication`.

**Blocked by:** 02

**Status:** open

- [ ] Add `resolveLyrics` to `TrackMetadataRepository` interface
- [ ] Implement dual-source lyrics resolution and caching in `TrackMetadataRepositoryImpl`
- [ ] Delete `domain/repository/LyricsRepository.kt` and `data/repository/LyricsRepositoryImpl.kt`
- [ ] Simplify `PlaybackMetadataCoordinator` to eliminate `lyricsRepository` constructor parameter
- [ ] Simplify `MusicPlayerAppSessionImpl` constructor to eliminate `lyricsRepository`
- [ ] Migrate `LyricsRepositoryTest` to test `TrackMetadataRepository.resolveLyrics`
- [ ] Update `WebDavApplication` and integration test mocks
- [ ] Ensure all tests pass cleanly
