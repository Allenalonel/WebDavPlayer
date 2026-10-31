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

1. `01-extract-playback-timeline-coordinator.md`: 提取 `PlaybackTimelineCoordinator` 内部协作者，会话类削减至 ≤400 行 (Status: open, Blocked by: None)
2. `02-establish-app-container-and-consolidate-factories.md`: 建立 `AppContainer` 统一容器，消除 4 个 Factory 样板与影子依赖 (Status: open, Blocked by: None)
3. `03-aggregate-playback-actions-in-full-player.md`: 提炼 `PlaybackActions` 播控意图契约，参数从 17 缩减至 5 (Status: open, Blocked by: None)
4. `04-migrate-cue-virtual-track-row-and-verify.md`: 迁移 CUE 虚拟分轨至通用 `AudioTrackRow`，执行全量回归与 600 行红线检查 (Status: open, Blocked by: 01, 02, 03)
