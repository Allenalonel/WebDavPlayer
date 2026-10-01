# 02: Internalize Resolver and Purify Lyrics Repository

**What to build:**
Retire the `TrackMetadataResolver` public domain interface, turn `DefaultTrackMetadataResolver` into an `internal` class scoped to the data layer, and update `LyricsRepositoryImpl` and `WebDavApplication` to depend exclusively on `TrackMetadataRepository`.

**Blocked by:** 01-deepen-track-metadata-repository-with-thumbnail-validation-and-self-healing

**Status:** completed

- [x] Remove public domain interface `TrackMetadataResolver.kt`.
- [x] Make `DefaultTrackMetadataResolver` an `internal` class used directly by `TrackMetadataRepositoryImpl`.
- [x] Refactor `LyricsRepositoryImpl` constructor to accept only `TrackMetadataRepository` (removing resolver and simplifying dual-branch code).
- [x] Update `WebDavApplication` to stop exposing `TrackMetadataResolver` and wire only `TrackMetadataRepository`.
- [x] Update `LyricsRepositoryTest` to test exclusively through `TrackMetadataRepository`.
