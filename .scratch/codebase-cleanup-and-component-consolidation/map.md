# Feature Map: Codebase Cleanup and Component Consolidation

## Summary
收敛第四轮架构审查采纳的 Candidate 1、2、3：
- **Candidate 1**: 彻底剪除 6 个零生产调用的僵尸转发文件与过时兼容别名，测试对齐真实行为接口。
- **Candidate 2**: 消除 `ui/common` 对 `ui/browser` 的反向依赖倒置；将 4 个 30 行微模块内聚并入 `AudioTrackRow.kt`，建立自包含深模块。
- **Candidate 3**: 将 `MiniPlayer` 全面对齐 `PlaybackActions` 播控契约，消除冗余的 `DockedMiniPlayer` 空壳别名。

---

## Decisions
- **Clean Tree Invariant**: 生产环境中零调用的过渡期工厂与废弃类坚决物理清除，不保留无意义空转发。
- **Layering Discipline**: 基础公共层 `ui/common` 严禁反向引用上层特定业务页面 `ui/browser`。
- **Deep Component Locality**: 音轨行相关的小胶囊与缩略图逻辑内聚在 `AudioTrackRow.kt` 中统一维护，杜绝跨 6 文件繁琐跳转。

---

## Ticket Topology & Dependency Graph

```text
[01-purge-zombie-forwarding] (物理清除 6 个僵尸壳文件，对齐单测)
               │
[02-fix-reverse-dependencies-and-consolidate] (扶正包依赖，合并 common 微文件)
               │
               ▼
[03-align-miniplayer-to-playback-actions] (MiniPlayer 对齐契约 + 全量回归验证)
```

---

## Tickets Breakdown
1. `01-purge-zombie-forwarding-and-dead-code.md`: 剪除 6 个零调用僵尸文件与过时别名 (Status: resolved, Blocked by: None)
2. `02-fix-reverse-dependencies-and-consolidate-common-components.md`: 扶正 Equalizer 依赖并合并 ui/common 微模块 (Status: resolved, Blocked by: None)
3. `03-align-miniplayer-to-playback-actions.md`: MiniPlayer 对齐 PlaybackActions 并执行全量回归 (Status: resolved, Blocked by: 01, 02)

---

## Decisions-so-far
- **Ticket 01 Resolved**: 彻底移除 4 个冗余的 `*ViewModelFactory.kt` 转发壳、废弃的 `CueTextCache.kt` 及 `AudioTrackItemRowCompat.kt` 别名文件。单测表面对齐真实组件接口。
- **Ticket 02 Resolved**: 倒置依赖，将 `EqualizerWaveState` 和 `EqualizerStateHelper` 归入 `ui/common/`；将 `TrackThumbnailLeading`、`AudioQualityBadgePill`、`TrackTagPill` 及 `AudioTrackRowDefaults` 合并至 `AudioTrackRow.kt`（379 行，严格控制在 ≤380 行及 600 行熔断线内）。
- **Ticket 03 Resolved**: `MiniPlayer` 重构为直接消费 `PlaybackActions` 契约，彻底删除冗余包装 `DockedMiniPlayer`；全量单测通过，所有源码文件严格遵守 600 行单文件红线。
