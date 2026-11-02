# Local Issue Tracker (.scratch)

本项目使用基于本地 Markdown 的 Issue Tracker 管理迭代任务与规范。详见 [`docs/agents/issue-tracker.md`](../docs/agents/issue-tracker.md)。

## 目录组织约定

- **活跃任务（Active Features）**：直接位于 `.scratch/<feature-slug>/`。
  - Spec 规范：`.scratch/<feature-slug>/spec.md`（或 Wayfinder 地图 `map.md`）
  - 工单切片：`.scratch/<feature-slug>/issues/<NN>-<slug>.md`
- **归档任务（Archived Features）**：所有 Tickets 状态变为 `resolved` / `completed` 且合并验证后，整体移入 [`.scratch/archive/`](archive/)。
- **永久领域认知**：已结项特性中沉淀的长期领域模型与架构决策，记录在根目录 [`GLOSSARY.md`](../GLOSSARY.md)、[`docs/adr/`](../docs/adr/) 和 [`CODING_STANDARDS.md`](../CODING_STANDARDS.md)。

---

## 活跃任务 (Active)

> 当前无进行中的活跃任务。工作区处于 Clean 状态，可直接通过 `/to-spec`、`/wayfinder` 或 `/to-tickets` 发起新特性开发。

---

## 已归档历史特性 (Archived)

所有历史工单均已全量结项并通过验收，归档保存在 [`archive/`](archive/) 目录下：

