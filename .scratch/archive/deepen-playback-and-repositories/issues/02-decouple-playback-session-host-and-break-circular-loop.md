# 02: Decouple PlaybackSessionHost and Break Circular Loop

**What to build:**
Remove `class PlaybackSessionHost(...) : AudioPlayerEngine by playerEngine` fake delegation. `PlaybackSessionHost` is a dedicated Android platform adapter for foreground service elevation, MediaSession, and AudioFocus. It must not pose as an `AudioPlayerEngine`.
Directly inject `Media3AudioPlayerEngine` into `MusicPlayerAppSessionImpl`.
Eliminate `(playerEngine as? PlaybackSessionHost)?.bindAppSession(...)` from `WebDavApplication` by having `PlaybackSessionHost` explicitly bind to `MusicPlayerAppSession` in a clean single-direction relationship (host observes and dispatches to session; session operates on pure `AudioPlayerEngine`).

**Blocked by:** 01

**Status:** resolved

- [x] Remove `AudioPlayerEngine by playerEngine` from `PlaybackSessionHost`
- [x] Remove empty stubs `seekToNext`, `seekToNextMediaItem`, `seekToPrevious`, `seekToPreviousMediaItem` from ForwardingPlayer where applicable, routing via session
- [x] Update `WebDavApplication` to instantiate `playerEngine = Media3AudioPlayerEngine(...)` directly, inject it into `MusicPlayerAppSessionImpl`, and bind session to `PlaybackSessionHost` cleanly without `as?` downcasts
- [x] Update `PlaybackSessionHostTest` and integration tests to reflect decoupled lifecycle
- [x] Ensure all tests pass cleanly

## Answer
Decoupled `PlaybackSessionHost` from `AudioPlayerEngine`:
1. Removed `AudioPlayerEngine by playerEngine` from `PlaybackSessionHost`. It now acts exclusively as an Android system adapter for foreground service elevation, MediaSession, and AudioFocus.
2. Routed `ForwardingPlayer`'s `seekToNext`, `seekToNextMediaItem`, `seekToPrevious`, `seekToPreviousMediaItem` directly to `appSession.skipToNext()` and `appSession.skipToPrevious()`, eliminating empty stubs.
3. Updated `WebDavApplication` to instantiate `playerEngine = Media3AudioPlayerEngine(...)` directly and inject it into `MusicPlayerAppSessionImpl`. `sessionHost` binds `musicPlayerAppSession` in a clean unidirectional relationship, eliminating all `as? PlaybackSessionHost` downcasts.
4. Updated `PlaybackSessionHostTest`, `PackagingSeamVerificationTest`, `EndToEndPlaybackPipelineIntegrationTest`, and `EndToEndSystemFocusAndRouteRegressionTest`. All tests pass cleanly and modified files adhere to the 600-line budget.
