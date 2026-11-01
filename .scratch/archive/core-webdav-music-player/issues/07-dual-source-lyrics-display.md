# 07: Dual-Source Lyrics Display

**What to build:** In the full-screen player, the user can toggle to a synchronized lyrics view. The app automatically searches for a matching `.lrc` file (e.g., `song.lrc` alongside `song.flac`) in the current remote directory. If no `.lrc` file exists, it falls back to extracting embedded lyric tags (ID3 USLT or Vorbis comment) from the audio file. Synced lyrics automatically scroll and highlight the active line matching the current playback timestamp.

**Blocked by:** 05: Asynchronous HTTP Range Metadata and Cover Art Resolution

**Status:** completed

- [x] Engine probes remote directory for `${baseName}.lrc` via WebDAV HEAD/GET request upon loading a track.
- [x] If remote `.lrc` is not found (404), the engine inspects embedded metadata for unsynchronized or synchronized lyric frames.
- [x] LRC parser decodes timestamps (`[mm:ss.xx]` and `[mm:ss.xxx]`) and lyric text into a structured timeline.
- [x] Compose lyrics view displays vertical scrolling lyrics with active line highlighting and smooth auto-scroll locked to playback position.
- [x] Tapping a lyric line seeks playback directly to that timestamp.
- [x] Empty state message ("No lyrics available") displays cleanly when neither source provides lyric data.
- [x] Tests verify dual-source resolution priority, LRC timestamp parsing edge cases, and active line calculation at given playback offsets.