| 特性 / 优化项 Slug | 描述 / 规范主题 | 工单数 | 状态 | 归档路径 |
| :--- | :--- | :---: | :---: | :--- |
| `consolidate-satellites-and-deepen-domain-locality` | 消除卫星碎片文件、收敛测试表面与深化领域模型局部性 | 5 | Completed | [`archive/consolidate-satellites-and-deepen-domain-locality/`](archive/consolidate-satellites-and-deepen-domain-locality/) |
| `network-awareness-and-auto-resumption` | 系统级网络连通性感知、弱网挂起断点无感自愈续播与离线曲目静默健康恢复 | 4 | Completed | [`archive/network-awareness-and-auto-resumption/`](archive/network-awareness-and-auto-resumption/) |
| `playback-engine-and-cross-server-resilience` | 播放引擎内核深化、跨服务器凭据原子同步与前台服务生命周期硬化 | 4 | Completed | [`archive/playback-engine-and-cross-server-resilience/`](archive/playback-engine-and-cross-server-resilience/) |
| `webdav-protocol-and-artwork-resilience` | WebDAV 路径 RFC 3986 百分号解码、外置封面内存预过滤与 Digest 认证加速 | 4 | Completed | [`archive/webdav-protocol-and-artwork-resilience/`](archive/webdav-protocol-and-artwork-resilience/) |
| `core-webdav-music-player` | 基础 WebDAV 音乐播放器核心架构与播放链路 | 10 | Completed | [`archive/core-webdav-music-player/`](archive/core-webdav-music-player/) |
| `modern-md3-player-ui` | Modern Material Design 3 UI 与导航框架 | 5 | Completed | [`archive/modern-md3-player-ui/`](archive/modern-md3-player-ui/) |
| `codebase-stability-and-architecture-optimization` | 架构稳定性、LRU 缓存与资源管理优化 | 5 | Completed | [`archive/codebase-stability-and-architecture-optimization/`](archive/codebase-stability-and-architecture-optimization/) |
| `architecture-deepening-and-simplification` | SWR 数据流深化与 ViewModel 瘦身 | 3 | Completed | [`archive/architecture-deepening-and-simplification/`](archive/architecture-deepening-and-simplification/) |
| `playback-pipeline-and-session-deepening` | 双轨响应式播放管线与 Session 统一宿主 | 4 | Completed | [`archive/playback-pipeline-and-session-deepening/`](archive/playback-pipeline-and-session-deepening/) |
| `p0-lifecycle-and-playback-credential-fix` | 播放凭据动态装配与前台服务生命周期加固 | 4 | Completed | [`archive/p0-lifecycle-and-playback-credential-fix/`](archive/p0-lifecycle-and-playback-credential-fix/) |
| `codebase-de-duplication-and-architecture-deepening` | 领域模型去重与 MediaItem 装配收拢 | 5 | Completed | [`archive/codebase-de-duplication-and-architecture-deepening/`](archive/codebase-de-duplication-and-architecture-deepening/) |
| `md3-immersive-ui-refinement` | 沉浸式 Edge-to-Edge、MiniPlayer 胶囊与主题优化 | 7 | Completed | [`archive/md3-immersive-ui-refinement/`](archive/md3-immersive-ui-refinement/) |
| `p1-cover-and-lyrics-refinement` | 封面回退管线与双语 LRC 歌词模型 | 5 | Completed | [`archive/p1-cover-and-lyrics-refinement/`](archive/p1-cover-and-lyrics-refinement/) |
| `cache-invalidation-and-refresh-resilience` | 封面磁盘自愈与目录刷新生命周期加固 | 5 | Completed | [`archive/cache-invalidation-and-refresh-resilience/`](archive/cache-invalidation-and-refresh-resilience/) |
| `metadata-repository-deepening` | 缩略图自愈与歌词元数据仓储收拢 | 4 | Completed | [`archive/metadata-repository-deepening/`](archive/metadata-repository-deepening/) |
| `binary-thinning-and-packaging-seam` | R8 混淆、图标裁剪与 64 位 ABI 瘦身 | 4 | Completed | [`archive/binary-thinning-and-packaging-seam/`](archive/binary-thinning-and-packaging-seam/) |
| `cue-virtual-tracks-and-gapless-playback` | CUE 虚拟分轨与流式无缝衔接引擎 | 5 | Completed | [`archive/cue-virtual-tracks-and-gapless-playback/`](archive/cue-virtual-tracks-and-gapless-playback/) |
| `session-managed-queue-and-fault-tolerant-streaming` | 会话层切歌管控与弱网容错流式管线 | 5 | Completed | [`archive/session-managed-queue-and-fault-tolerant-streaming/`](archive/session-managed-queue-and-fault-tolerant-streaming/) |
| `deepen-player-engine-to-stream-pipeline` | 播放引擎接缝深度收窄与会话意图统一 | 5 | Completed | [`archive/deepen-player-engine-to-stream-pipeline/`](archive/deepen-player-engine-to-stream-pipeline/) |
| `resilient-background-playback-and-battery-whitelist` | 弹性后台保活、双硬件锁与厂商电池白名单 | 5 | Completed | [`archive/resilient-background-playback-and-battery-whitelist/`](archive/resilient-background-playback-and-battery-whitelist/) |
| `unify-system-playback-intent-routing` | 统一系统级播控意图接缝至应用会话层 | 3 | Completed | [`archive/unify-system-playback-intent-routing/`](archive/unify-system-playback-intent-routing/) |
| `duration-calibration-and-fault-tolerant-reconnection` | 物理时长校准与弱网断线重连仲裁 | 4 | Completed | [`archive/duration-calibration-and-fault-tolerant-reconnection/`](archive/duration-calibration-and-fault-tolerant-reconnection/) |
| `becoming-noisy-and-audio-route-resilience` | 音频防意外外放保护与规范化重采样 | 3 | Completed | [`archive/becoming-noisy-and-audio-route-resilience/`](archive/becoming-noisy-and-audio-route-resilience/) |
| `system-audio-focus-and-route-resilience` | 系统音频焦点仲裁与全流转防外放自愈 | 5 | Completed | [`archive/system-audio-focus-and-route-resilience/`](archive/system-audio-focus-and-route-resilience/) |
| `stream-pipeline-and-host-decoupling` | 媒体会话上提与 Session Host 解耦 | 4 | Completed | [`archive/stream-pipeline-and-host-decoupling/`](archive/stream-pipeline-and-host-decoupling/) |
| `streamline-playback-pipeline-and-consolidate-session-host` | 播放管线精简与硬件锁收拢至会话宿主 | 4 | Completed | [`archive/streamline-playback-pipeline-and-consolidate-session-host/`](archive/streamline-playback-pipeline-and-consolidate-session-host/) |
| `industry-benchmark-and-feature-gap-analysis` | 业界标杆竞品分析与功能差距研究调研报告 | 0 | Completed | [`archive/industry-benchmark-and-feature-gap-analysis/`](archive/industry-benchmark-and-feature-gap-analysis/) |
| `global-search-and-cross-server-playlists` | 全局即时搜索与跨服务器自定义本地歌单 | 5 | Completed | [`archive/global-search-and-cross-server-playlists/`](archive/global-search-and-cross-server-playlists/) |
| `deepen-directory-and-search-repositories` | 搜索历史与目录仓储深化收拢 | 3 | Completed | [`archive/deepen-directory-and-search-repositories/`](archive/deepen-directory-and-search-repositories/) |
| `deepen-playback-and-repositories` | 播放引擎深化与服务删除级联清理 | 4 | Completed | [`archive/deepen-playback-and-repositories/`](archive/deepen-playback-and-repositories/) |
| `ui-component-seams-and-file-budget-compliance` | UI 屏幕组件拆解与代码行预算合规 | 3 | Completed | [`archive/ui-component-seams-and-file-budget-compliance/`](archive/ui-component-seams-and-file-budget-compliance/) |
| `decompose-session-god-class` | Session 上帝类拆解为四大聚焦协作者 | 4 | Completed | [`archive/decompose-session-god-class/`](archive/decompose-session-god-class/) |
| `deepen-cue-sheet-resolver` | 深度 CUE 解析器封装与网络客户端泄漏阻断 | 3 | Completed | [`archive/deepen-cue-sheet-resolver/`](archive/deepen-cue-sheet-resolver/) |
| `codebase-cleanup-and-component-consolidation` | 僵尸代码清理与公共组件反向依赖消除 | 3 | Completed | [`archive/codebase-cleanup-and-component-consolidation/`](archive/codebase-cleanup-and-component-consolidation/) |
| `unified-audio-track-presentation` | 统一 AudioTrackItemRow 组件接缝 | 4 | Completed | [`archive/unified-audio-track-presentation/`](archive/unified-audio-track-presentation/) |
| `architecture-hardening-and-budget-compliance` | 时间轴协调器抽取与 App 容器收拢 | 4 | Completed | [`archive/architecture-hardening-and-budget-compliance/`](archive/architecture-hardening-and-budget-compliance/) |
| `navigation-purification-and-search-indexing` | 主导航协调器纯净化与后台静默索引 | 3 | Completed | [`archive/navigation-purification-and-search-indexing/`](archive/navigation-purification-and-search-indexing/) |
| `prototype` | HTML UI 与应用图标交互设计原型集 | 0 | Archived | [`archive/prototype/`](archive/prototype/) |
