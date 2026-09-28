# 03: Non-Disruptive Track Metadata Enrichment

**What to build:** Refactor `Media3AudioPlayerEngine.updateTrack()` to differentiate between actively playing tracks and idle/queued tracks, avoiding destructive `player.replaceMediaItem()` calls on the active audio timeline to eliminate rebuffering glitches and prevent stream interruption.

**Blocked by:** 02-dynamic-authenticated-media-source-factory

**Status:** completed

- [x] In `Media3AudioPlayerEngine.updateTrack()`, check if the targeted index corresponds to the currently active track and the player is in an active playing or buffering state.
- [x] For the actively playing track, update `mediaMetadata` and external presentation flows without invoking `player.replaceMediaItem()`, keeping the underlying decoder timeline intact and continuous.
- [x] For non-playing queue items (upcoming or historical), safely update the playlist item via `player.replaceMediaItem()` so subsequent track transitions pick up the enriched metadata.
- [x] UI components (`MiniPlayer`, `FullPlayerView`) continue to receive instantaneous reactive metadata updates through `MusicPlayerAppSessionImpl` without depending on timeline replacement.
- [x] Unit tests verify that calling `updateTrack()` on the active playing item does not reset current position, trigger player errors, or induce timeline rebuffering.

## Comments

- Refactored `Media3AudioPlayerEngine.updateTrack()` to differentiate active track playback state: updates `player.playlistMetadata` for playing/buffering active track without timeline rebuilding via `player.replaceMediaItem()`, and updates queue items via `player.replaceMediaItem()`.
- Verified reactive metadata dispatch through `MusicPlayerAppSessionImpl` with `sessionState.queue` and `playbackState` flows updating seamlessly.
- Verified test coverage in `Media3AudioPlayerEngineTest.reactiveSession_receivesEnrichedMetadata_whileActiveTimelineRemainsIntact` and regression tests.

