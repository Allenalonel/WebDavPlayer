# 03: Basic Audio Streaming and Mini-Player

**What to build:** Clicking any audio track in a directory immediately starts streaming playback over HTTP/HTTPS. The player automatically populates the in-memory Playback Queue with all audio tracks from the current directory, beginning playback at the clicked track. A persistent mini-player appears at the bottom of browsing screens displaying the current track title, a play/pause toggle button, and basic buffering/loading indicators. Audio streams in-memory without creating persistent disk cache files.

**Blocked by:** 02: Remote Directory Browsing and Navigation

**Status:** ready-for-agent

- [ ] AndroidX Media3 (ExoPlayer) is integrated with an authenticated OkHttp `HttpDataSource.Factory` pointing to the active WebDAV server.
- [ ] Audio streaming utilizes an in-memory/transient buffer without writing audio tracks to local disk cache (per ADR-0002).
- [ ] Tapping an audio track in a directory populates the `PlaybackQueue` with all audio tracks in that folder and begins playing the selected track.
- [ ] Persistent mini-player component is visible at the bottom of browsing screens whenever a track is loaded.
- [ ] Mini-player displays track title (initial file name fallback), current playback state (playing, paused, buffering), and a responsive play/pause toggle.
- [ ] Native audio formats (MP3, FLAC, WAV, AAC) stream and play reliably over both HTTP and HTTPS connections.
- [ ] Tests verify queue population from directory contents, playback command dispatch, and mini-player state updates via the application facade.
