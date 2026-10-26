# 0019: Streamline Playback Pipeline and Consolidate Session Host

## Context

在 ADR-0016（确立底层播放引擎为流式渲染管道）与 ADR-0018（收拢系统播控意图至应用会话层）演进后，播放核心的架构边界得到了显著收窄。然而，近期对代码库热点区域（近 40 次提交中 22 次涉及的区域）的架构审查揭示，`PlaybackSessionHost` 与底层 `Media3AudioPlayerEngine` 之间依然存在多处浅显委派、接缝击穿与状态竞争缺陷：

1. **接缝向下强转泄漏（Seam Downcasting Leaks）**：
   - `PlaybackSessionHost` 名义上实现了 `AudioPlayerEngine` 接口并采用类委托（`by playerEngine`），但在内部多达 5 处强行使用 `(playerEngine as? Media3AudioPlayerEngine)` 访问底层具体实现细节：
     - 读取 `hardwareLockManager`；
     - 注入 `engine.sessionCallback`；
     - 注入 `engine.onSkipToNext` 与 `engine.onSkipToPrevious` 回调；
     - 读取 `audioRoutingObserver`；
     - 调用 `engine.onAudioRoutingChanged()`。
   - 这种模式破坏了接口的封装性，使得 `AudioPlayerEngine` 接口沦为虚设接缝。

2. **双重硬件锁重复仲裁与冗余参数传染**：
   - `PlaybackSessionHost` 与 `Media3AudioPlayerEngine` **两边各自**维护对 `playbackState` 的协程/监听器收集，并在每次状态变迁时**各自独立调用** `hardwareLockManager.acquireLocks()` 与 `releaseLocks()`。
   - `DefaultWebDavMediaSourceAdapter` 在构造函数中引入了 `StreamingHardwareLockManager` 参数，并层层向上穿透传递，但适配器内部从未消费过该锁，纯属参数污染。

3. **系统媒体会话（MediaSession）所有权错位**：
   - `MediaSession`（Android 系统媒体会话）实际由 `Media3AudioPlayerEngine` 内部构造，内部包装了复杂的 `ForwardingPlayer`（`sessionPlayer`）并设立了可变的 `sessionCallback` 与跳过钩子。
   - 然而，真正管理系统服务（`WebDavMediaService`）、前台通知（`WebDavNotificationProvider`）、音频焦点（`AudioFocusHandler`）以及系统按键回调（`WebDavMediaSessionCallback`）的却是外层的 `PlaybackSessionHost`。这种宿主与引擎倒错的所有权关系导致了第 1 点中繁琐的回调反向连线。

4. **测试表面与生产真实路径偏离**：
   - `PlaybackSessionHost` 暴露了 `@VisibleForTesting fun attachAudioRouting` 与 `val audioRoutingObserver`，仅仅是为了让单元测试通过宿主去测试底层引擎内部的路由刷新。

## Decision

基于与开发者的对齐共识，彻底落实深层模块（Deep Module）设计准则，完成三大核心收敛：

### 1. 硬件锁生命周期收归宿主层（Consolidate Hardware Locks in PlaybackSessionHost）
- **职责划分**：`StreamingHardwareLockManager`（CPU WakeLock + Wi-Fi Lock）的持有与释放属于 Android 系统级前台服务与生命周期保活范畴，由 `PlaybackSessionHost` 独家统一管控。在进入 `Playing` / `Buffering` 且前台激活时加锁，在 `Paused` / `Error` / `Idle` / `Ended` 及服务销毁时释放锁。
- **净化底层引擎**：彻底移除 `Media3AudioPlayerEngine` 中对 `StreamingHardwareLockManager` 的状态监听与操作（ExoPlayer 原生自带 `setWakeMode(C.WAKE_MODE_NETWORK)` 兜底底层网络解码）；
- **净化媒体源适配器**：移除 `DefaultWebDavMediaSourceAdapter` 构造函数及属性中的 `hardwareLockManager`。

### 2. 媒体会话所有权收归宿主层（Consolidate MediaSession in PlaybackSessionHost）
- **宿主独占 MediaSession**：将 `MediaSession` 及其 `sessionPlayer` 的构建与生命周期完全迁移至 `PlaybackSessionHost`。宿主在构建时直接将其关联至构造注入的 `WebDavMediaSessionCallback`，彻底消除运行时可变 `var sessionCallback` 与 `engine.onSkipToNext / onSkipToPrevious` 反向回旋钩子；
- **窄化引擎管道**：`Media3AudioPlayerEngine` 只专职实现 `AudioPlayerEngine` 接口中声明的物理流装载、预卷装填、非打扰元数据更新与播放/暂停控制，对外部媒体会话与系统控制器完全无知。

### 3. 动态音频路由完全内化（Internalize Audio Routing in Engine）
- **引擎内自闭环**：`AudioRoutingObserver` 与底层 `AudioSink` / `CanonicalResamplingAudioProcessor` 的缓冲区刷新是底层音频硬件管道的私有自愈行为，完全在 `Media3AudioPlayerEngine` 内部独立启动与响应；
- **清理宿主穿透接缝**：彻底删除 `PlaybackSessionHost` 上的 `audioRoutingObserver` 透传属性及 `@VisibleForTesting attachAudioRouting` 方法；
- **测试表面对齐**：音频路由变更与缓冲区刷新测试直接以 `Media3AudioPlayerEngine` 为测试切面（Seam）。

## Consequences

- **消灭跨层强转（Zero Downcasts）**：`PlaybackSessionHost` 中 5 处 `as? Media3AudioPlayerEngine` 强转彻底被消除，`AudioPlayerEngine` 成为真实的纯物理流渲染接缝。
- **单点保活仲裁（High Locality）**：硬件锁、前台服务提升、延迟降级与 `MediaSession` 生命周期集中在宿主模块中，杜绝两处状态机竞争。
- **参数纯净（Clean Adapter）**：`WebDavMediaSourceAdapter` 不再携带无关的硬件锁依赖。
- **接口即测试表面（Align Test Surface）**：宿主测试专注断言前台保活与媒体会话派发；引擎测试专注断言流式解码、规整重采样与路由刷新，职责边界完全正交。
