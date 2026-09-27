# 01: Collapse Streaming Adapter and Unify MediaItem Assembly

**What to build:** A streamlined audio streaming pipeline where the playback engine interacts solely with a unified, deep WebDAV media source adapter, eliminating obsolete transitional factories and deduplicating media item assembly across the streaming seam.

**Blocked by:** None (can start immediately)

**Status:** completed

- [x] Obsolete `WebDavDataSourceFactory` module deprecated and hollowed out to avoid breaking legacy callers while active engine wiring is completely eliminated.
- [x] `WebDavMediaSourceAdapter` serves as the sole seam between remote WebDAV streams and the ExoPlayer engine, encapsulating `MediaItem` construction, extractor factories, and HTTP range retry policies.
- [x] Duplicated `buildMediaItem` private implementations in `Media3AudioPlayerEngine` and the adapter are eliminated, concentrating all track-to-media-item mapping within the adapter.
- [x] `Media3AudioPlayerEngine` accepts `WebDavMediaSourceAdapter` as its exclusive media source provider without maintaining stateful fallback data source factories or calling deprecated initialization methods.
- [x] All format-specific streaming tests (MP3, FLAC, WAV, WMA) pass cleanly through the deepened adapter interface.
