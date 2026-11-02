# 03: Foreground Service Elevation and Recovery

**What to build:** Resilient foreground service elevation adhering to modern Android 14 (API 34) platform execution limits, preventing background termination by the OS Low Memory Killer (LMK). Playback commands received via media buttons, lock-screen controls, and notification intents trigger foreground elevation synchronously on the main thread within the system's temporary broadcast exemption window. If background start restrictions prevent immediate service elevation, the player maintains a recoverable state and automatically reconciles foreground status as soon as the application regains window focus.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `WebDavMediaService.onStartCommand` invokes foreground service elevation synchronously upon receiving playback intents within the broadcast exemption window
- [ ] `PlaybackSessionHost.elevateForeground` gracefully handles platform background execution exceptions without corrupting session playback state
- [ ] Service prewarming and activity `onStart` reconcile pending foreground elevation if previous attempts were deferred
- [ ] Unit tests in `PlaybackSessionHostTest` and `WebDavMediaServiceTest` verify synchronous elevation during playback intent dispatch and resilient handling of background start exceptions
