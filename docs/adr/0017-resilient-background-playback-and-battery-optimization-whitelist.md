# 0017: Resilient Background Playback, Deferred Demotion, Dual Hardware Locks, and OEM Battery Whitelist

## Context

在 WebDavPlayer 的日常高频使用与真实设备排查中，发现了一个严重影响后台与锁屏播放体验的核心痛点：**应用在锁屏数分钟后播放卡死，再次解锁时发现播放中断并停滞在历史进度点。**

通过 ADB 深入抓取系统底层日志（`logcat`、`dumpsys media_session`、`dumpsys audio`），还原出了进程被杀与播放中断的完整真实事故现场：

1. **前台服务主动降级导致被判闲置杀死（App Idle Stop）**：
   - 现存实现中，`PlaybackSessionHost` 一旦感知到播放器进入 `PlaybackState.Paused`，便立即调用 `service.stopForeground(Service.STOP_FOREGROUND_DETACH)`，将前台服务降级为普通后台服务。
   - 在 Android 10+ / MIUI 等系统严苛的后台管控下，失去前台身份的后台服务在数分钟内即被系统强制终止：
     ```log
     W ActivityManager: Stopping service due to app idle: u0a345 com.webdav.player/.data.service.WebDavMediaService
     ```
2. **后台提权受阻与非法启动（Background Start Restriction）**：
   - 降级为后台服务后，当后续从后台尝试重新提升前台，或在 `MainActivity` 退入后台瞬间通过 `loadTrack()` / `play()` 被动触发 `startServiceInternal()` 时，直接撞上 Android 8.0+ / 12+ 的后台前台服务限制（FGS Restriction）：
     ```log
     W ActivityManager: Service.startForeground() not allowed due to bg restriction: service WebDavMediaService
     W ActivityManager: Background start not allowed: service Intent { cmp=...WebDavMediaService }
     No services match: WebDavMediaService
     ```
   - 导致服务无法启动或无法提权，底层播放引擎退化为无服务宿主保护的“裸奔”状态。
3. **MIUI 激进锁屏清理强制查杀（LockScreenClean Force-Stop）**：
   - 失去前台服务保护且未被列入系统省电白名单的单进程应用，其进程优先级 `oom_adj` 高达 915（普通后台缓存级）。手机锁屏后，MIUI 的 `ProcessManager` 直接执行无情查杀：
     ```log
     I ProcessManager: LockScreenClean: force-stop com.webdav.player Adj=915 State=20
     I ActivityManager: Force stopping com.webdav.player appid=10345 user=0: LockScreenClean
     I ActivityManager: Killing 22753:com.webdav.player (adj 915): stop com.webdav.player: LockScreenClean
     ```
4. **硬件级休眠断流（缺少 CPU WakeLock 与 Wi-Fi Lock）**：
   - WebDavPlayer 采用纯在线远程流式架构（无本地磁盘持久化缓存）。手机息屏后数十秒，系统 CPU 进入 Deep Sleep，Wi-Fi 芯片进入低功耗节能降频模式。由于代码中缺少任何硬件唤醒锁，导致底层 Socket 阻塞、流式传输饥饿超时，音频解复用器和解码器陷入假死。
5. **退后台内存未自洁**：
   - 单进程内 Compose UI 树、高分辨率专辑封面（Bitmap）及历史列表全量驻留在内存中（占用 130MB+），加剧了触发 Low Memory Killer (LMK) 与厂商清理的概率。

## Decision

对标 **椒盐音乐（Salt Player）** 等标杆级第三方独立播放器的工程实践，在坚守**单进程模型**的前提下，实施四大层面的深度架构重构与加固：

### 1. 单进程内部架构与生命周期治理（Service-Centric & Lifecycle Guard）
- **前台窗口合法预热绑定**：
  在 `MainActivity.onStart()`（应用处于合法前台窗口期）时主动预热启动并绑定 `WebDavMediaService`，彻底消除在后台异步触发 `startForegroundService` 引发的 `Background start not allowed` 异常。
- **前台身份单向连续性**：
  严禁在后台执行“降级为普通后台服务再重新申请前台服务”的危险跳变。在宽容退避期内始终保持 Foreground 身份，状态流转仅原地刷新通知栏按钮状态（`notify`），彻底杜绝 `Service.startForeground() not allowed due to bg restriction`。
- **退后台内存激进自洁（TrimMemory 机制）**：
  在 `MainActivity` 与 `WebDavApplication` 中实现 `ComponentCallbacks2.onTrimMemory(level)`。当监听到 `TRIM_MEMORY_UI_HIDDEN`（应用退入后台或息屏）时，主动调用 `ThumbnailMemoryCache.clear()` 释放位图内存并清除临时 UI 状态，将后台常驻内存从 130MB+ 压降至 50MB 左右，消除被 LMK 查杀的风险。
