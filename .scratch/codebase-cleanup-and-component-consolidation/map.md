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
1. `01-purge-zombie-forwarding-and-dead-code.md`: 剪除 6 个零调用僵尸文件与过时别名 (Status: open, Blocked by: None)
2. `02-fix-reverse-dependencies-and-consolidate-common-components.md`: 扶正 Equalizer 依赖并合并 ui/common 微模块 (Status: open, Blocked by: None)
3. `03-align-miniplayer-to-playback-actions.md`: MiniPlayer 对齐 PlaybackActions 并执行全量回归 (Status: open, Blocked by: 01, 02)
