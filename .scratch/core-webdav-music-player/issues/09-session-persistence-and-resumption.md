# 09: Cold-Start Session State Persistence and Playback Resumption

**What to build:** When the user closes or dismisses the app, the current playback session state (active server ID, current directory path, active playback queue, current track index, playback mode, and playback millisecond position) is persisted in Jetpack DataStore. Upon reopening the app, the mini-player is pre-populated with the previous track and position, allowing the user to resume listening with a single tap.

**Blocked by:** 04: Full Player View, Seeking, and Queue Controls

**Status:** ready-for-agent

- [ ] Jetpack DataStore schema stores `PlaybackSessionState` (activeServerId, currentDirectoryPath, queueTrackList, currentTrackIndex, positionMs, playbackMode).
- [ ] Playback session periodically and on app pause/destroy flushes current state to DataStore.
- [ ] App cold start reads saved state, initializes `MusicPlayerAppSession`, and renders the mini-player in paused state at the saved position.
- [ ] Tapping play on the restored mini-player seamlessly streams from the saved millisecond offset.
- [ ] If the remote server or file is no longer accessible on cold start, the app handles the error gracefully without crashing.
- [ ] Tests verify state serialization, deserialization, and cold-start restoration logic through simulated process restarts.
