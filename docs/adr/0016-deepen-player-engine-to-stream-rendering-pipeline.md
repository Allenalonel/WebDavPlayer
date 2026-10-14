# 0016: Deepen Audio Player Engine to Stream Rendering Pipeline and Unify Session Intent

## Context

在完成会话集中管控切歌权（ADR-0015）与 CUE 虚拟分轨（ADR-0014）的架构演进后，代码库确立了“底层播放内核仅装入单轨或双轨预热、跨曲决策全权收归应用会话层”的设计原则。

然而在代码审查与架构深度评审中，底层播放引擎接缝（`AudioPlayerEngine`）暴露出了严重的浅显化（Shallow Interface）与概念撕裂：

1. **接口形式与真实实现严重脱节**：
   - `AudioPlayerEngine` 接口依然保留了历史遗留的 `playTracks(server, tracks: List, startIndex)`、`seekToTrack(index)`、`removeTrack(index)`、`insertTrack(index)` 等全量播放列表方法。
   - 实现类 `Media3AudioPlayerEngine` 内部维护了 `loadedTracks: List<AudioTrack>` 私有集合，但在实际执行中，ExoPlayer 物理时间线永远只装载 1 个活跃曲目（`activeTrack`）以及至多 1 个预卷曲目（`prerollTrack`）。
   - 底层实现的队列操作与实际 ExoPlayer 播放管道毫无物理关联，形成虚假的状态镜像。

2. **幽灵接缝（Ghost Seam）与反向回调回旋镖（Circular Hijack）**：
   - 接口声明的 `val shufflePermutation: List<Int>?` 在实际引擎实现中恒为 `null`，沦为无人兑现的幽灵属性；而会话层却仍存在读取该属性的防御分支。
   - 系统通知栏与蓝牙线控回调（`WebDavMediaSessionCallback`）收到切歌事件时调用底层 `playerEngine.skipToNext()`，底层再通过 `SkipHandler` 回调回会话层 `session.skipToNext()`。这种跨接缝反向迂回不仅架空了底层自身的备用切歌逻辑，还构成了双向耦合。

3. **破坏“接口即测试表面”（The Interface is the Test Surface）**：
   - 测试夹具被迫编写了 200+ 行的 `FakeAudioPlayerEngine`，在测试桩内部模拟完整的曲目列表增删与洗牌算法。
   - 生产环境中切歌 100% 由 `MusicPlayerAppSessionImpl` 防御网络断流（Duration Guard / CUE 虚拟时间线）；而测试桩却在测试底层自算下一首，导致测试跨越的接缝与生产实际运行的路径完全不一致。

## Decision

彻底落实 ADR-0015 架构决议，将 `AudioPlayerEngine` 重塑为高内聚、窄接口的**流式播放渲染管道（Streaming Playback Pipeline）**，完成以下四大设计收敛：

### 1. 彻底剥离多曲目列表概念，收窄为单流/双流管道接口
- **删除多曲目接口**：彻底移除 `playTracks(List)`、`seekToTrack`、`removeTrack`、`insertTrack`、`loadedTracks`、`currentTrackIndex`。
- **确立纯流式加载语义**：
  - `loadTrack(server: WebDavServer, track: AudioTrack, startPositionMs: Long = 0L)`：装载并启动单物理音频流渲染。
  - `prerollTrack(server: WebDavServer, track: AudioTrack)`：剥离多余的 `targetTrackIndex` 参数，仅将下一轨音频流装填进底层的 `Pre-roll Stream Buffering` 管道。
  - `clearPrerollTrack()`：取消预加载流。
  - `updateTrackMetadata(track: AudioTrack)`：剥离 `index` 参数，专职负责当前流的非打扰元数据富化（Non-Disruptive Metadata Enrichment）。

### 2. 铲除幽灵接缝与双向回调，收拢切歌意图至会话域
- **彻底删除幽灵属性与反向回调**：
  - 移除 `val shufflePermutation: List<Int>?`。洗牌置换序列完全由 `PlaybackQueue` 自身的数据函数掌管。
  - 移除 `fun skipToNext()`、`fun skipToPrevious()` 及 `interface SkipHandler` / `fun setSkipHandler(...)`。
- **系统通知栏与线控直接意图派发**：
  - `WebDavMediaSessionCallback` 响应 `onSkipToNext` / `onSkipToPrevious` 时，直接派发意图至 `MusicPlayerAppSession`。
  - 会话层结合 CUE 虚拟时间线换算、队列边界与弱网状态统一仲裁切歌，再通过单向命令调用底层的 `loadTrack`。彻底消除回旋镖调用。

### 3. 净化测试夹具（Purify Test Surface）
- 将 `FakeAudioPlayerEngine` 瘦身为极简的流状态记录器（仅保存当前 loadedTrack、prerollTrack、播放状态及时间戳），代码量削减 70% 以上。
- 队列增删、洗牌遍历与模式循环测试 100% 集中于 `PlaybackQueueTest` 和 `MusicPlayerAppSessionTest`。生产与测试跨越同一真实接缝。

## Consequences

- **极高的杠杆率（High Leverage）**：播放引擎接口从 20+ 个方法/属性锐减为 11 个专注的物理流控制方法，外部调用者的心智负担降至最低。
- **严格的局部性（Strict Locality）**：列表调度、切歌熔断（Premature EOF Guard）与 CUE 虚拟分轨 100% 收敛于会话层，底层无多余镜像状态。
- **测试真实信度提升**：消除虚假的列表测试桩，测试表面与底层真实渲染行为绝对一致。
- **重构波及面可控（Bounded Blast Radius）**：受影响范围集中于 `AudioPlayerEngine`、`Media3AudioPlayerEngine`、`PlaybackSessionHost`、`WebDavMediaSessionCallback`、`MusicPlayerAppSessionImpl` 以及相关的单元测试，不影响 UI 展现层与数据持久层。
