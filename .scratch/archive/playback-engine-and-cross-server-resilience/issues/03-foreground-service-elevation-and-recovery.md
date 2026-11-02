# 03: Foreground Service Elevation and Recovery

**What to build:** Resilient foreground service elevation adhering to modern Android 14 (API 34) platform execution limits, preventing background termination by the OS Low Memory Killer (LMK). Playback commands received via media buttons, lock-screen controls, and notification intents trigger foreground elevation synchronously on the main thread within the system's temporary broadcast exemption window. If background start restrictions prevent immediate service elevation, the player maintains a recoverable state and automatically reconciles foreground status as soon as the application regains window focus.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] `WebDavMediaService.onStartCommand` invokes foreground service elevation synchronously upon receiving playback intents within the broadcast exemption window
- [x] `PlaybackSessionHost.elevateForeground` gracefully handles platform background execution exceptions without corrupting session playback state
- [x] Service prewarming and activity `onStart` reconcile pending foreground elevation if previous attempts were deferred
- [x] Unit tests in `PlaybackSessionHostTest` and `WebDavMediaServiceTest` verify synchronous elevation during playback intent dispatch and resilient handling of background start exceptions

## Answer
1. 在 `WebDavMediaService.onStartCommand` 中，当接收到播放意图（`ACTION_PLAY`, `ACTION_NEXT`, `ACTION_PREVIOUS`, `ACTION_MEDIA_BUTTON`）时，立即在广播系统临时豁免窗口内同步执行 `startForegroundSafely` 并将 Service 绑定至 `PlaybackSessionHost`，先提权再分发播放调用。
2. 在 `PlaybackSessionHost.elevateForeground` 中完善异常兜底逻辑，捕获平台后台限制异常并维护 `isElevationPendingRef` 状态标记，不破坏引擎正在播放或缓冲的会话状态。
3. 在 `PlaybackSessionHost.prewarmService()` 中增加前台状态对齐与重对齐检查，当应用窗口恢复（`MainActivity.onStart()`）时，若存在挂起的前台提权或当前正在活动播放，立即重新触发 `elevateForeground` 恢复前台服务保障。
4. 在 `WebDavMediaServiceTest` 与 `PlaybackSessionHostTest` 中补充同步提权分发、无服务兜底暂存、`prewarmService` 状态对齐等单元测试，全量验证通过。
