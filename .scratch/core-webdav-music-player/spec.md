Status: ready-for-agent

# Feature Specification: Core WebDAV Music Player for Android

## Problem Statement

Users who store their personal music collections on private WebDAV storage (such as home NAS, Synology, AList, Nextcloud, or private cloud drives) currently lack a modern, lightweight, and dedicated Android audio player. 

Existing solutions either treat WebDAV as an afterthought in bloated general-purpose video players, fail to integrate cleanly with modern Android media sessions (resulting in lost background playback and missing lockscreen controls), require downloading entire music libraries to local storage (exhausting phone capacity), fail to decode legacy formats such as WMA, or reject self-signed SSL certificates commonly found in home networks. Users need an elegant, streaming-native music player that connects directly to their WebDAV servers, starts playing immediately from directory trees, and respects Android platform audio standards.

## Solution

A native Android application (Android 10.0+ / API 29, Kotlin, Jetpack Compose, Material 3) dedicated to streaming audio from WebDAV servers over HTTP/HTTPS.

The application enables users to manage multiple WebDAV server profiles (with configurable basic/digest auth and optional self-signed SSL trust). Users browse remote directories with instant file-name rendering while an asynchronous engine extracts track metadata, cover art, and lyrics via HTTP Range requests without persistent audio disk caching. Audio playback is powered by AndroidX Media3 (ExoPlayer) with an integrated FFmpeg software decoding extension, enabling seamless playback across MP3, FLAC, WAV, WMA, and other formats. The app maintains a background `MediaSessionService` with lockscreen controls, notification actions, Bluetooth/headset integration, audio focus handling, and persistent session restoration on restart.

---

## User Stories

### Server Management

1. As a music listener, I want to add a WebDAV server with its display name, URL (HTTP or HTTPS), port, path prefix, username, and password, so that the app can connect to my private storage.
2. As a user with self-hosted home services, I want an option to trust self-signed or invalid SSL certificates per server, so that I can connect to my home NAS over HTTPS without installing custom root CA certificates on Android.
3. As a music collector with multiple storage backends (e.g., home NAS and cloud AList), I want to store multiple WebDAV server configurations and easily switch the active server, so that I can access different libraries.
4. As a user, I want to edit existing server details and test the connection with immediate visual feedback, so that I can fix typos or updated credentials.
5. As a user, I want to delete a server configuration along with its associated cached metadata, so that obsolete server data is cleanly removed.

### Directory Navigation & Library Browsing

6. As a user, I want to browse remote directories in a hierarchical file-tree view, so that I can explore my music organized in folder structures.
7. As a user, I want directory listings to render immediately using basic file names from the initial directory fetch, so that I don't experience blocking delays while waiting for deep metadata.
8. As a user, I want to see breadcrumbs or a clear back navigation path, so that I can easily navigate up to parent directories.
9. As a user, I want non-audio files (except recognized `.lrc` lyric files) to be filtered out or clearly distinguished from audio tracks, so that my browsing view remains uncluttered.
10. As a user, I want to pull-to-refresh any directory, so that newly added or modified remote files are promptly updated.

### Metadata & Cover Art Resolution

11. As a listener, I want the app to asynchronously extract track metadata (title, artist, album, track number, duration) in the background via HTTP Range requests, so that my songs show proper tags instead of raw file names.
12. As a listener, I want embedded cover artwork to be extracted and cached locally in a lightweight image cache, so that album art displays on the player interface and notification bar without re-fetching on every view.
13. As a user, I want metadata resolution to be non-blocking and failure-tolerant, so that songs with corrupted tags or unsupported header formats gracefully fall back to clean file-name display.
14. As a user, I want resolved metadata to be stored in a local lightweight database, so that revisiting previously browsed directories displays rich tags instantly.

### Playback & Queue Management

15. As a listener, I want clicking any audio track in a directory to immediately begin playback and automatically populate the playback queue with all audio tracks in that directory, so that I can listen through an entire album or folder without manual queuing.
16. As a listener, I want to see a persistent mini-player at the bottom of browsing screens, so that I can control playback (play/pause, next track) while continuing to browse other folders.
17. As a listener, I want to tap the mini-player to open a full-screen player view showing large album artwork, track title, artist, seek bar, time elapsed, total duration, and playback controls.
18. As a listener, I want to drag the seek bar to jump to any position in the track, so that the audio stream seeks cleanly and resumes playback from that position.
19. As a listener, I want to cycle playback modes between List Loop, Single Loop, and Shuffle, so that I can control the playback order according to my preference.
20. As a listener, I want to open the current playback queue from the player view, see upcoming tracks, jump directly to any track in the queue, or remove individual tracks from the queue.

### Audio Decoding & Streaming

