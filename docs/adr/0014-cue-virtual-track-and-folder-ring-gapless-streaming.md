# 0014: CUE Virtual Track In-Stream Mapping and Folder-Bounded Gapless Streaming

## Context

在对比同类开源播放器（如 Folder-Player）的功能特性时，发烧友用户群体对 **CUE 整轨无损音频（FLAC/WAV/APE）虚拟分轨播放**与**连贯畅听体验**有着强烈的诉求。

然而，WebDavPlayer 从诞生起便坚守**“纯在线流式播放，无本地持久化磁盘缓存”**（参见 ADR-0002）的核心架构底线。在这一约束下引入 CUE 虚拟分轨与连贯播放，面临严峻的架构摩擦：

1. **物理切片与纯流式的致命冲突（Clipping vs Streaming Gap）**：
   业界常见的实现通常将 CUE 虚拟分轨借助 `ClippingMediaSource` 切分为多个物理 `MediaItem`。在有本地磁盘缓存的播放器中，该方案体验良好。但在**纯流式且无本地磁盘缓存**的环境下，ExoPlayer 切换 `MediaItem` 时会强制切断当前的 `DataSource`，并向 WebDAV 服务器重新建立 HTTP 连接发起 `Range: bytes=start-end` 请求。这会导致相邻分轨交界处发生 500ms ~ 2000ms 的网络握手与重缓冲间隙，造成明显的声学卡顿与爆音，彻底打破了无缝回放（Gapless Playback）。
2. **跨目录连播破坏文件夹边界感（Unbounded Directory Hopping）**：
   某些播放器实现的“无限跨文件夹播放”（Auto Next Folder）打破了用户对“专辑/目录”这一物理边界的掌控。在网络受限或层级复杂的 WebDAV 存储树中，自动跨目录漫游不仅增加了不必要的目录检索开销，也违背了用户专注于当前专辑的聆听习惯。

## Decision

坚守“纯流式无本地磁盘缓存”设计，建立**“单流虚拟轨映射”**与**“目录环形闭环”**的架构决策：

### 1. CUE 虚拟分轨采用“单物理流 + 逻辑虚拟轨”（Single Physical Stream + Logical Virtual Track）
- **物理流连续性**：针对整轨大音频文件（如 `album.flac`），底层播放引擎仅向 WebDAV 服务器建立一条长连接 HTTP 流式通道（单一 `Authenticated MediaSource`），回放跨越 CUE 分轨时**物理流绝对不断开、零重连握手、零解码器 Flush**。
- **虚拟时间轴映射（Virtual Timeline Engine）**：在会话层（`MusicPlayerAppSession`）引入轻量状态机，将底层播放器的大文件绝对进度转换为当前虚拟分轨的相对时间（`virtualPositionMs = rawPositionMs - cueTrack.startTimeMs`），向 UI 与播控条呈现独立的单曲进度。
- **跨轨零打扰广播（Non-Disruptive Virtual Hop）**：当播放进度跨越分轨时间点时，**严禁重载播放器**，仅通过会话层触发 `Non-Disruptive Metadata Enrichment`，将下一分轨的标题、艺术家及分轨号响应式同步至 `MediaSession`（通知栏、锁屏与蓝牙车机），实现声学级物理真无缝（Bit-Perfect Gapless）。
- **纯内存即时解析**：CUE 元数据仅在点选或进入目录时于内存中即时解析，不向 SQLite/Room 数据库落盘，严格保持纯流式零磁盘沉淀的设计纯粹性。

### 2. 播放边界严格限定在当前目录环形循环（Folder Ring Playback）
- **文件夹闭环契约**：播放队列（`Playback Queue`）以当前所处 `Remote Directory` 的音频曲目为唯一严格边界。
- **列表循环流转**：列表循环模式下，最后一首播放完毕后自动回流至当前目录首曲，杜绝跨越父级或同级兄弟目录进行不确定的自动连播。

### 3. 普通独立曲目间实施“流式预卷缓冲”（Pre-roll Stream Buffering）
- **长连接复用（Keep-Alive Connection Pool）**：OkHttp 客户端维持长连接池保活，确保连续请求同一 WebDAV 节点时 TCP/TLS 握手耗时为 0ms。
- **交叠预加载（Pre-roll LoadControl）**：配置 `DefaultLoadControl`，在上一曲目播放临近尾声（如剩余 10 秒）时提前发起下一曲目的前导数据流缓冲与 Extractor 探针预热，使得 ExoPlayer `AudioSink` 能够平滑拼接 PCM 数据，消灭流式播放下的跨文件静音间隙。

## Consequences

- **真·无缝播放体验（Bit-Perfect Gapless）**：CUE 整轨在纯流式下享受完全无断流、无重连的声学极致连续性，彻底超越物理切片方案。
- **零磁盘空间侵占**：完全遵循纯流式设计原则，不写任何音频缓存文件，不产生多余的本地缓存生命周期同步问题。
- **心智模型清晰**：播放范围被严格限制在当前文件夹内，消除了跨目录切换造成的用户迷失感与网络树递归请求的不确定性。
- **实现复杂度权衡（Trade-off）**：会话层需要承担虚拟时间轴与真实时间轴的映射逻辑，上一曲/下一曲由流内的 `seekTo` 驱动；切歌如果跨度较大且超出内存缓冲区（`Streaming Buffer`），需由 ExoPlayer 自适应发起 HTTP Range 重定位。
