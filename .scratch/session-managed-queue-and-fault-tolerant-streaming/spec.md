# Spec: 会话层切歌管控与弱网容错流式管线 (Session-Managed Queue Progression & Fault-Tolerant Streaming Pipeline)

Status: ready-for-agent

## Problem Statement

作为听书、评书及长音频内容的用户，当我在移动网络信号较弱（如进出电梯、地下车库、隧道）、Wi-Fi 抖动或访问家庭 NAS 遇到机械硬盘从休眠状态唤醒（冷启动马达起转延迟约 10~20 秒）时，播放器在短暂卡顿数秒后，不仅没有耐心地转圈等待缓冲加载，反而会擅自跳过当前正在收听的章节，自动开始播放下一集。

这导致我经常漏听重要内容，并且在发现跳集后不得不手动在列表中翻找上一集、重新寻找上次听到的时间点。此外，在网络意外断开时，底层的网络流异常截断常被播放器误判为“整首歌已自然播放完毕”，从而误触发了下一曲的连播。

我期望播放器在网络不好时能够坚定地停留在当前集并显示“正在缓冲”，在后台自动重试断点续传；一旦网络恢复，能够原地无缝继续播放，绝不擅自跳集。同时，当我在等待缓冲期间主动点击暂停或切歌时，播放器必须能够立即响应，绝不卡死。

## Solution

1. **会话层接管切歌决策权（Session-Managed Queue Progression）**：
   彻底改变将整个专辑或目录所有曲目一次性灌入播放器内核时间线的做法。底层播放引擎（`AudioPlayerEngine`）默认严格维持单轨载入，剥夺播放器底层在遇到网络错误时自主跳过曲目的权力。只有当当前曲目通过完整性校验自然播完、或用户在界面上显式点击“下一首”时，由 `MusicPlayerAppSession` 统一下发切换指令。同时，仅当当前曲目顺利播放至最后 10 秒且网络处于健康非卡顿状态时，才按需注入下一曲目实施无缝预卷缓冲（`Pre-roll Stream Buffering`）；一旦处于弱网卡顿，立刻退化为绝对单轨。

2. **伪播放结束双重熔断防线（Premature EOF Guard）**：
   - 底层数据源字节熔断（Content-Length Guard）：数据源在读取流结束时，核对已接收字节数与响应头声明的实体大小（`Content-Range` / `Content-Length`），未达标立即作为网络读取异常转入断点重连，禁止向解码器发送流结束信号。
   - 上层会话层时长校验熔断（Duration Guard）：会话层在接收到播放引擎的播放完成信号时，比对当前播放进度与音频总时长（`currentPositionMs < durationMs - 3000ms`）。如果尚未真正播放至尾声，判定为网络意外截断，拒绝推进播放队列，并在当前断点发起自动续播。

3. **底层数据源透明断点续连（Transparent Range Reconnection）**：
   在流式数据源内部捕获网络超时与连接中断，记录当前已读取的字节偏移量，采用指数退避算法（重试间隔平顶于 5s~8s）持续发起 HTTP Range 续传（`Range: bytes=(offset)-`）。重连对上层解码管道和播放器状态机透明，播放器平稳保持在 `PlaybackState.Buffering` 状态，不销毁解码器，进度条不跳变，网络恢复后毫秒级原地出声。

4. **流媒体专用连接锁定 HTTP/1.1**：
   专用于音频数据流的客户端强制限定为 `Protocol.HTTP_1_1`，彻底根除 HTTP/2 长时间单流读取 Range 时服务端多路复用流控窗口挂死与伪超时问题。

5. **用户操作最高抢占优先级**：
   在后台无限重试期间，只要用户主动操作（暂停、上一曲/下一曲、点选其他曲目），立刻取消正在进行的重试协程并释放网络连接，保证 UI 零卡顿。

## User Stories

