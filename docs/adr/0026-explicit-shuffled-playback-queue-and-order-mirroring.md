# 0026: 显式洗牌播放队列与原序镜像 (Explicit Shuffled Playback Queue and Order Mirroring)

## Context

在音乐播放器的随机播放（Shuffle Mode）功能演进中，存在长期困扰的后台切歌脱节缺陷及与主流播放器心智割裂的问题：

1. **后台/锁屏切歌 UI 与实际播放曲目脱节 Bug**：
   - 当曲目接近尾声（最后 10 秒）时，系统的预加载机制（`checkAndTriggerAdaptivePreroll`）计算下一首并向底层的 ExoPlayer 装填预卷流（Preroll Stream）；
   - 当歌曲自然播放完毕触发 `trackTransitions` 切歌事件时，上层协调器重新调用了队列计算；
   - 在历史实现中，`PlaybackQueue` 依赖不稳定的 `shuffleOrder: List<Int>` 映射表，一旦映射表未初始化或未命中，立即退化为每一步即时掷骰子（`randomOrNull()`）；
   - 这导致底层 ExoPlayer 物理播放的是预加载的 Track A，而上层事件却重新掷出了 Track B 并写入 UI 状态，导致息屏切歌后底部栏与耳机听到的音乐完全脱节；拖拽进度条到末尾时因 Seek 强制清除了预加载缓冲而碰巧掩盖了此缺陷。

2. **随机播放模型与主流工业标准割裂**：
   - **缺乏前进/回溯对称性**：历史逻辑按“下一首”跳到新歌后，再按“上一首”又是一次即时随机，无法返回刚刚听过的歌曲；
   - **播放队列界面（PlaybackQueueBottomSheet）心智割裂**：用户打开队列抽屉时，呈现的永远是原目录/专辑顺序，用户无法感知接下来会播放什么（Up Next）；
   - **循环随机缺乏自然重新洗牌机制**：列表末尾未建立确定性的 Reshuffle 边界。

## Decision

落实业界主流播放器（如 Spotify / Apple Music）成熟的**显式洗牌队列（Explicit Shuffled Playback Queue / Deck of Cards Permutation）**模型，彻底废弃不稳定的动态索引映射：

### 1. 领域模型升级为双轨镜像 (`PlaybackQueue`)
- `PlaybackQueue` 包含 `tracks: List<AudioTrack>`（活跃队列）与 `originalTracks: List<AudioTrack>`（原序镜像）：
  - 列表循环/单曲循环模式下：`tracks` 为原始目录/歌单顺序，`originalTracks` 为空；
  - 随机播放模式下：`tracks` 直接呈现为**被打乱后的真实待播清单**，`originalTracks` 完整保留打乱前的原始序列；
- **开启随机（`toShuffled()`）**：正在播放的曲目不被中断并固定置于第 0 位（`currentIndex = 0`），其余剩余曲目打乱拼接在后；
- **关闭随机（`toUnshuffled()`）**：纯内存瞬时恢复 `originalTracks`，并根据当前音轨 ID 秒级重新寻址其在原序中的位置更新 `currentIndex`，零 IO 损耗且平滑无感知；
- **切歌与回溯确定性**：`getNextIndex()` / `getPreviousIndex()` 在随机模式下退化为纯粹的 `currentIndex + 1` 与 `currentIndex - 1`，彻底消灭任何 `randomOrNull()` 掷骰子逻辑。

### 2. 预加载与切歌无缝闭环 (Deterministic Preroll & Transition)
- 预加载阶段取 `tracks[currentIndex + 1]` 塞入播放器引擎；
- 播放自然结束触发切歌事件时，直接递增指针切换至 `tracks[currentIndex + 1]`，二者具备 100% 的数学一致性，彻底根绝锁屏切歌 UI 脱节 Bug；
- **整轮末尾自动重新洗牌（Reshuffle）**：当在随机模式下播放至列表最后一首（`currentIndex >= tracks.lastIndex`）需要循环起播时，以当前列表重新洗牌并重置 `currentIndex = 0`，开启不重复的新一轮随机。

### 3. 插队与 CUE 虚拟分轨平级支持
- **临时插队（Play Next）双向同步**：在随机模式下执行“下一首播放”，新曲目不仅插入在 `tracks` 的 `currentIndex + 1`，同时同步插入在 `originalTracks` 当前播放曲目的后一位；中途切回顺序播放，插队歌曲依旧紧随其后；
- **CUE 虚拟分轨**：虚拟分轨在进入播放队列时平级展开为独立音轨实体，与普通音频完全平级地参与洗牌。

### 4. 会话持久化与冷启动所见即所得
- 扩充 `PlaybackSessionData` 与 `DataStorePlaybackSessionStore`，将 `originalTracks` 纳入持久化字段；
- 冷启动恢复会话时完整复原打乱后的待播列表与原序镜像，保证进程被杀后无缝恢复，队列抽屉中看到的待播清单分毫不差。

### 5. 彻底覆盖与清理老旧残留代码 (Zero Legacy Code)
- 彻底删除 `PlaybackQueue` 中的 `shuffleOrder: List<Int>`、`withGeneratedShuffleOrder`、`withShuffleOrder` 以及 `randomOrNull()` 分支；
- 彻底删除会话层与管理器中的 `setShuffleOrder` 接口与内部方法；
- 全面更新单元测试用例，完全迁移至显式洗牌队列断言。

## Consequences

- **100% 根除后台/锁屏切歌脱节缺陷**：预加载曲目与切歌事件在洗牌列表中完全对齐，不再受随机数发生器二次计算干扰；
- **主流工业级体验**：播放队列界面（PlaybackQueueBottomSheet）真正成为所见即所得的 Up Next 列表，前进与回溯具备严格对称性；
- **零代码残留与极简接缝**：彻底拔除老旧索引计算黑魔法与 fallback 分支，所有模式下的流转均遵循统一的线性索引算式；
- **完全兼容冷启动与临时插队**：会话持久化与双列表镜像设计保证了无论如何切换模式或重启应用，曲目数据均不丢失。
