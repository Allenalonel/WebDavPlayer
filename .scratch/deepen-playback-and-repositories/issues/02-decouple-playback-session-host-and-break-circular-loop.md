# 02: Decouple PlaybackSessionHost and Break Circular Loop

**What to build:**
Remove `class PlaybackSessionHost(...) : AudioPlayerEngine by playerEngine` fake delegation. `PlaybackSessionHost` is a dedicated Android platform adapter for foreground service elevation, MediaSession, and AudioFocus. It must not pose as an `AudioPlayerEngine`.
Directly inject `Media3AudioPlayerEngine` into `MusicPlayerAppSessionImpl`.
Eliminate `(playerEngine as? PlaybackSessionHost)?.bindAppSession(...)` from `WebDavApplication` by having `PlaybackSessionHost` explicitly bind to `MusicPlayerAppSession` in a clean single-direction relationship (host observes and dispatches to session; session operates on pure `AudioPlayerEngine`).

**Blocked by:** 01

**Status:** open

- [ ] Remove `AudioPlayerEngine by playerEngine` from `PlaybackSessionHost`
- [ ] Remove empty stubs `seekToNext`, `seekToNextMediaItem`, `seekToPrevious`, `seekToPreviousMediaItem` from ForwardingPlayer where applicable, routing via session
- [ ] Update `WebDavApplication` to instantiate `playerEngine = Media3AudioPlayerEngine(...)` directly, inject it into `MusicPlayerAppSessionImpl`, and bind session to `PlaybackSessionHost` cleanly without `as?` downcasts
- [ ] Update `PlaybackSessionHostTest` and integration tests to reflect decoupled lifecycle
- [ ] Ensure all tests pass cleanly
