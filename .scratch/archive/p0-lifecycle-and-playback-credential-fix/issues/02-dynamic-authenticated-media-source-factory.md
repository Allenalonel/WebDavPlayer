# 02: Dynamic Authenticated Media Source Factory

**What to build:** Provision the playback engine's global `MediaSource.Factory` with a server-aware delegating data source factory tied to `WebDavMediaSourceAdapter`, ensuring every media item converted by ExoPlayer inherits WebDAV Basic Authentication, timeouts, and self-signed SSL trust.

**Blocked by:** None

**Status:** completed

- [x] `Media3AudioPlayerEngine` tracks the current active server instance in a thread-safe variable upon `playTracks()` invocation.
- [x] A delegating `DataSource.Factory` is provided to `DefaultMediaSourceFactory` during `ExoPlayer` initialization that delegates to `mediaSourceAdapter.getDataSourceFactory(server)` whenever an active server context is available.
- [x] Internal ExoPlayer timeline operations, automatic queue transitions, and media source rebuilds automatically inherit the active WebDAV credentials and SSL socket factory without emitting naked HTTP requests.
- [x] Unit tests in `Media3AudioPlayerEngineTest` and `WebDavStreamingPlaybackTest` verify that media items generated or rebuilt by the engine retain valid credentials and do not fail with HTTP 401 when tested against authenticated endpoints.

## Comments

- Implemented `activeServerRef` (`AtomicReference<WebDavServer?>`) in `Media3AudioPlayerEngine` and wired it into `delegatingDataSourceFactory`.
- ExoPlayer's `DefaultMediaSourceFactory` is injected with `delegatingDataSourceFactory`, ensuring dynamic resolution of WebDAV credentials, custom timeouts, and custom SSL socket configurations.
- Validated with unit tests in `Media3AudioPlayerEngineTest` (`delegatingDataSourceFactory_whenActiveServerSet_delegatesToMediaSourceAdapter`, `updateTrack_replacesMediaItem_andRetainsActiveServerContext`) and `WebDavStreamingPlaybackTest` (`engineMediaSourceRebuild_inheritsCredentials_inExoPlayer`, `authenticatedStreaming_delegatingDataSourceFactory_retainsCredentialsOnUpdateTrack`): all passed cleanly.

