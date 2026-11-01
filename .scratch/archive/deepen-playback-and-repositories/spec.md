# Spec: Deepen Playback Engine and Consolidate Repositories

## Summary
Refactor the playback core and repository layer to eliminate hypothetical seams, fake delegations, circular dependencies, and cross-repository leakages in accordance with ADR-0025:
1. Fold `WebDavMediaSourceAdapter` into `Media3AudioPlayerEngine` as an internal collaborator and purge 10 test downcasts.
2. Decouple `PlaybackSessionHost` from `AudioPlayerEngine` fake delegation, eliminate boot-time `as? PlaybackSessionHost` downcasting, and break the host-session circular dependency.
3. Consolidate dual-source lyrics resolution into `TrackMetadataRepository`, delete `LyricsRepository` and `LyricsRepositoryImpl`, and eliminate redundant secondary LRU caching.
4. Prune unused legacy interfaces (`observeDirectory`, `listDirectory`) from `DirectoryRepository`, encapsulate initial CUE skeleton generation in `CueSheetResolver`, and cascade server deletion across `directory_cache` and `track_metadata`.

## Goals
- **Zero Hypothetical Seams**: Delete single-implementation wrapper interfaces `WebDavMediaSourceAdapter` and `LyricsRepository`.
- **Zero Downcasting**: Eradicate all 10 `as DefaultWebDavMediaSourceAdapter` test casts and `as? PlaybackSessionHost` application startup cast.
- **Acyclic Seams**: Decouple `PlaybackSessionHost` and `MusicPlayerAppSessionImpl` into a strict single-direction dependency (host observes and dispatches to session; session talks directly to `AudioPlayerEngine`).
- **High Locality**: Consolidate metadata & lyrics into `TrackMetadataRepository`; consolidate directory SWR & CUE into `DirectoryRepository` & `CueSheetResolver`.
- **Atomic Server Deletion**: Cascade server deletion to wipe `directory_cache` and `track_metadata`.
- **Budget Compliance**: All modified files stay strictly under 600 lines; 100% test pass rate.

## Ticket Breakdown
1. **01-fold-media-source-adapter-into-player-engine.md**: Internalize MediaSource construction in `Media3AudioPlayerEngine` and purge `WebDavMediaSourceAdapter`.
2. **02-decouple-playback-session-host-and-break-circular-loop.md**: Remove `by playerEngine` fake delegation, inject pure engine into session, and eliminate startup cast.
3. **03-consolidate-dual-source-lyrics-into-track-metadata-repository.md**: Move `resolveLyrics` to `TrackMetadataRepository` and remove `LyricsRepository`.
4. **04-prune-directory-repository-and-cascade-server-deletion.md**: Prune dead directory methods, encapsulate CUE skeleton, cascade server deletion, and full regression verification.
