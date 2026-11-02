# Feature Map: System-Level Network Connectivity Awareness, Transparent Auto-Resumption, and Offline Playlist Self-Healing

## Summary
实现系统级网络连通性感知、弱网挂起断点无感自愈续播与离线歌单静默健康恢复，彻底解决网络环境波动（如进出电梯、地铁隧道、基站切换）下的三大体验瓶颈：
- **Ticket 01**: 系统级网络连通性响应式观察者（带 500ms 防抖）。基于 Android 原生 `ConnectivityManager.NetworkCallback` 监听网络物理能力（`NET_CAPABILITY_INTERNET`, `NET_CAPABILITY_VALIDATED`）与传输介质（`TRANSPORT_WIFI`, `TRANSPORT_CELLULAR`），向上暴露纯净的领域层 `StateFlow<NetworkStatus>`；内置 500ms 协程防抖窗口以过滤高频信号翻转。
- **Ticket 02**: 防鬼畜误播的透明断点无感续播流水线。将网络状态流直通核心播放调度器与故障恢复引擎；当网络恢复连通时，严格校验恢复前置条件（弱网挂起暂停且支持重试且有有效曲目），用户主动暂停或耳机拔出（Becoming Noisy）保护严格禁止自动恢复；从精确毫秒偏移量平滑恢复数据流并自动隐藏弱网重试横幅。
- **Ticket 03**: 离线歌单曲目可达性静默自愈。网络恢复后，异步触发歌单可达性自愈，查询标记为 `SERVER_UNREACHABLE` 的失联服务器，并发派发轻量探测（`PROPFIND Depth: 0` / `testConnection`）；探测成功的服务器批量将对应灰显曲目复原为激活状态，无需用户手动逐一测试。
- **Ticket 04**: 全量端到端流水线回归与发布包二进制体积预算（APK <= 7.5 MB, classes.dex <= 4.0 MB, arm64-v8a 架构）质量门禁。

---

## Decisions
- **Domain Model & Interface Seams**:
  - 在领域层定义 `data class NetworkStatus(val isConnected: Boolean, val isWifi: Boolean = false, val isCellular: Boolean = false, val isMetered: Boolean = false)`。
  - 定义领域契约接口 `NetworkConnectivityObserver`，暴露 `val networkStatus: StateFlow<NetworkStatus>`。
  - 提供确定性单元测试桩 `FakeNetworkConnectivityObserver`。
- **Platform Observer Implementation & Lifecycle**:
  - 在 `DefaultNetworkConnectivityObserver` 中使用 `ConnectivityManager.registerDefaultNetworkCallback`，安全清理回调并注入 500ms 防抖。
  - 作为单例在 `AppContainer` 与 `WebDavApplication` 中托管。
- **Ghost-Proof Auto-Resumption in PlaybackFaultTolerance**:
  - `PlaybackFaultTolerance` 扩展 `onNetworkRestored(onAutoResume: () -> Unit)`，只在 `sessionState.isWeakNetwork && sessionState.canManualRetry && sessionState.hasTrack` 时触发自动恢复。
  - 主动暂停或音频路由打断时严格抑制恢复。
- **Offline Playlist Reachability Self-Healing in PlaylistRepository**:
  - `PlaylistDao` 提供 `getDistinctServerIdsForInactiveReason` 与 `clearServerUnreachableStatus` 批处理操作。
  - `PlaylistRepository` 扩展 `triggerReachabilitySelfHealing()`，并发探测离线服务器并批量恢复曲目。

---

## Ticket Topology & Dependency Graph

```text
[01-system-network-connectivity-observer] ──┬──► [02-ghost-proof-transparent-playback-auto-resumption] ──┬──► [04-end-to-end-pipeline-and-packaging-budget-gate]
                                            └──► [03-offline-playlist-reachability-self-healing]   ───────┘
```

---

## Tickets Breakdown
1. `01-system-network-connectivity-observer.md`: 系统级网络连通性响应式观察者与防抖 (Status: ready-for-agent, Blocked by: None)
2. `02-ghost-proof-transparent-playback-auto-resumption.md`: 防鬼畜误播透明断点无感续播 (Status: ready-for-agent, Blocked by: 01)
3. `03-offline-playlist-reachability-self-healing.md`: 离线歌单网络恢复静默自愈 (Status: ready-for-agent, Blocked by: 01)
4. `04-end-to-end-pipeline-and-packaging-budget-gate.md`: 全量回归与二进制体积预算门禁 (Status: ready-for-agent, Blocked by: 02, 03)

---

## Decisions-so-far
- **Task Graph Initialized**: 建立集成分支 `feature/network-awareness-and-auto-resumption`，确定 4 个任务的拓扑关系与依赖图谱。
