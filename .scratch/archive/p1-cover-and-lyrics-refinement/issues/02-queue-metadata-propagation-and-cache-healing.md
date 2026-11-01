# 02: Queue Metadata Propagation and Cache Healing

**What to build:** Propagate pre-cached metadata from `DirectoryBrowserViewModel` and Room into newly created `PlaybackQueue` items upon track click, and implement self-healing cache logic so that transient network failures do not poison Room with permanent null artwork entries.

**Blocked by:** 01-adaptive-range-and-folder-cover-fallback.md

**Status:** completed

- [x] In `DirectoryBrowserViewModel.onAudioTrackClicked`, look up the clicked directory's cached metadata from `_uiState.value.metadataMap` and pass the enriched `AudioTrack` or metadata list to `musicPlayerAppSession.playDirectoryTrack`.
- [x] In `MusicPlayerAppSessionImpl.playDirectoryTrack`, construct `AudioTrack` items using available metadata so that `coverThumbnailPath`, `durationMs`, and tags are immediately populated in the active queue.
- [x] In `MusicPlayerAppSessionImpl`, ensure the reactive metadata flow immediately enriches the queue tracks from Room without waiting for subsequent database write events.
- [x] In `TrackMetadataRepositoryImpl`, differentiate transient network errors from genuine absence of metadata: do not persist permanent null entries for failed network requests, allowing automatic retry on subsequent folder visits.
- [x] Add unit tests in `DirectoryBrowserViewModelTest` and `MusicPlayerAppSessionTest` verifying immediate metadata and artwork presence in the queue upon track click.
