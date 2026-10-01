# Synchronous Service Elevation and Dynamic Authenticated MediaSource

## Context

在 WebDAV 远程音频流式播放架构演进中，遇到了两个影响核心可用性的 P0 级严重缺陷：
1. **Android 前台服务生命周期致命崩溃（RemoteServiceException）**：在 Android 8.0+ 及更高版本中，通过 `Context.startForegroundService()` 启动服务后，系统严格要求必须在 5 秒内调用 `Service.startForeground()` 发布前台通知。原实现将通知提升时机绑定在播放引擎达到有效缓冲或就绪状态之后；一旦遇到网络高延迟、DNS 较慢或 SSL 握手延迟超过 5 秒，系统直接强制杀死应用进程，造成无法被常规 try-catch 拦截的致命异常。
2. **后台元数据富化引发凭证丢失（HTTP 401 & SSL 失败）与声学卡顿**：对于需要 HTTP Basic 认证或启用自签名 SSL 证书的 WebDAV 服务器，后台异步提取到高清封面或曲目标签后会通知播放引擎更新。原实现直接在播放器时间线上替换 `MediaItem`，触发 ExoPlayer 内部重建 `MediaSource`；然而默认的数据源工厂缺失活跃服务器的认证头与 SSL 套接字配置，导致重建请求退化为无认证访问，被服务端返回 401 Unauthorized 立即中断播放；同时，推倒重建解码管线会导致播放产生刺耳卡顿与二次缓冲。

## Decision

1. **首帧零延迟前台服务提升（First-Frame Synchronous Service Elevation）**：
   - 在 `WebDavMediaService` 创建的首帧生命周期回调中，无条件同步调用 `PlaybackSessionHost` 发布初始播放准备通知并提升为前台服务。
   - 彻底将前台服务的系统契约与播放引擎内部的异步状态（`Buffering`/`Ready`）完全解耦，以 0ms 满足系统的 5 秒约束，彻底消除 `RemoteServiceException`。
   - 后续播放状态变更仅驱动已有通知的内容更新与前台标记切换，不再重复触发跨进程竞争。
2. **动态凭据化媒体源工厂（Dynamic Server-Aware DataSource Factory）**：
   - 为 `Media3AudioPlayerEngine` 全局装配动态代理数据源工厂，深度绑定 `WebDavMediaSourceAdapter`。
   - 播放引擎内任何由此工厂衍生或自动重建的 `MediaSource`，均动态注入当前 `Active Server` 的 `Server Context`，自动附带 `Authorization: Basic ...` 认证头、自定义超时与自签名 SSL 信任配置，杜绝未认证裸请求发生。
3. **无感在途元数据富化（Non-Disruptive Reactive Metadata Enrichment）**：
   - 区分当前音轨与队列待播音轨的更新机制：
     - **待播/已播音轨**：直接安全更新播放列表中的 `MediaItem`。
     - **正在播放音轨**：禁止对底层 ExoPlayer 时间线执行破坏性重建；新提取的元数据仅更新 `MusicPlayerAppSession` 响应式状态流及系统 `MediaSession`。
   - 悬浮 Mini-Player、Full Player Sheet 及系统锁屏播控均直连响应式会话状态，实现秒级无感更新且保持音频解码流平滑连续。

## Consequences

- 根除了高延迟网络与弱网冷启动下的进程崩溃隐患。
- 保证了在有密私有云及自签名 NAS 环境下，流式播放永远携带正确凭证，网络临时抖动恢复时不丢认证。
- 架构上明确了系统服务层、播放解码层与会话状态层的职责边界，消除了多层状态竞争。
