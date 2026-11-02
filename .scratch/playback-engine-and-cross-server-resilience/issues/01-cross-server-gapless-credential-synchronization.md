# 01: Cross-Server Gapless Credential Synchronization

**What to build:** End-to-end credential synchronization when playing custom cross-server playlists. When playback naturally transitions from a track on Server A to a track on Server B (gapless progression), the application session state and audio player engine dynamically update their active server reference to Server B. Subsequent range reconnections during network jitter, manual user seeks, premature EOF validation, and subsequent track pre-roll automatically present Server B's credentials, completely preventing false HTTP 401 unauthorized errors and wrongful track inactivation.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] Audio player engine domain interface and implementations provide `setActiveServer(server: WebDavServer)`
- [x] Timeline coordinator observes engine track transitions and checks if the new track belongs to a different server
- [x] Upon cross-server transition, timeline coordinator asynchronously resolves the new server and atomically updates `sessionStateFlow.activeServer` and `playerEngine.setActiveServer`
- [x] Subsequent range reconnections and seeking actions use the correct server credentials
- [x] Unit tests in `PlaybackTimelineCoordinatorTest` and `MusicPlayerAppSessionTest` verify cross-server natural track transitions atomically update session and engine active server contexts

## Answer
1. 在 `AudioPlayerEngine` 中新增 `setActiveServer(server: WebDavServer)` 接口契约，并在 `Media3AudioPlayerEngine` 中通过应用 Looper 更新 `activeServerRef`，确保 `delegatingDataSourceFactory` 后续创建的数据源与 Range 断点续传自动继承目标服务器凭据。
2. 在 `PlaybackTimelineCoordinator.startTrackTransitionsObservation` 中，当收到引擎自然切歌事件时，比对下一曲目的 `serverId` 与当前激活服务器：若所属服务器变更，异步从 `ServerRepository` 解析最新服务器实体，并原子更新 `sessionStateFlow.activeServer` 与 `playerEngine.setActiveServer`。
3. 新增 `PlaybackTimelineCoordinatorTest` 与 `MusicPlayerAppSessionTest.seamlessPreroll_crossServerTrackTransition_atomicallyUpdatesSessionAndEngineActiveServer`，全面覆盖跨服务器自然过渡时会话与引擎凭据上下文的原子同步。
