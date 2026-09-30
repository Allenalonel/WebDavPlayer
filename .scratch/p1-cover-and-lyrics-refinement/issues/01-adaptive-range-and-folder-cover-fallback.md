# 01: Adaptive Range and Folder Cover Fallback

**What to build:** Expand `TrackMetadataRepositoryImpl` HTTP Range fetching from 128KB to an adaptive 512KB (`0L..524287L`), add secondary Range completion for oversized ID3/FLAC tags, and implement folder-level artwork sniffing (`cover.jpg`, `folder.jpg`, `front.jpg`, `${filename}.jpg`) when audio files lack embedded art.

**Blocked by:** None (can start immediately)

**Status:** completed

- [x] `TrackMetadataRepositoryImpl` increases default Range size to 512KB to capture embedded album artwork without cross-boundary truncation.
- [x] If ID3 header indicates `tagSize > 512KB`, perform a secondary Range request to fetch the remaining tag bytes up to a reasonable cap (e.g. 2MB).
- [x] Implement folder-level artwork detection in `TrackMetadataRepositoryImpl`: when audio files in a directory have no embedded artwork, probe for `cover.jpg`, `cover.png`, `folder.jpg`, or `${trackName}.jpg` in the directory, decode and persist thumbnail via `CoverArtStorage`.
- [x] Add unit tests verifying 512KB adaptive Range fetching, large ID3 tag extraction, and folder artwork fallback resolution.
- [x] Ensure all existing tests in `TrackMetadataRepositoryTest` pass.