21. As a listener, I want to stream and play MP3, FLAC, WAV, and AAC files smoothly over HTTP/HTTPS, so that my standard music collection plays reliably.
22. As a listener with legacy audio collections, I want the player to decode and play WMA files via an integrated FFmpeg audio extension, so that my WMA tracks play seamlessly alongside other formats.
23. As a mobile user, I want the player to stream audio using an in-memory/transient buffer without writing audio tracks to local disk storage, so that the app does not consume my device's internal storage space.
24. As a mobile user on unstable networks, I want visual buffering indicators and automatic retry logic upon brief network drops, so that playback resumes smoothly once connection is restored.

### System Integration & Background Playback

25. As a mobile user, I want music to continue playing when the app is in the background or the screen is locked, so that I can listen to music while using other apps or on the go.
26. As a listener, I want a system notification using Android MediaStyle showing album art, track details, play/pause, seek progress, and skip buttons, so that I can control music from the notification shade.
27. As a listener, I want lockscreen controls displaying the current track artwork and playback controls, so that I can manage playback without unlocking the device.
28. As a listener, I want hardware media buttons (Bluetooth headphones, car audio, wired headsets) to control play, pause, next, and previous actions.
29. As a user receiving a phone call or navigation prompt, I want playback to pause for calls (and resume after call termination) and temporarily duck (lower volume) during short notifications.

### Lyrics Display

30. As a listener, I want the full-screen player to display synchronized, scrolling lyrics highlighting the active line corresponding to the current playback position.
31. As a listener, I want the app to automatically look for a matching `.lrc` file with the same base name in the same remote directory, so that standard external lyrics are loaded seamlessly.
32. As a listener whose files have embedded lyrics, I want the app to fall back to extracting embedded lyric tags (ID3 USLT or Vorbis comments) when no external `.lrc` file exists.
33. As a listener, I want a clean empty-state message when no lyrics are available, without disrupting playback or album art display.

### State Persistence & Cold-Start Restoration

34. As a user, I want the app to remember the active server, the current playback queue, the active track index, and the exact playback millisecond position when I close or restart the app.
35. As a user reopening the app, I want to see my last playback session restored in the mini-player with a single tap to resume playing from where I left off.

---

## Implementation Decisions

### 1. Architectural Shape & Clean Seam

- **Pattern**: Unidirectional Data Flow (UDF) with MVVM / MVI architecture using Kotlin Coroutines and `StateFlow`.
- **Single Highest Test Seam (`MusicPlayerAppSession`)**:
  - The UI layer (Compose screens) interacts exclusively with a unified `MusicPlayerAppSession` facade.
  - The facade coordinates remote WebDAV interactions, playback queue states, player engine commands, and local session persistence.
  - Tests verify user-observable state emissions (`StateFlow<PlayerSessionState>`) in response to user actions, using test doubles for the network transport and audio sink.

### 2. Remote Storage & WebDAV Adapter

- **Library / Transport**: OkHttp client configured with an XML pull/DOM parser for WebDAV `PROPFIND` multi-status responses.
- **Protocol Operations**:
  - `PROPFIND` (depth 1) for fetching directory listings (name, size, content type, last modified date).
  - HTTP `GET` with `Range` header for partial file fetches (fetching byte slices for ID3/Vorbis/WMA header parsing).
  - HTTP `GET` (streamed) feeding into Media3 data sources for audio playback.
  - HTTP `HEAD` / `GET` for remote `.lrc` lyric file discovery.
- **Authentication**: OkHttp `Authenticator` supporting HTTP Basic Auth and Digest Auth.
- **SSL / TLS**: Per-server toggle allowing an insecure `X509TrustManager` and permissive `HostnameVerifier` for self-signed certificates (respecting ADR-0004).

### 3. Audio Engine & Media3 Integration

- **Framework**: AndroidX Media3 (`androidx.media3:media3-session`, `androidx.media3:media3-exoplayer`).
- **Service Architecture**: `MediaSessionService` subclass (`WebDavMediaService`) maintaining the foreground service lifecycle and `MediaSession`.
- **Codec Extension**: Integrated FFmpeg software audio decoder (`media3-decoder-ffmpeg`) compiled for `arm64-v8a` and `armeabi-v7a`, registered into `DefaultRenderersFactory` with `EXTENSION_RENDERER_MODE_ON` (respecting ADR-0001).
- **Streaming DataSource**: Custom `HttpDataSource.Factory` utilizing the active WebDAV server's authenticated OkHttp client.
- **Buffering & Disk Policy**: Transient in-memory ring buffer for streaming; no persistent disk audio caching (respecting ADR-0002).
- **Audio Focus**: Managed via Media3 `AudioAttributes` (`USAGE_MEDIA`, `CONTENT_TYPE_MUSIC`) with automatic ducking and transient loss handling.

### 4. Metadata & Lyrics Engine