1. 作为有声书听众，当我在弱网环境下收听较长章节时，我希望播放器在遇到网络卡顿时停留在当前集并显示“正在缓冲”，而不是擅自跳到下一集。
2. 作为评书爱好者，当家庭 NAS 机械硬盘休眠导致起转耗时约 15 秒时，我希望播放器能够耐心地退避重试拉取音频流，并在网络恢复后自动接续出声，以便我无需手动重新点播。
3. 作为用户，当流媒体网络连接因为信号切换瞬间断开时，我希望底层的断点续传能够自动从上次读取的字节偏移继续下载，以便我的播放进度条不会突然跳回开头或意外跳动。
4. 作为用户，当网络意外断开且接收到的数据流被突然截断时，我希望播放器不会把未听完的文件误认为是“自然播完”，以便防止假播放结束引发的自动跳曲。
5. 作为用户，当我正在等待网络缓冲时，如果我主动点击“暂停”按钮，我希望播放器能瞬间变为暂停状态并停止后台重试请求，以便节省手机电量和移动流量。
6. 作为用户，当我处于网络重试缓冲状态时，如果我主动点击“下一首”或点选另一首歌，我希望播放器能立即放弃当前重试并切换到新选曲目，以便操作没有任何延迟或卡顿。
7. 作为用户，当网络卡顿超过 15 秒仍未恢复时，我希望在播控条和通知栏上能够看到直观的“网络受阻，正在重连...”状态提示，以便清楚了解当前的连接情况。
8. 作为用户，当弱网持续时间较长（例如超过 30 秒）时，我希望播控区域能提供一个显式的“重试”按钮，以便我可以随时手动触发重新连接。
9. 作为音乐发烧友，在网络状况良好且当前歌曲顺利播放到剩余最后 10 秒时，我希望播放器能够自动预热下一首歌曲，以便享受零静音间隙的无缝连播体验（Pre-roll Stream Buffering）。
10. 作为音乐发烧友，如果当前歌曲在尾声阶段发生弱网卡顿，我希望播放器立即取消对下一首的预加载，全力集中网络带宽保障当前歌曲的缓冲与播放。
11. 作为用户，当我在收听 CUE 虚拟分轨（Virtual Track）时遇到弱网，我希望时间轴引擎和分轨映射机制与物理曲目同样保持断点续传和原地缓冲，以便不打乱分轨播放顺序。
12. 作为用户，在断点重连成功恢复播放后，我希望蓝牙耳机和车机屏幕（MediaSession）上的播放状态能无缝从缓冲变回播放，以便车载播控同步无误。
13. 作为用户，当播放真正到达整首歌曲末尾（进度与总时长相符）时，我希望播放器能正常顺畅地推进到下一集，以便在网络通畅时无需手动切歌。
14. 作为用户，当播放模式为“列表循环”且播放到最后一集真正结束时，我希望队列能无缝回环到目录第一集（Folder Ring Playback），而不是跨越到其他文件夹。
15. 作为用户，我希望整个弱网容错和断点重连过程完全基于内存缓冲与 HTTP Range 协议，绝不向手机磁盘写入任何持久化音频文件，严格遵守纯在线流式播放原则。
16. 作为用户，如果 WebDAV 服务器明确返回了 401（未授权）或 404（文件不存在）等不可恢复的 HTTP 错误，我希望播放器能够立刻停止重试并给出清晰的报错提示，而不是死循环等待。
17. 作为用户，当我在弱网缓冲期间将应用退到后台时，我希望前台播放服务（Foreground Service）和通知栏保持活跃，以便系统不会因为短时间无音频输出而立即杀掉播放进程。
18. 作为用户，当弱网导致音频流恢复耗时数秒时，我希望恢复播放时解码器不会发出刺耳的爆音或破音，以便获得高保真的声学体验。

## Implementation Decisions

### 1. 会话层切歌决策权收拢 (Session-Managed Queue Progression)
- **解耦底层播放列表**：
  改造 `Media3AudioPlayerEngine`，其职责收敛为单一当前媒体项（或双轨预热时追加的下一项）的流式渲染容器。
  弃用批量全量装载接口 `player.setMediaSources(allTracks)`，改为按需装载当前活跃 `AudioTrack`。
