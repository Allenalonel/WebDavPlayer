# 03: Basic Audio Streaming and Mini-Player

**What to build:** Clicking any audio track in a directory immediately starts streaming playback over HTTP/HTTPS. The player automatically populates the in-memory Playback Queue with all audio tracks from the current directory, beginning playback at the clicked track. A persistent mini-player appears at the bottom of browsing screens displaying the current track title, a play/pause toggle button, and basic buffering/loading indicators. Audio streams in-memory without creating persistent disk cache files.

**Blocked by:** 02: Remote Directory Browsing and Navigation

**Status:** resolved

- [x] AndroidX Media3 (ExoPlayer) is integrated with an authenticated OkHttp `HttpDataSource.Factory` pointing to the active WebDAV server.
- [x] Audio streaming utilizes an in-memory/transient buffer without writing audio tracks to local disk cache (per ADR-0002).
- [x] Tapping an audio track in a directory populates the `PlaybackQueue` with all audio tracks in that folder and begins playing the selected track.
- [x] Persistent mini-player component is visible at the bottom of browsing screens whenever a track is loaded.
- [x] Mini-player displays track title (initial file name fallback), current playback state (playing, paused, buffering), and a responsive play/pause toggle.
- [x] Native audio formats (MP3, FLAC, WAV, AAC) stream and play reliably over both HTTP and HTTPS connections.
- [x] Tests verify queue population from directory contents, playback command dispatch, and mini-player state updates via the application facade.

## Comments

### Implementation Summary
1. **Domain Layer**:
   - Added `AudioTrack`, `PlaybackQueue`, `PlaybackState`, and `PlayerSessionState` reflecting CONTEXT.md domain language.
   - Defined `AudioPlayerEngine` abstraction allowing clean test separation between business logic and media decoding.
   - Defined `MusicPlayerAppSession` facade coordinating WebDAV servers, directory queues, and player engine commands.
2. **Media3 Integration & Data Layer**:
   - Integrated AndroidX Media3 (`media3-exoplayer`, `media3-session`, `media3-datasource-okhttp`).
   - Implemented `WebDavDataSourceFactory` binding active server authentication and SSL trust configuration to OkHttp DataSource.
   - Built `Media3AudioPlayerEngine` with `DefaultLoadControl` enforcing purely in-memory transient buffering (ADR-0002).
   - Added file URL resolution and percent-encoding in `WebDavServer.resolveFileUrl`.
3. **UI Layer**:
   - Created Material 3 `MiniPlayer` displaying fallback file names, buffering spinners, playing/paused status, and play/pause toggle button.
   - Anchored `MiniPlayer` persistently into `DirectoryBrowserScreen` Scaffold `bottomBar` whenever a track is loaded.
   - Wired `DirectoryBrowserViewModel.onAudioTrackClicked` to populate `PlaybackQueue` with all directory audio tracks and play immediately.
   - Created `WebDavApplication` to provide persistent application-scoped session management.
4. **Testing**:
   - Added `MusicPlayerAppSessionTest` covering queue population, play/pause toggling, error propagation, and server switching.
   - Added `DirectoryBrowserViewModelTest` testing track clicks and session dispatch.
   - Added `WebDavServerUrlResolutionTest` testing URL encoding across HTTP/HTTPS and special characters.
   - Added `WebDavDataSourceFactoryTest` and `Media3AudioPlayerEngineTest` verifying ExoPlayer integration.
