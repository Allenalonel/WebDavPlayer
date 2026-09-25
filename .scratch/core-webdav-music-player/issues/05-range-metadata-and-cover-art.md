# 05: Asynchronous HTTP Range Metadata and Cover Art Resolution

**What to build:** When a directory is browsed or tracks are queued, the app asynchronously extracts rich metadata (title, artist, album, duration) and embedded cover artwork using partial HTTP Range requests in the background. File listings, mini-player, and the full player screen update progressively as metadata arrives. Extracted tags and cover thumbnails are saved in a local Room database so previously seen songs load rich tags instantly.

**Blocked by:** 04: Full Player View, Seeking, and Queue Controls

**Status:** ready-for-agent

- [ ] Background worker issues bounded concurrent HTTP `Range: bytes=0-131071` requests to probe ID3v2, FLAC Vorbis comments, and ASF headers.
- [ ] Metadata parser extracts song title, artist, album name, track number, and duration from byte slices.
- [ ] Embedded artwork byte arrays are decoded into scaled thumbnails and cached in local app storage/Room.
- [ ] Directory browser and player screens reactively update from transient file names to rich track metadata as parsing completes.
- [ ] Failed or malformed tag reads gracefully fall back to clean file-name display without crashing or blocking playback.
- [ ] `TrackMetadataEntity` stores resolved tags and cover thumbnail references in Room, keyed by `(serverId, remotePath)`.
- [ ] Tests verify tag extraction from sample MP3, FLAC, and WAV header byte streams, and assert non-blocking incremental UI updates.
