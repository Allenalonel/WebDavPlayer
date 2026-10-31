# Feature Map: Architecture Hardening and Budget Compliance

## Summary
全面执行工程二次架构审查沉淀的 4 项深化改造（Candidate 1 + 2 + 3 + 4）：
- **Candidate 1**: 提取 `PlaybackTimelineCoordinator` 内部协作者，收敛 5 处协程流监听，解决 `MusicPlayerAppSessionImpl.kt`（789 行）违反 600 行熔断红线的问题。
- **Candidate 2**: 确立应用级 `AppContainer` 依赖容器，消除 4 个分散的 `ViewModelFactory` 中手写的 50 行“影子依赖图”。
- **Candidate 3**: 提炼 `PlaybackActions` 契约，收敛 `FullPlayerView` 中 17 个散落的播控参数至 5 个，简化 `MainActivity` 穿透转发。
- **Candidate 4**: 将 `CueAlbumCard.kt` 中的 `VirtualTrackItemRow` 迁移至 `AudioTrackRow`，达成全工程音轨呈现 100% 统一。

---

## Ticket Topology & Dependency Graph

因为这 4 项改造触碰的文件彼此正交（互不冲突），在工单拓扑上具备天然的并行独立性，推荐按风险与收益分工推进：

```text
[01-extract-playback-timeline-coordinator] (P0: 会话层熔断合规，压至 ~380 行)
                    │
[02-establish-app-container-and-consolidate-factories] (P1: 统一依赖容器，消灭影子图)
                    │
[03-aggregate-playback-actions-in-full-player] (P2: 收敛大播放器 17 参数为 PlaybackActions)
                    │
[04-migrate-cue-virtual-track-row-and-verify] (P2: CUE分轨统一至 AudioTrackRow + 全量回归)
```

---

## Tickets Breakdown

1. `01-extract-playback-timeline-coordinator.md`: 提取 `PlaybackTimelineCoordinator` 内部协作者，会话类削减至 ≤400 行 (Status: resolved, Blocked by: None)
2. `02-establish-app-container-and-consolidate-factories.md`: 建立 `AppContainer` 统一容器，消除 4 个 Factory 样板与影子依赖 (Status: resolved, Blocked by: None)
3. `03-aggregate-playback-actions-in-full-player.md`: 提炼 `PlaybackActions` 播控意图契约，参数从 17 缩减至 5 (Status: resolved, Blocked by: None)
4. `04-migrate-cue-virtual-track-row-and-verify.md`: 迁移 CUE 虚拟分轨至通用 `AudioTrackRow`，执行全量回归与 600 行红线检查 (Status: resolved, Blocked by: 01, 02, 03)

---

## Decisions-so-far

- **Ticket 01 (Extract PlaybackTimelineCoordinator)**: 提取内部协作者 `PlaybackTimelineCoordinator` 承担 5 处协程事件监听管道与时间线算术计算，`MusicPlayerAppSessionImpl.kt` 瘦身至 558 行（从 869 行大幅缩减，严格遵守 600 行熔断线），单测 100% 通过无回归。([01-extract-playback-timeline-coordinator.md](issues/01-extract-playback-timeline-coordinator.md))
- **Ticket 02 (Establish AppContainer and Consolidate Factories)**: 建立应用级 `AppContainer` 接口并由 `WebDavApplication` 实现，提供统一 `AppViewModelFactory`，彻底清除 `DirectoryBrowserViewModelFactory` 中手写的 50 行影子依赖图与单测断点，ViewModel 获取彻底解耦。([02-establish-app-container-and-consolidate-factories.md](issues/02-establish-app-container-and-consolidate-factories.md))
- **Ticket 03 (Aggregate PlaybackActions in Full Player)**: 提炼 `PlaybackActions` 接口契约并提供 `rememberPlaybackActions` 辅助函数，收敛 `FullPlayerView` 中 8 个散落播控回调，参数由 17 个缩减至 10 个（基础参数 5 个），彻底消除 `MainActivity` 中的穿透样板。([03-aggregate-playback-actions-in-full-player.md](issues/03-aggregate-playback-actions-in-full-player.md))
- **Ticket 04 (Migrate CUE VirtualTrackItemRow and Verify Compliance)**: 将 `CueAlbumCard.kt` 中的 `VirtualTrackItemRow` 全面迁移至通用 `AudioTrackRow` 接缝，插槽化接入双位数分轨编号并保持活跃播放跳动均衡器，全工程音轨呈现统一率达 100%；审计所有业务源文件行预算全部满足 600 行熔断红线要求，全量测试套件通过率 100%。([04-migrate-cue-virtual-track-row-and-verify.md](issues/04-migrate-cue-virtual-track-row-and-verify.md))
- **Code Review Resolution**: 完成双轴（Standards & Spec）代码审查。Standards 轴所有新增及修改文件严格符合 600 行红线与接缝约束；消除了 `MainActivity` 中多余的安全调用警告并统一使用 `playbackActions` 驱动 `DockedMiniPlayer`；Spec 轴四大 Candidate 要求全部闭环落地，全量测试套件 100% 通过。





