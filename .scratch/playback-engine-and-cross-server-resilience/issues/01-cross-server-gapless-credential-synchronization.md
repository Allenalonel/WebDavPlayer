# 01: Cross-Server Gapless Credential Synchronization

**What to build:** End-to-end credential synchronization when playing custom cross-server playlists. When playback naturally transitions from a track on Server A to a track on Server B (gapless progression), the application session state and audio player engine dynamically update their active server reference to Server B. Subsequent range reconnections during network jitter, manual user seeks, premature EOF validation, and subsequent track pre-roll automatically present Server B's credentials, completely preventing false HTTP 401 unauthorized errors and wrongful track inactivation.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Audio player engine domain interface and implementations provide `setActiveServer(server: WebDavServer)`
- [ ] Timeline coordinator observes engine track transitions and checks if the new track belongs to a different server
- [ ] Upon cross-server transition, timeline coordinator asynchronously resolves the new server and atomically updates `sessionStateFlow.activeServer` and `playerEngine.setActiveServer`
- [ ] Subsequent range reconnections and seeking actions use the correct server credentials
- [ ] Unit tests in `PlaybackTimelineCoordinatorTest` and `MusicPlayerAppSessionTest` verify cross-server natural track transitions atomically update session and engine active server contexts
