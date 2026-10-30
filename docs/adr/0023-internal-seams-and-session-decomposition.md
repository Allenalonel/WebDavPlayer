# 0023: 播放会话内部接缝化与解构上帝类 (Internal Seams and Session Decomposition)

## Context

在先前的架构演进中，**ADR-0018** 确立了将系统所有播控意图（UI、通知栏 Action、蓝牙物理按键、Becoming Noisy 外放阻断）统一收拢至应用会话层（`MusicPlayerAppSession`）的核心决策。该决策彻底消除了底层引擎绕行、冷启动播放无声以及 Stop 资源泄漏等系统级缺陷。

然而，在后续特性的快速迭代中（涵盖 ADR-0014 CUE 虚拟分轨、ADR-0015 队列容错、ADR-0017 电池优化进程被杀横幅、ADR-0020 跨服务器歌单），系统出现了严重的**“引力黑洞效应（Gravitational Sink）”**：
- 架构规则极力保护底层引擎（ADR-0016 规定纯流式渲染，剥离队列与会话）与宿主服务（ADR-0019 规定仅管生命周期），被各层排挤出的业务逻辑全部单向汇集到 `MusicPlayerAppSessionImpl`；
- 在缺乏“内部接缝（Internal Seams）”约束的情况下，团队误将“对外接口窄小（深模块）”等同于“将所有实现写在单一文件内”；
- 导致 `MusicPlayerAppSessionImpl.kt` 膨胀至 **2,149 行**，在单一实现类中杂糅了会话持久化与反序列化、播放队列状态机算法、弱网与 404 故障自愈看门狗、CUE 时间轴换算、音频焦点与定时落盘等 9 大正交职责，沦为标准的**领域上帝类（God Class）**。

虽然外部接口小、杠杆高，但实现内部完全丧失了局部性（Locality），理解或修改局部逻辑的心智负担极其繁重。

## Decision

在坚守 ADR-0018 外部意图接缝的前提下，实施高内聚解耦，彻底打破上帝类：

### 1. 恪守 ADR-0018 外部公共契约（Zero External Breakdown）
- `MusicPlayerAppSession` 顶级领域接口**保持 100% 稳定，不增删或修改任何方法签名**；
- 外部所有调用方（Compose UI、`WebDavMediaSessionCallback`、`WebDavMediaService`、`PlaybackSessionHost`）调用代码**零破坏、零改动**。

### 2. 建立四大包级私有（`internal`）协作者（Internal Seams）
在 `domain.session` 包内建立四大职责高度专注的私有协作者类，对外部包完全不可见：

1. **`SessionPersistenceCoordinator`（会话持久化与冷恢复协调器）**：
   - 专职负责会话快照落盘与冷启动会话重建；
   - 内部封装 `PlaybackSessionStore` 读写、定时落盘任务（Periodic Flush）、服务器最后访问目录映射（`serverLastDirectories`）；
   - 独立承担普通会话还原（`restoreOrdinarySession`）与 CUE 虚拟分轨会话还原（`restoreVirtualTrackSession`）。
2. **`PlaybackQueueManager`（播放队列状态机管理器）**：
   - 专职负责运行时 `PlaybackQueue` 的集合拓扑与流转算法；
   - 集中仲裁下一曲/上一曲索引换算（`calculateNextQueueIndex` / `calculatePreviousQueueIndex`）；
   - 管控列表循环、单曲循环、随机洗牌（Shuffle）序列生成、单曲插队起播（`playNext` / `playNextAndAdvance`）及失效曲目防死循环跳跃机制（`failedTrackIdsInCurrentCycle`）。
3. **`PlaybackFaultTolerance`（流式播放故障熔断与看门狗）**：
   - 专职负责在线流媒体的自适应故障防护与容错状态；
   - 管控弱网 15 秒提示与 30 秒自动暂停看门狗（`bufferingWatchdogJob`）；
   - 集中处理 HTTP 404 文件丢失、服务器凭证失效与服务器不可达异常的判定与安全跳跃；
   - 仲裁不支持 HTTP Range 的重试对话框确认与忽略状态。
4. **`PlaybackMetadataCoordinator`（元数据富化与歌词协调器）**：
   - 专职负责响应式监听服务器元数据更新、歌词异步加载，以及队列音轨元数据同步缓存富化；
   - 彻底隔离元数据与歌词处理管道，杜绝逻辑反向浸润到核心调度器。

### 3. 主类纯化为轻量总调度器（Lean Orchestrator）
- `MusicPlayerAppSessionImpl` 不再直接编写持久化序列化代码或队列算法，转为作为轻量中枢协调器：
  - 持有底层的 `AudioPlayerEngine`、`VirtualTimelineEngine` 以及上述 4 个内部协作者；
  - 专职做意图派发与底层事件到 `PlayerSessionState` / `PlaybackProgress` 的响应式映射；
  - 核心实现类代码行数由 2,149 行削减至 **600 行以内**（目标 ~550 行）。

### 4. 确立内部接缝规范（Internal Seam Discipline）
- 正式在项目规范中设立铁律：深模块是对外接口属性，绝非单文件膨胀的借口；
- 核心业务实现类严格执行 **600 行熔断线**，承载 2 个以上正交职责时强制要求引入内部私有协作者。

## Consequences

- **消灭单点上帝类（Zero God Classes）**：2,149 行单一体解构为 4 个职责纯正的模块，每个模块平均在 300~500 行，内聚度极高。
- **高局部性与强测试性（High Locality & Direct Testability）**：持久化、队列算法、故障自愈各自拥有独立的单元测试切面，不再需要为了测一个洗牌逻辑而初始化完整的播放引擎或 DataStore。
- **零外部破坏与 100% 回归防护**：外部接口未受任何影响，现有的 3,000+ 行 `MusicPlayerAppSessionTest` 集成测试套件继续充当坚不可摧的安全网。
- **建立制度红线**：从根源上杜绝未来新增特性继续向会话层倾倒逻辑。
