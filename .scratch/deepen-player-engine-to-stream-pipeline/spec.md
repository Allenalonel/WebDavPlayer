# Spec: 播放引擎接缝深度收窄与会话意图统一 (Deepen Player Engine to Stream Pipeline & Unify Session Intent)

Status: completed

## Problem Statement

作为 WebDAV 在线流式音乐播放器的用户，在使用耳机线控、车载蓝牙或通知栏进行切歌时，偶发遇到切歌响应迟钝、随机播放顺序错乱或歌曲状态与播放进度不同步的问题。

在深入代码库架构时，底层的音频播放引擎暴露出了严重的概念撕裂与接缝浅显（Shallow Seam）：
底层播放引擎明明在物理上退化为只负责 1 首单轨（以及至多 1 首预热轨）的流式渲染管道，其对外接口却依然伪装成一个完整的“播放列表控制器”，向外暴露多曲目增删、索引寻道及恒为 null 的幽灵洗牌属性。
与此同时，来自通知栏和蓝牙线控的切歌指令没有直接通知主管队列的会话层，而是先调用底层播放引擎，再由底层播放引擎通过反向回调弹回会话层，形成了脆弱的双向回旋镖调用。这不仅破坏了单一真理来源，还迫使单元测试不得不编写大量虚假的列表测试桩来伪装底层的多曲目行为。

用户和开发者期望底层播放引擎是一个纯粹、高内聚、窄接口的流式音频渲染管道；所有的播放列表排序、随机算法、循环流转与 CUE 虚拟分轨调度统一由应用会话层集中管控；系统媒体通知与线控事件能够直接直达会话层，实现零延迟、零竞态、绝对可靠的播放控制。

## Solution

1. **加深播放引擎接缝（Deepen Player Engine to Stream Pipeline）**：
   彻底剥离底层播放引擎接口中所有关于“多曲目播放列表”的旧方法与概念（包括 `playTracks(List)`、`seekToTrack`、`removeTrack`、`insertTrack`、`loadedTracks`、`currentTrackIndex` 等）。将接口重塑为专注于当前单轨物理流起播（`loadTrack`）、下一轨 PCM 无缝预卷（`prerollTrack` / `clearPrerollTrack`）和当前轨元数据安全广播（`updateTrackMetadata`）的极简深模块。

2. **切歌意图直接派发至会话层（Direct Session Intent Dispatching）**：
   重构系统媒体会话（MediaSession）的回调处理逻辑，耳机线控、车载系统与通知栏派发的“下一首”、“上一首”切歌指令直接派发给应用会话层（`MusicPlayerAppSession`），由会话层综合判定当前是否为 CUE 虚拟分轨、是否触发弱网熔断（Premature EOF Guard / Duration Guard）以及当前循环模式，再以单向命令调用底层的 `loadTrack`。彻底拔除底层的反向回调接口（`SkipHandler`）。

3. **队列状态与洗牌算法收敛归一（Unified Playback Queue Source of Truth）**：
   彻底清除底层播放引擎中恒为 null 的幽灵属性 `shufflePermutation`。随机播放算法与置换顺序 100% 归属于 `PlaybackQueue` 与 `MusicPlayerAppSession`，会话层直接基于自身队列状态推演下一曲索引，消灭双重状态机冲突。

4. **全面净化测试表面（Purify Testing Surface）**：
   将测试使用的仿真播放引擎（`FakeAudioPlayerEngine`）大幅瘦身为仅记录单轨状态与流式进度的简单测试桩，移除所有关于曲目列表管理、反向拦截和洗牌模拟的代码。让测试跨越的接缝与生产环境完全同一（Strictly abide by "The interface is the test surface"）。

## User Stories

