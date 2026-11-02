# Feature Map: Playback Engine Deepening, Cross-Server Credential Synchronization, and Foreground Service Resilience

## Summary
实现播放引擎内核深化、跨服务器凭据原子同步与前台服务生命周期硬化，彻底根除三大播放与系统稳定性瓶颈：
- **Ticket 01**: 跨服务器无缝（Gapless）切歌凭据原子同步。当播放跨服务器混合歌单自然切换至下一服务器曲目时，应用会话状态与播放引擎原子更新激活服务器引用，消除 Range 断点续传、进度条拖动与早抛 EOF 校验时的 HTTP 401 误报与曲目失效。
- **Ticket 02**: 流媒体数据源有界重试与工作线程快速释放。将 `ResilientWebDavDataSource` 无限重试改为确定性的 5 次指数退避上限（约 19 秒），并在数据源关闭时主动中断退避等待，避免 NAS 离线或弱网时 ExoPlayer 加载线程陷入阻塞死循环与切歌 UI 假死。
- **Ticket 03**: Android 14 (API 34) 规范前台服务同步提权与容错恢复。在媒体按键/通知栏意图广播分发窗口内同步提权前台服务，安全兜底平台后台启动限制异常，并在应用窗口恢复（`prewarmService` / `MainActivity.onStart`）时自动对齐前台状态，杜绝 LMK 后台误杀。
- **Ticket 04**: 全量端到端流水线回归与发布包二进制体积预算（APK <= 7.5 MB, classes.dex <= 4.0 MB, arm64-v8a 架构）质量门禁。

---

## Decisions
- **Engine Active Server Mutability**: 深化 `AudioPlayerEngine` 领域接口，增加 `setActiveServer(server: WebDavServer)` 契约。在 `Media3AudioPlayerEngine` 中通过应用 Looper 更新 `activeServerRef`，确保后续由 `delegatingDataSourceFactory` 创建的所有数据源及 Range 请求立即继承最新凭据上下文。
- **Timeline Coordinator Gapless Synchronization**: 在 `PlaybackTimelineCoordinator.startTrackTransitionsObservation` 中监听曲目自然过渡；比对下一曲目的 `serverId` 与当前激活服务器，异步从 `ServerRepository` 解析最新服务器实体，并原子更新 `sessionStateFlow.activeServer` 与 `playerEngine.setActiveServer`，确保早于预加载与进度更新。
- **Bounded Retry Threshold & Interruptible Sleep**: `ResilientWebDavDataSource` 采用 5 次重试退避策略（1s, 2s, 4s, 6s, 6s，总计 19s），与弱网看门狗协同防护；超过 5 次持久失败后停止重试并抛出原始 `IOException`；`close()` 时设置状态并打断等待线程，立即释放加载器。
- **Synchronous Foreground Elevation & Graceful Recovery**: 在 `WebDavMediaService.onStartCommand` 中，在广播临时豁免窗口内同步执行 `startForegroundSafely`；在 `PlaybackSessionHost` 中捕获 `ForegroundServiceStartNotAllowedException` 等后台限制异常，保持播放状态可恢复；并在 `prewarmService()` 时检查并对齐挂起的前台提权。

---

## Ticket Topology & Dependency Graph

```text
[01-cross-server-credential-sync] ──┐
[02-bounded-retry-thread-release] ──┼──► [04-end-to-end-pipeline-and-packaging-budget]
[03-foreground-elevation-recovery] ─┘
```

---

## Tickets Breakdown
1. `01-cross-server-gapless-credential-synchronization.md`: 跨服务器切歌凭据原子同步 (Status: resolved, Blocked by: None)
2. `02-bounded-retry-and-thread-release-in-data-source.md`: 流媒体数据源有界重试与线程释放 (Status: resolved, Blocked by: None)
3. `03-foreground-service-elevation-and-recovery.md`: 前台服务同步提权与容错恢复 (Status: resolved, Blocked by: None)
4. `04-end-to-end-pipeline-and-packaging-budget-gate.md`: 全量回归与二进制体积预算门禁 (Status: ready-for-agent, Blocked by: 01, 02, 03)

---

## Decisions-so-far
- **Task Graph Initialized**: 明确了四项工单的拓扑依赖与设计规范，建立集成分支 `feature/playback-engine-and-cross-server-resilience`。
- **Ticket 01 Resolved**: 在 `AudioPlayerEngine` 中扩展 `setActiveServer(server: WebDavServer)`，在 `Media3AudioPlayerEngine` 中通过应用 Looper 更新 `activeServerRef`；在 `PlaybackTimelineCoordinator` 中于曲目过渡时检测跨服务器变更并异步解析新服务器实体，原子同步 `sessionStateFlow.activeServer` 与 `playerEngine.setActiveServer`；完成 `PlaybackTimelineCoordinatorTest` 与 `MusicPlayerAppSessionTest` 验证。
- **Ticket 02 Resolved**: 将 `ResilientWebDavDataSource` 默认最大重试阈值收敛为 5 次（总退避 19 秒），并在重试耗尽后终止循环抛出底层异常；引入 `activeThread` 跟踪并在 `close()` 时打断休眠线程即时释放 Loader 阻塞；完成重试上限与中断释放单元测试验证。
- **Ticket 03 Resolved**: 在 `WebDavMediaService.onStartCommand` 中于广播分发窗口内同步执行前台服务提权；在 `PlaybackSessionHost` 中捕获并兜底后台启动异常，通过 `isElevationPendingRef` 暂存未生效提权；在 `prewarmService()` 中实现前台恢复自动对齐；完成 `WebDavMediaServiceTest` 与 `PlaybackSessionHostTest` 测试验证。
