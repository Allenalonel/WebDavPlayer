# 02: Remote Directory Browsing and Navigation

**What to build:** When an active WebDAV server is connected, the user can browse remote directory trees in a clean list view. Users can click directories to navigate deeper, use a breadcrumb bar or back button to navigate up, see visual distinctions between folders and audio files, and pull down to refresh the current directory listing. Audio files are rendered immediately with their basic file names.

**Blocked by:** 01: Project Scaffold and WebDAV Server Management

**Status:** resolved

- [x] WebDAV client issues `PROPFIND` (depth 1) request and parses multi-status XML responses into `RemoteDirectory` and `RemoteFile` domain entities.
- [x] Directory browser UI renders a list of items showing folder icons for directories and audio badges for recognized audio formats (MP3, FLAC, WAV, WMA, AAC).
- [x] Clicking a folder navigates into that remote directory, displaying loading states and error handling for empty or unreachable folders.
- [x] Breadcrumb navigation and Android system back gesture allow navigating back up to parent directories.
- [x] Pull-to-refresh invalidates any in-memory directory cache and re-queries the remote WebDAV endpoint.
- [x] Non-audio files (except recognized `.lrc` lyric files) are hidden or visually subdued.
- [x] Tests verify XML PROPFIND parsing, path resolution, and navigation state transitions using MockWebServer.

## Comments

### Implementation Summary
1. **Domain Layer**:
   - Added `RemoteDirectory`, `RemoteFile`, `RemoteFileType`, `AudioFormat`, `Breadcrumb`, and `ListDirectoryResult` following CONTEXT.md naming standards.
   - Identified recognized audio formats (MP3, FLAC, WAV, WMA, AAC, OGG, M4A) and `.lrc` lyric files.
2. **Data & Remote Layer**:
   - Extended `WebDavClient` with `listDirectory(server, path)`.
   - Built `WebDavXmlParser` supporting namespace-agnostic WebDAV XML parsing, URL decoding, path normalization, and child filtering.
   - Implemented `DirectoryRepository` with an in-memory cache and pull-to-refresh cache invalidation.
3. **UI Layer**:
   - Implemented `DirectoryBrowserViewModel` managing path navigation, breadcrumb hierarchy, and loading/empty/error states.
   - Built `DirectoryBrowserScreen` with Material 3, dynamic breadcrumb scroll bar, audio format chips/badges, subdued non-audio files, pull-to-refresh, empty and error state handling, and Android system back gesture interception (`BackHandler`).
   - Integrated screen transition between `ServerListScreen` and `DirectoryBrowserScreen` in `MainActivity`.
4. **Verification**:
   - Comprehensive unit and integration test coverage across XML parsing, MockWebServer PROPFIND handling, repository cache invalidation, and ViewModel navigation state transitions. All tests passing.
