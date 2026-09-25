# 05: Asynchronous HTTP Range Metadata and Cover Art Resolution

**What to build:** When a directory is browsed or tracks are queued, the app asynchronously extracts rich metadata (title, artist, album, duration) and embedded cover artwork using partial HTTP Range requests in the background. File listings, mini-player, and the full player screen update progressively as metadata arrives. Extracted tags and cover thumbnails are saved in a local Room database so previously seen songs load rich tags instantly.

**Blocked by:** 04: Full Player View, Seeking, and Queue Controls

**Status:** resolved

- [x] Background worker issues bounded concurrent HTTP `Range: bytes=0-131071` requests to probe ID3v2, FLAC Vorbis comments, and ASF headers.
- [x] Metadata parser extracts song title, artist, album name, track number, and duration from byte slices.
- [x] Embedded artwork byte arrays are decoded into scaled thumbnails and cached in local app storage/Room.
- [x] Directory browser and player screens reactively update from transient file names to rich track metadata as parsing completes.
- [x] Failed or malformed tag reads gracefully fall back to clean file-name display without crashing or blocking playback.
- [x] `TrackMetadataEntity` stores resolved tags and cover thumbnail references in Room, keyed by `(serverId, remotePath)`.
- [x] Tests verify tag extraction from sample MP3, FLAC, and WAV header byte streams, and assert non-blocking incremental UI updates.

## Comments

### Implementation Summary
1. **Domain & Local Storage**:
   - Added `TrackMetadata` domain model with display fallback helpers.
   - Enriched `AudioTrack` with `coverThumbnailPath` and `withMetadata(metadata)` enrichment logic.
   - Created `TrackMetadataEntity` and `TrackMetadataDao` in Room with composite primary key `(serverId, remotePath)`.
   - Updated `AppDatabase` to schema version 2 with `TrackMetadataDao`.
   - Created `CoverArtStorage` (`CoverArtStorageImpl`) to decode, downscale (max 512px), and persist embedded artwork into local app storage.
2. **Binary Parsing (Zero Native Dependency, Safe Byte Reader)**:
   - Created `ByteSliceReader` with bounds checks and safe string/number/synchsafe parsing.
   - Implemented `Id3v2Parser` supporting ID3v2.2, ID3v2.3, ID3v2.4 text frames (TIT2, TPE1, TALB, TRCK, TLEN) and APIC/PIC cover artwork extraction.
   - Implemented `FlacParser` supporting STREAMINFO duration calculations, VORBIS_COMMENT tags (TITLE, ARTIST, ALBUM, TRACKNUMBER), and PICTURE blocks.
   - Implemented `WavParser` supporting RIFF chunks, audio fmt duration calculation, LIST INFO chunks (INAM, IART, IPRD, ITRK), and embedded ID3v2 chunk parsing.
   - Implemented `AsfParser` supporting ASF headers, File Properties duration (100ns units), and Content Description / Extended Content Description metadata.
   - Unified `AudioMetadataParser` providing format-hinted and automatic fallback detection with graceful handling of malformed or truncated byte streams.
3. **HTTP Range & Background Resolution**:
   - Added `fetchRange` to `WebDavClient` and `OkHttpWebDavClient` for partial `Range: bytes=0-131071` requests.
   - Implemented `TrackMetadataRepository` / `TrackMetadataRepositoryImpl` orchestrating Room caching, Semaphore-bounded concurrency (3 parallel requests), background tag extraction, thumbnail persistence, and graceful fallback to clean file names.
4. **UI & Session Integration**:
   - Updated `DirectoryBrowserViewModel` and `DirectoryBrowserUiState` to trigger background resolution when directory files are loaded and reactively observe Room metadata flows.
   - Created `CoverThumbnailImage` composable for cached bitmap rendering with fallback placeholder.
   - Updated `DirectoryBrowserScreen` (`FileItemRow`) to render resolved titles, artists, durations, and cover art thumbnails.
   - Updated `MiniPlayer`, `FullPlayerView`, and `PlaybackQueueBottomSheet` to display decoded cover thumbnails and rich metadata.
   - Updated `MusicPlayerAppSessionImpl` to observe metadata updates and reactively enrich queue tracks.
5. **Testing**:
   - `TrackMetadataDaoTest`: verified Room CRUD and reactive Flow queries.
   - `AudioMetadataParserTest`: verified MP3 ID3v2, FLAC Vorbis Comments + PICTURE, WAV RIFF INFO + duration, ASF/WMA headers, and corrupted/truncated streams.
   - `TrackMetadataRepositoryTest`: verified Range fetching, Room caching, skipping previously resolved tracks, fallback handling, and reactive updates.
   - Extended `DirectoryBrowserViewModelTest` and `MusicPlayerAppSessionTest` verifying incremental, non-blocking UI and session updates.
