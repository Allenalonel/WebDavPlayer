# 0015: Session-Managed Queue Progression and Fault-Tolerant Streaming Pipeline

## Context

在坚守“纯在线流式播放、无持久化磁盘缓存”（ADR-0002）的核心原则下，移动网络在弱网、家庭 NAS 机械硬盘休眠冷启动或网络波动时，极易诱发网络 I/O 读取超时或 TCP 连接静默断开。

在此场景下，原播放体系暴露出两项破坏听感体验的缺陷：

1. **播放器内核越权跳集（Autonomous Player Transition）**：以往将整个目录的 `Playback Queue` 批量灌入 ExoPlayer 内部时间线。当底层发生网络读取超时（`SocketTimeoutException`）或错误重试耗尽时，ExoPlayer 默认将当前曲目判定为损坏项，并自动推进至下一曲目（`onMediaItemTransition(reason = REASON_AUTO)`）。在有声书、评书等长音频场景下造成严重跳集漏听。
2. **伪播放结束误触发（Premature EOF）**：当网络流意外中断且底层流读到 `-1` 时，若未触发致命异常，解码器将误判为音频已自然播完（`STATE_ENDED`），促使播放器顺理成章地连播下一集。
3. **HTTP/2 多路复用流挂起**：流媒体客户端使用 HTTP/2 单流长时间读取 Range 分片时，受服务端窗口更新及多路复用流控影响，易在读取响应头阶段触发假死超时。

## Decision

坚守 ADR-0002 纯流式零磁盘沉淀约束，建立**“会话层接管切歌权”**、**“伪播放结束双重熔断”**与**“底层透明断点续连”**的高韧性流媒体管线决策：

### 1. 切歌决策权收拢至会话层（Session-Managed Queue Progression）

- **单轨/双轨动态窗口**：ExoPlayer 内部不再全量预载整张专辑的媒体源。平时播放器内部严格维持单轨载入，剥夺播放器内核自主跨曲跳过权。
- **弱网动态抑制预热**：仅当当前曲目顺畅播放至最后 10 秒、且网络处于健康非缓冲状态时，才按需注入下一曲目实施 `Pre-roll Stream Buffering`（ADR-0014）；一旦检测到弱网重试或频发卡顿，立即退化为绝对单轨，集中全部带宽与连接池保障当前曲目。
- **高频采样与控制副作用的双层线程防线（Dual-Layer Thread Confinement）**：高频进度流计算（`combine`）严格驻留在后台工作线程（`Dispatchers.Default`）以保障 UI 流畅度；一旦会话层计算出需要向底层播放器施加控制副作用（如触发 `prerollTrack`、`updateTrack`），必须通过 `coroutineScope.launch(Dispatchers.Main)` 将调用明确移交主线程，并配合**原子状态二次校验**防范时序颠倒。同时底层 `Media3AudioPlayerEngine` 内置 `runOnApplicationLooper` 兜底屏障，当检测到非主线程直接调用时自动异步投递至应用 Looper，形成双层防御，彻底杜绝 `IllegalStateException: Player is accessed on the wrong thread`。

### 2. 伪播放结束双重熔断防线（Premature EOF Guard）

- **底层字节计数熔断（Content-Length Guard）**：数据源读取层在流结束时比对已接收字节与响应头声明长度（`Content-Range` / `Content-Length`），未达标立即抛出 `UnexpectedEndOfStreamException` 转入断点重连，禁止向解码器派发 EOF。
- **会话层时长校验熔断（Duration Guard）**：应用会话层监听到 `Player.STATE_ENDED` 时，严格对比当前进度与音频总时长（`currentPositionMs < durationMs - 5000ms`）。采用 5000ms 容差既能牢牢阻断中途意外网络截断，又能防止因 ASF/WMA 标准 Preroll（通常为 3100ms）或编码器 Padding 导致正常完播被误拦截。若未真实播完，拒绝推进队列，并在当前断点自动发起重连。

### 3. 底层数据源透明断点续连（Transparent Range Reconnection）

- **无感重试机制**：在数据源传输层捕获超时及网络断线异常，自动记录当前已读取的字节偏移量，采用指数退避（重试间隔平顶于 5s~8s）持续发起 HTTP Range 续传（`Range: bytes=(offset)-`）。
- **非 206 与 416 边界防线**：若续传时已读取过数据且遇到 `416 Range Not Satisfiable`，优雅转换为流结束而不是死循环重试；当数据已全部读满（`totalBytesRead >= expectedContentLength`）时主动终止连接。
- **零破坏渲染管道**：重连过程完全在数据源内部完成，播放器仅保持为 `PlaybackState.Buffering`，不向应用抛出不可逆的 Fatal Error，不销毁解码器，播放进度条不发生回跳。
- **用户交互最高抢占优先级**：用户主动点击暂停、切歌或取消时，瞬间取消底层重试协程并释放网络连接，确保交互零迟滞。

### 4. 纯内存自适应大缓冲（Expanded Weak-Network Buffer）

- 坚守 ADR-0002 纯在线流式零持久化落盘约束，在 `StreamingLoadControlConfig` 中引入弱网韧性大缓冲配置（`createWeakNetworkResilientConfig`），将最大缓冲从 50 秒弹性提升至 90 秒（`maxBufferMs = 90_000`），在起播阈值（1.5s）与重缓冲阈值（3s）不变的前提下，利用网络波动间歇预存 1.5 分钟音频流，显著拓宽抗断网抗抖动时间窗口。

### 5. 流媒体专用连接锁定 HTTP/1.1

- 专用于音频数据流的 OkHttpClient 明确锁定 `Protocol.HTTP_1_1`，规避 HTTP/2 单流长时间读取引发的多路复用队头阻塞与流控死锁。短报文元数据与目录查询继续保持灵活协商。

## Consequences

- **彻底消除弱网跳集**：弱网时播放器永远停留在当前曲目等待缓冲，网络恢复秒级自动继续发声，绝不意外跳过任何章节。
- **严格捍卫纯流式底线**：所有重试与断点接续均基于内存流与 HTTP Range，未向设备磁盘写入任何持久化音频文件，完全符合 ADR-0002。
- **架构职责分明**：播放引擎退化为纯粹的单轨/双轨音频渲染管道，所有的播放列表调度、预热判定、切歌仲裁均由 `MusicPlayerAppSession` 统一负责，与 CUE 虚拟分轨调度模型达成高度统一。
