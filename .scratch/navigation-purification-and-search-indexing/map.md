# Feature Map: Navigation Seam Purification and Search Indexing

## Summary
落地第三轮审查采纳的 Candidate 3（顶层导航纯粹化）与 Candidate 4（曲库搜索索引化与 CUE 泄漏清除）：
- **Candidate 3**: 剪除 `MainNavigationCoordinator` 中的 `togglePlayPause` 与 `skipToNext` 播控透传存根，导航类 100% 专职于路由状态，彻底剥离对 `MusicPlayerAppSession` 的依赖。
- **Candidate 4**: 优化 `LibrarySearchRepositoryImpl` 检索管道，消除全量 JSON 盲目反序列化开销，彻底封堵对 `CueAssociationHelper` 私有类的跨包侵入。

---

## Decisions
- **Decoupled Navigation**: 导航协调器不承载任何播控动作，所有播控意图由 `PlaybackActions` 契约独家管理。
- **Encapsulated CUE Association**: CUE 虚拟分轨与物理音频关联严格由 `CueSheetResolver` 封装，曲库搜索仓储不再直接操作底层启发式辅助类。

### Decisions So Far
- **01 Resolved**: 移除 `MainNavigationCoordinator` 的 `musicPlayerAppSession` 依赖及 `togglePlayPause`/`skipToNext` 存根，导航状态机 100% 专职于路由管理。详见 [01-purify-main-navigation-coordinator.md](issues/01-purify-main-navigation-coordinator.md)。
- **02 Resolved**: 将 `CueAssociationHelper` 设为 `internal` 并消除跨包导入；在 `LibrarySearchRepositoryImpl` 中引入目录瞬态 LRU 缓存与匹配前置过滤，精简至 242 行。详见 [02-deepen-search-indexing-and-encapsulate-cue.md](issues/02-deepen-search-indexing-and-encapsulate-cue.md)。

---

## Ticket Topology & Dependency Graph

```text
[01-purify-main-navigation-coordinator] (剪除导航协调器播控存根，剥离会话依赖)
                    │
[02-deepen-search-indexing-and-encapsulate-cue] (优化搜索管道，封堵 CUE 跨包泄漏)
                    │
                    ▼
[03-verify-search-and-navigation-suite] (全量测试套件回归与 600 行红线检查)
```

---

## Tickets Breakdown
1. `01-purify-main-navigation-coordinator.md`: 纯粹化导航协调器，剥离会话依赖与冗余播控方法 (Status: resolved, Blocked by: None)
2. `02-deepen-search-indexing-and-encapsulate-cue.md`: 深入搜索管道，收敛 CUE 关联算法至 CueSheetResolver (Status: resolved, Blocked by: None)
3. `03-verify-search-and-navigation-suite.md`: 运行全量单元测试，验证搜索性能与导航回归 (Status: open, Blocked by: 01, 02)