- **服务粘性与任务清理守护**：
  - `WebDavMediaService.onStartCommand()` 明确返回 `START_STICKY`，若遭遇不可抗力被杀，系统将在资源宽裕时尝试重启服务；
  - 重构 `onTaskRemoved()`：若当前处于播放中或处于延迟降级宽容期内，严禁调用 `stopSelf()`，保障用户在多任务列表划掉主界面卡片后音乐依然持续播放。

### 2. 15 分钟延迟前台降级（Deferred Foreground Demotion）
- **状态流转契约**：
  - **`Playing` / `Buffering`**：取消任何挂起的降级定时器，维持前台服务活跃状态（`isForegroundActive = true`）；
  - **`Paused`**：启动 15 分钟协程延迟降级计时器（`DEFAULT_DEMOTION_TIMEOUT_MS = 15 * 60 * 1000L`），通知栏切换为暂停图标，但严格维持 Foreground Service 身份不释放；
  - **15 分钟内恢复播放**：即刻取消定时器，零延迟无缝继续播放，无任何提权开销；
  - **15 分钟超时未恢复**：优雅调用 `service.stopForeground(Service.STOP_FOREGROUND_DETACH)`，仅保留可关闭的非前台通知，释放前台身份以节约电量；
  - **用户手动划除/通知栏点击退出**：彻底注销 Service 与通知栏。

### 3. 流式双硬件锁守护（Dual Streaming Hardware Locks）
- **CPU 唤醒锁（WakeLock）**：
  在 `Media3AudioPlayerEngine` 创建 ExoPlayer 时配置 `.setWakeMode(C.WAKE_MODE_NETWORK)`，由播放内核在音频数据消费与解码阶段自动持有 `PARTIAL_WAKE_LOCK`，确保息屏后 CPU 解码时钟不丢步。
- **Wi-Fi 高性能锁（WifiLock）**：
  在数据源层注入 `WifiManager.WifiLock`（`WIFI_MODE_FULL_HIGH_PERF`，带标签 `"WebDavPlayer:StreamingWifiLock"`）。
- **节能释放契约**：
  - **播放 / 缓冲中**：双锁共同 `acquire()`，确保护城河般的网络吞吐，抵抗 Wi-Fi 网卡息屏休眠；
  - **暂停 / 空闲时**：立即 `release()` 释放双锁，允许手机锁屏进入深度节能睡眠，绝不产生无谓电池耗电；
  - **恢复播放时**：再次动态申请双锁。

### 4. 厂商防杀感知与白名单常驻横幅（OEM Kill Detection & Whitelist Banner）
- **冷启动异常被杀检测（Killed Detection）**：
  在 `PlaybackSessionStore` 持久化会话中维护正常退出标记 `cleanExit: Boolean`。正常暂停、切换或退出时置为 `true`；播放中保持为 `false`。冷启动恢复时若检测到上次在播放中未正常退出，判定为遭遇系统强杀（如 MIUI `LockScreenClean`），在会话状态中置位 `wasKilledBySystem = true`。
- **全屏播放器常驻横幅（可跳转）**：
  在 `FullPlayerView` 顶部实时检查 `PowerManager.isIgnoringBatteryOptimizations(packageName)`。若未开启，常驻展示一条美观的卡片横幅：“检测到锁屏后可能被系统断流，点击优化后台播放保护”。
- **一键跳转适配**：
  用户点击横幅后，直接调用标准系统 Intent `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 请求豁免电池优化；同时针对小米（MIUI / HyperOS）等机型，弹出清晰指引并一键跳转至应用设置页，指导开启「自启动」与设置省电策略为「无限制」。

## Consequences

### Positive
- **彻底根治锁屏被杀与后台卡死**：通过前台窗口合法预热、前台身份连续性与 15 分钟宽容期，根除了 `Background start not allowed` 与 `Stopping service due to app idle`；
- **锁屏流媒体网络坚如磐石**：CPU 唤醒锁与 Wi-Fi 高性能锁协同作战，彻底杜绝息屏后网络芯片休眠引起的流式卡顿与假死；
- **对齐标杆级播放器体验**：单进程内实现退后台内存瘦身（压降 60% 内存），以极小的架构代价换取了媲美独立播放进程的抗杀能力；
- **零 IPC 复杂度与高可维护性**：避免了拆分多进程导致的 Room/DataStore 跨进程并发锁死与复杂的 AIDL 序列化开销，完整保留了 CUE 虚拟时间轴、双源元数据富化等高级特性。

### Trade-offs & Mitigations
- **电池电量消耗考量**：前台服务在暂停后多维持 15 分钟前台身份。通过在暂停瞬间**立即释放 Wi-Fi 锁与 CPU 唤醒锁**，CPU 与网卡得以正常进入休眠，仅保留微弱的 Service 评级，将额外电量损耗控制在忽略不计的水平；
- **白名单依赖用户授权**：对抗国产 ROM 锁屏强杀无法脱离系统权限。通过“异常被杀主动检测 + 全屏播放器显式横幅 + 一键跳转”的最短路径，极大降低用户的认知与操作门槛。