1. 作为移动端音乐收听者，当我点击播放目录中的某一首音频曲目时，我希望播放器能够瞬时起播该曲目，并且底层播放内核专注于当前音频流的稳定解码，不会因加载整个目录列表而造成内存抖动或初始化延迟。
2. 作为使用车载蓝牙或智能手表的用户，当我通过方向盘按键或手表切歌按键触发“下一首”时，我希望切歌指令能够直接命中播放会话层并立即响应，不再出现任何因底层反向回调转发导致的指令丢失或延迟。
3. 作为使用车载蓝牙或智能手表的用户，当我通过方向盘按键触发“上一首”时，如果当前播放时间小于 3 秒，我希望会话层能够立即平滑切换至上一曲；如果当前播放时间大于 3 秒，能够精确重新起播本曲。
4. 作为音乐发烧友，当开启“列表循环”且曲目播放到最后一首的自然末尾时，我希望会话层能够结合时长熔断守卫（Duration Guard）确认完播，并直接指令底层流管道无缝切换至当前目录第一首（Folder Ring Playback）。
5. 作为音乐发烧友，当开启“单曲循环”模式时，当歌曲播放完毕，我希望底层流管道在会话层指挥下从头重新起播该单曲，而不会触发任何跨曲目跳跃。
6. 作为音乐发烧友，当开启“随机播放”模式时，我希望切歌顺序完全遵循会话层生成的确定性洗牌序列，每一首歌在列表循环内均只播一次，绝不依赖底层播放引擎的虚假或随机索引。
7. 作为收听 CUE 整轨无损音频（Virtual Track）的用户，当我在通知栏或线控上点击“下一首”时，我希望会话层能够无缝寻道至下一个虚拟分轨时间戳并更新通知元数据，底层物理流保持持续连接不中断。
8. 作为收听 CUE 整轨无损音频的用户，当分轨自然跨越边界时，我希望底层物理流零停顿零爆音，会话层在后台响应式更新分轨标题与艺术家信息，且不会向底层播放引擎下发多余的重建命令。
9. 作为处于弱网环境下的用户，当发生网络闪断导致音频流中途意外截断时，我希望会话层的时长守卫能牢牢拦截该意外事件，底层流管道在断点处耐受重连，而绝不被底层播放引擎自主跳过到下一首。
10. 作为追求极致无缝听感的用户，在网络良好且当前歌曲播放至最后 10 秒时，我希望会话层能够按需向底层管道注入预卷指令（`prerollTrack`），底层提前加载下一曲 PCM 缓冲并在交界处实现零静音衔接。
11. 作为用户，当下一首歌曲已经处于预卷缓冲中但我突然手动点击“暂停”或“切歌”时，我希望会话层能立即指示底层清空预卷流（`clearPrerollTrack`），带宽与资源瞬间释放，操作零滞后。
12. 作为用户，当后台完成内嵌封面或高清标签的非打扰元数据富化（Non-Disruptive Metadata Enrichment）时，我希望底层流管道能够通过单一轻量接口接收更新，且严禁导致音频流重新缓冲或进度跳变。
13. 作为用户，当我拖拽进度条进行 Seek 时，我希望指令直接穿透至底层流管道的物理时间戳，精准到位，不与队列推进状态发生任何竞态。
14. 作为用户，当我调整系统音量或播放器音量时，底层流管道能够即时平滑调节音频增益，保证声音响度自然顺滑。
15. 作为应用维护者，当我编写针对播放会话的单元测试时，我希望测试桩无需伪装 200 行复杂的播放列表管理与虚构回调，而是仅需通过极简的流状态断言即可验证复杂的切歌与熔断逻辑。
16. 作为应用维护者，当我为底层播放引擎编写集成测试时，我只需关注当前媒体源的创建、预热与解码渲染，无需再排查多曲目列表增删引发的边界越界 Bug。

## Implementation Decisions

### 1. 播放引擎接口收窄为流式播放管道 (AudioPlayerEngine Seam Deepening)
- **从接口中移除多曲目概念**：
  彻底废弃并移除 `playTracks(server, tracks, startIndex, ...)`、`seekToTrack(index, pos)`、`removeTrack(index)`、`insertTrack(index, server, track)`。
- **确立纯流渲染接口**：
  ```kotlin
  interface AudioPlayerEngine {
      val playbackState: StateFlow<PlaybackState>
      val currentPositionMs: StateFlow<Long>
      val durationMs: StateFlow<Long>
      val bufferedPositionMs: StateFlow<Long>

      fun loadTrack(server: WebDavServer, track: AudioTrack, startPositionMs: Long = 0L)
      fun prerollTrack(server: WebDavServer, track: AudioTrack)
      fun clearPrerollTrack()
      fun updateTrackMetadata(track: AudioTrack)

      fun play()
      fun pause()
      fun seekTo(positionMs: Long)
      fun stop()
      fun release()
      fun setVolume(volume: Float)
      fun getVolume(): Float
  }
  ```
- **消除幽灵属性与模式管理**：
  删除 `val shufflePermutation: List<Int>?`。
  删除 `val currentTrackIndex: StateFlow<Int>`。
  删除 `val playbackMode: StateFlow<PlaybackMode>` 及 `fun setPlaybackMode(mode: PlaybackMode)`。底层播放管道无需知晓上层的循环模式。