- **切歌状态机推进权**：
  切歌的所有权完全归属于 `MusicPlayerAppSession`。只有当：
  1. 会话层收到底层上报的真实完成事件并通过 Duration Guard 校验；或
  2. 用户主动调用 `skipToNext()`、`skipToPrevious()` 或 `seekToTrack()`；
  会话层才更新 `queue.currentIndex`，并命令底层引擎加载并播放新曲目。
- **动态双轨预热窗口 (Adaptive Dual-Track Window)**：
  在 `MusicPlayerAppSession` 中监控高频播放进度：
  - 当 `durationMs - currentPositionMs <= 10_000L` 且 `playbackState is PlaybackState.Playing` 且最近 30 秒内未发生 `Buffering` 时，向播放引擎发送下一首的预加载信号（`player.addMediaSource`）。
  - 若当前曲目处于 `PlaybackState.Buffering` 或刚刚触发过重连，严格抑制预热指令，维持绝对单轨。

### 2. 伪播放结束双重熔断机制 (Premature EOF Guard)
- **数据源层 Content-Length 熔断**：
  在自定义的流式数据源读取循环中维护累计已读字节计数 `totalBytesRead`：
  - 打开连接时记录响应头中的预期字节数 `expectedContentLength`。
  - 当网络输入流返回 `-1` 时，若 `expectedContentLength > 0` 且 `totalBytesRead < expectedContentLength`，严禁向 ExoPlayer 返回 `-1`，而是直接抛出 `UnexpectedEndOfStreamException`，使其触发断点重传。
- **会话层 Duration 熔断**：
  在 `MusicPlayerAppSessionImpl` 中监听引擎的播放状态流：
  - 当收到 `PlaybackState.Ended` 时，校验 `clampedPositionMs >= durationMs - DURATION_COMPLETION_TOLERANCE_MS`（容差设为 3000ms）。
  - 若进度不足，判定为异常断流，拦截 `PlaybackState.Ended` 状态，不推进队列索引，并命令播放引擎在当前进度 `currentPositionMs` 执行原位断点重试。

### 3. 底层数据源透明断点重连 (Transparent Range Reconnection)
- **封装带自愈能力的流式数据源**：
  构建 `ResilientWebDavDataSource`（实现 Media3 `DataSource` 规范），包裹底层 HTTP 请求：
  - 内部记录当前 `currentStreamOffset = request.position + bytesRead`。
  - 当读取过程中捕获 `SocketTimeoutException`、`HttpDataSourceException` 或连接重置时，不向解码器抛出中断异常。
  - 使用指数退避延迟（初始 1000ms，每次乘 2，最大平顶为 6000ms），在内部自动重新构造携带 `Range: bytes=(currentStreamOffset)-` 的新 HTTP 请求并无缝续接输入流。
  - 在重连期间，向外暴露 `isReconnecting = true` 状态，使播放器平滑处于 `STATE_BUFFERING`。
- **不可重试状态码快速通道**：
  对 HTTP 400、401、403、404、405、410 等致命响应码，不执行重试，立即向上抛出错误。

### 4. 传输协议与超时配置 (Streaming Client Protocol)
- **HTTP/1.1 协议锁定**：
  在 `OkHttpWebDavClient` 的 `streamingBaseOkHttpClient` 中显式指定 `protocols = listOf(Protocol.HTTP_1_1)`，规避 HTTP/2 长时间单流读取的流控挂死问题。
- **流式超时调优**：
  将流媒体读取超时 `readTimeout` 适当放宽至 60 秒，连接超时 `connectTimeout` 设为 30 秒，兼容家庭 NAS 机械硬盘起转耗时。