- **Incremental Range Fetching**:
  - When a directory is listed, files are immediately emitted with status `Transient` (file name, size).
  - Background workers execute bounded concurrent HTTP Range requests:
    - First 128 KB for ID3v2, FLAC headers, and WMA ASF headers.
    - Last 128 KB for ID3v1 or trailing tags if needed.
  - Parsed metadata and extracted album art thumbnails are cached in the local Room database (respecting ADR-0003).
- **Dual-Source Lyrics**:
  - Step 1: Probe remote directory for `${baseName}.lrc`. If found (HTTP 200), stream and parse time-stamped lines (`[mm:ss.xx]`).
  - Step 2: If `.lrc` does not exist (HTTP 404), inspect embedded tags (`USLT` frame in ID3v2, `LYRICS` in Vorbis, or ASF lyrics in WMA).
  - Step 3: Emit parsed `LyricsTimeline` or empty state (respecting ADR-0006).

### 5. Local Data Storage

- **Room Database**:
  - `WebDavServerEntity`: id, name, url, port, pathPrefix, username, password, allowSelfSigned, isDefault, createdAt.
  - `TrackMetadataEntity`: serverId, remotePath, title, artist, album, durationMs, coverThumbnailBlob, lastModified, resolvedAt.
- **Jetpack DataStore**:
  - Stores `PlaybackSessionState`: activeServerId, currentDirectoryPath, serializedQueue, currentTrackIndex, positionMs, playbackMode.

### 6. UI Layer (Jetpack Compose & Material 3)

- **Min SDK**: API 29 (Android 10.0), Target SDK: API 34 (Android 14) (respecting ADR-0005).
- **Design System**: Material 3 with dynamic color support (Material You).
- **Key Screens**:
  - `ServerListScreen` & `ServerEditDialog`: Manage WebDAV endpoints with SSL and auth options.
  - `DirectoryBrowserScreen`: Breadcrumb bar, pull-to-refresh list, audio track icons, loading badges, and mini-player anchor.
  - `PlayerScreen` (expanded modal/screen): Album art carousel, synced scrolling lyrics tab, seek slider, playback mode toggle, queue inspector.
  - `QueueBottomSheet`: Reorderable, dismissible list of active queue tracks.

---

## Testing Decisions

### What Makes a Good Test

- Tests must exercise external user-observable behavior and contracts, not internal private functions or implementation details.
- Tests verify state transitions emitted by the `MusicPlayerAppSession` facade when fed with realistic user interactions.
- Network interactions are mocked via a local `MockWebServer` producing valid WebDAV XML responses, HTTP 401 challenges, and Range slices, ensuring fast, hermetic, and reproducible tests.

### Tested Modules & Scenarios

1. **WebDAV Client & Range Request Tests**:
   - Parsing valid and malformed WebDAV PROPFIND responses.
   - HTTP Basic Auth headers generation and 401 challenge retries.
   - Self-signed SSL flag toggling (verifying insecure trust manager is engaged only when explicitly enabled).
   - Partial Range request handling and error backoff.
2. **Metadata & Lyrics Resolver Tests**:
   - Parsing ID3v2 (MP3), Vorbis Comment (FLAC), and ASF (WMA) metadata from byte streams.
   - Dual-source lyric resolution: priority of `.lrc` over embedded tags, handling missing lyrics.
   - LRC timeline parser testing (standard time-tags, multi-line tags, malformed timestamps).
3. **Playback Queue & Flow Tests**:
   - Loading a directory into the queue and starting playback at the selected index.
   - List Loop, Single Loop, and Shuffle behavior upon track completion.
   - Mini-player state updates and seek position tracking.
4. **Session Persistence & Recovery Tests**:
   - Serializing playback session state to DataStore.
   - Deserializing and restoring active server, queue, and track position on simulated app cold start.

---

## Out of Scope

- Offline downloading or persistent disk caching of full audio files.
- Modifying, deleting, uploading, or renaming remote files on the WebDAV server (read-only operations only).
- Non-WebDAV cloud music protocols (e.g., Subsonic/OpenSubsonic, Jellyfin, Plex, SMB/CIFS).
- Audio equalizer, crossfade, replay gain, or DSP audio effects (reserved for post-v1 milestones).
- User playlist authoring that modifies server files (only in-memory playback queues are supported).

---

## Further Notes

- **FFmpeg Integration**: The FFmpeg software decoding extension requires compiling prebuilt `.so` shared libraries (`libavcodec`, `libavformat`, `libavutil`, `libswresample`) for Android ABIs (`arm64-v8a`, `armeabi-v7a`). These can be packaged in `app/src/main/jniLibs/` or integrated via an automated build module.
- **Android 13+ Permissions**: Ensure `POST_NOTIFICATIONS` runtime permission is requested for background playback controls.