### 2. 媒体会话回调单向化与解耦 (Unify MediaSession Intent Flow)
- **重塑媒体会话切歌回调**：
  重构 `WebDavMediaSessionCallback`，使其依赖 `MusicPlayerAppSession`（或专职的会话调度接口），当收到 `onSkipToNext()` 与 `onSkipToPrevious()` 时，直接派发调用 `musicPlayerAppSession.skipToNext()` 与 `musicPlayerAppSession.skipToPrevious()`。
- **移除反向切歌拦截器**：
  彻底删除 `AudioPlayerEngine.SkipHandler` 内部接口及 `setSkipHandler` 方法。
  删除底层实现 `Media3AudioPlayerEngine` 和 `PlaybackSessionHost` 中对 `SkipHandler` 的持有与代理。

### 3. 会话层与播放队列职责闭环 (Session-Managed Progression Consolidation)
- **独占队列索引与洗牌序列**：
  `MusicPlayerAppSessionImpl` 全权掌控当前播放列表 `PlaybackQueue`，洗牌序列由 `PlaybackQueue` 自身生成，切歌时直接由会话层判定目标音轨，并将计算出的唯一单轨下发给底层 `playerEngine.loadTrack(server, targetTrack, startPos)`。
- **预卷与弱网抑制单向驱动**：
  在满足预卷条件时，会话层调用 `playerEngine.prerollTrack(server, nextTrack)`；在发生卡顿、切歌或暂停时，直接下发 `playerEngine.clearPrerollTrack()`。底层无需再维护 `prerollTargetTrackIndex` 索引变量。

### 4. 装饰器与前台服务宿主对齐 (PlaybackSessionHost Adaptation)
- **保持前台服务提升透明**：
  `PlaybackSessionHost` 作为 `AudioPlayerEngine` 的透明前台服务装饰器，全面对齐收窄后的 `AudioPlayerEngine` 接口，在 `loadTrack` 与 `play` 时保持 `ensureServiceStarted()` 逻辑，并剥离所有的多曲目代理方法。

## Testing Decisions

### 1. 优质测试的黄金法则
- **仅测试外部行为，杜绝耦合私有实现**：测试仅通过 `AudioPlayerEngine` 的收窄接口和 `MusicPlayerAppSession` 的公共状态流进行断言，不探测或断言其内部的私有对象或协程调度器。
- **生产与测试跨越同一接缝**：测试桩（Fake）必须精确映射真实深模块的精简语义，绝不在测试桩中编写额外的多曲目管理逻辑。

### 2. 测试模块与范围
- **`AudioPlayerEngineTest` / `Media3AudioPlayerEngineTest`**：
  验证 `loadTrack` 正确配置底层 ExoPlayer 媒体源与起播位置；验证 `prerollTrack` 能够按需添加预卷源并在 `clearPrerollTrack` 时安全移除；验证元数据无损更新。
- **`MusicPlayerAppSessionTest`**：
  验证会话层在顺序播放、单曲循环、随机播放及 CUE 虚拟分轨模式下的推进算法，验证其直接以单向命令驱动底层 `playerEngine.loadTrack` 与 `prerollTrack`。
- **`PlaybackQueueTest`**：
  针对纯数据模型 `PlaybackQueue` 验证洗牌顺序（`shuffleOrder`）计算与边界溢出保护。
- **`WebDavMediaSessionCallbackTest`**：
  验证系统通知与蓝牙回调直接触发会话层切歌逻辑，消灭任何死循环或反向拦截漏调。

### 3. 既有参考 (Prior Art)
- 参照 `.scratch/session-managed-queue-and-fault-tolerant-streaming/spec.md` 中的单轨调度与弱网断点续传测试标准。
- 参照 `docs/adr/0011-deepen-track-metadata-repository-and-internalize-resolver.md` 与 `docs/adr/0016-deepen-player-engine-to-stream-rendering-pipeline.md`。

## Out of Scope

- **UI 界面组件变更**：MiniPlayer、FullPlayerView、歌词页及目录浏览界面的布局和视觉交互不在本次修改范围内。
- **持久化磁盘缓存引入**：严格恪守 ADR-0002 纯在线流式零磁盘沉淀设计，不在此次重构中引入本地音频文件持久化。
- **编解码器与数据源改动**：ExoPlayer 现有的 WMA/ASF 解码扩展、OkHttp 客户端连接池及 Range 重试机制保持现有高韧性实现不变。

## Further Notes

- 本规范完全承接并固化 ADR-0016 决策结论。
- 重构完成后，`AudioPlayerEngine` 相关的接口代码行数与测试桩维护成本将减少 50% 以上，为后续 CUE 虚拟分轨进一步向数据仓储层收拢提供极简坚实的底层流管道支撑。
