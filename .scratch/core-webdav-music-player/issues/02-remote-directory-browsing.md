# 02: Remote Directory Browsing and Navigation

**What to build:** When an active WebDAV server is connected, the user can browse remote directory trees in a clean list view. Users can click directories to navigate deeper, use a breadcrumb bar or back button to navigate up, see visual distinctions between folders and audio files, and pull down to refresh the current directory listing. Audio files are rendered immediately with their basic file names.

**Blocked by:** 01: Project Scaffold and WebDAV Server Management

**Status:** ready-for-agent

- [ ] WebDAV client issues `PROPFIND` (depth 1) request and parses multi-status XML responses into `RemoteDirectory` and `RemoteFile` domain entities.
- [ ] Directory browser UI renders a list of items showing folder icons for directories and audio badges for recognized audio formats (MP3, FLAC, WAV, WMA, AAC).
- [ ] Clicking a folder navigates into that remote directory, displaying loading states and error handling for empty or unreachable folders.
- [ ] Breadcrumb navigation and Android system back gesture allow navigating back up to parent directories.
- [ ] Pull-to-refresh invalidates any in-memory directory cache and re-queries the remote WebDAV endpoint.
- [ ] Non-audio files (except recognized `.lrc` lyric files) are hidden or visually subdued.
- [ ] Tests verify XML PROPFIND parsing, path resolution, and navigation state transitions using MockWebServer.
