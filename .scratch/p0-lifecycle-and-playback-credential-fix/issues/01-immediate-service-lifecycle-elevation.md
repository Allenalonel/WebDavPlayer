# 01: Immediate Service Lifecycle Elevation

**What to build:** Synchronous, first-frame foreground elevation inside `WebDavMediaService.onCreate()` using the session host's notification provider to completely satisfy the Android 5-second `startForegroundService` operating system contract in zero milliseconds.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `WebDavMediaService.onCreate()` immediately and synchronously invokes `startForeground()` with a valid notification on its very first lifecycle frame, eliminating the race condition with remote WebDAV network latency.
- [ ] Safe compatibility check for Android 10+ (`ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`) with graceful fallback for earlier Android versions and test environments.
- [ ] `PlaybackSessionHost.attachService()` establishes `isForegroundActive = true` immediately upon service binding, harmonizing state tracking across the lifecycle seam.
- [ ] State transitions in `handlePlaybackStateChanged()` safely update notification presentation and ongoing status without prematurely triggering `demoteForeground()` during initial preparation or buffering.
- [ ] Existing `WebDavMediaServiceTest` and `PlaybackSessionHostTest` pass cleanly with no unhandled lifecycle exceptions or timeout errors.