### 5. 用户操作高优先级抢占 (User Preemption Priority)
- **响应式取消机制**：
  所有断点重连与网络读取均运行在受控的 Coroutine / Thread 作用域内。
  - 当会话层接收到 `pause()` 时，立即取消当前活动的数据流读取或重试等待，置状态为 `PlaybackState.Paused`。
  - 当会话层接收到 `skipToNext()` 或外部定位请求时，立即关闭旧连接，重置字节计数器，无缝载入新目标。

### 6. 恪守 ADR-0002 纯流式约束
- 整个重试与断点续传完全依托内存输入流、OkHttp 内部缓冲区与 HTTP Range 协议完成，不向任何本地磁盘、数据库或缓存目录写入音频分片文件，保持零磁盘空间沉淀。

## Testing Decisions

### 1. 良好测试准则
- 仅测试可观察的外部行为（播放状态流转、队列索引变更、重试行为、进度与事件派发），严禁探测私有变量或私有实现细节。
- 采用虚拟测试调度器（Standard Test Dispatcher）与 MockWebServer / FakeOkHttpClient，杜绝在单元测试中产生真实网络 I/O。

### 2. 测试接缝与层级设计 (Testing Seams)
- **最高领域接缝（Highest Domain Seam - 推荐主力接缝）**：
  - **模块**：`MusicPlayerAppSessionImpl`
  - **测试内容**：
    - 当底层播放器由于网络异常断开提前报告 `Ended` 但当前进度未达总时长时，验证 `queue.currentIndex` 保持不变，且触发原位续播调用，绝不推进到下一首。
    - 验证当前曲目真正完整播放完毕时，正确且仅触发一次向下一首的正常切歌。
    - 验证弱网状态下（频繁重试或卡顿），会话层抑制双轨预热，维持单轨运行。
    - 验证在缓冲重试过程中调用 `pause()` 或 `skipToNext()` 时，会话状态瞬间流转，抢占优先。
- **流媒体传输接缝（Streaming Transport Seam）**：
  - **模块**：`ResilientWebDavDataSource` / `WebDavMediaSourceAdapter`
  - **测试内容**：
    - 模拟 OkHttp 在传输中途抛出 `SocketTimeoutException`，验证数据源内部以正确的 `Range: bytes=(offset)-` 自动重连，且上层读取流无缝拼接，字节零缺失。
    - 模拟服务端提前截断流（返回 EOF 但字节数不达标），验证数据源抛出校验异常进入重试，而非返回 `-1`。
    - 验证遇到 HTTP 401 / 404 时快速失败，不进入死循环重试。
- **协议配置验证接缝**：
  - **模块**：`OkHttpWebDavClient`
  - **测试内容**：
    - 验证 `streamingBaseOkHttpClient` 的协议列表仅包含 `Protocol.HTTP_1_1`。

### 3. 代码库先例 (Prior Art)
- `MusicPlayerAppSessionTest.kt`：会话状态机驱动与队列流转测试先例。
- `WebDavStreamingPlaybackTest.kt`：流媒体数据源构建与适配器先例。
- `EndToEndPlaybackPipelineIntegrationTest.kt`：端到端完整链路协同测试先例。

## Out of Scope

- 本地持久化音频磁盘缓存（`CacheDataSource`，明确由 ADR-0002 排除）。
- 跨目录或跨文件夹无限连播（明确由 ADR-0014 Folder Ring 排除）。
- 视频或非音频格式的自适应流支持。
- WebDAV 目录树增量索引协议修改。

## Further Notes

- **蓝牙车机兼容性**：在底层触发透明断点重试且播放器进入 `Buffering` 期间，`WebDavMediaSessionCallback` 需向系统 MediaSession 报告 `STATE_BUFFERING` 而非 `STATE_ERROR`，防止部分蓝牙车机误认为播放终结而自动退出车机播放模式。
- **通知栏体验**：当处于弱网重试超过 15 秒时，前台服务通知栏副标题显示“正在缓冲网络数据...”，通知保持常驻，给用户清晰的视觉确定感。
