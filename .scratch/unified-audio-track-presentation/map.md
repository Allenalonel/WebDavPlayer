# Feature Map: Unified Audio Track Presentation Seam

## Summary
阶段二（UI 表现层）：统一跨视窗音轨条目呈现接缝（Candidate 5）。
提炼高内聚、深实现的通用 `AudioTrackRow` 组件，消除媒体库、歌单、搜索结果与播放队列底抽 4 个核心视窗中的近 1,800 行 UI 样板代码，杜绝排版和交互手势不一致。

## Key Architectural Decisions
- **Slot-Based Component Seam**:
  在 `ui/common/AudioTrackRow.kt` 建立统一的 Slot 架构，内置跑马灯、副标题拼接、高解析度角标与自适应等化器跳动脉冲。
- **Gesture Isolation**:
  通用 `AudioTrackRow` 专注于内部视觉与标准点击/菜单动作；外部手势（如歌单的 50% 阈值双向侧滑、列表项上下拖拽重排）作为外层 Decorator/Wrapper 组合，不将复杂手势状态机侵入基础条目组件。
- **600-Line Budget Compliance**:
  重构后 `AudioTrackItemRow.kt`、`PlaylistTrackRow.kt`、`SearchResultsViews.kt` 与 `PlaybackQueueBottomSheet.kt` 代码行数全部下降至 200 行以内。

## Decisions so far
- **Ticket 01 resolved**: Created `ui/common/AudioTrackRow.kt` (242 lines) and collaborators (`AudioTrackRowDefaults.kt`, `TrackThumbnailLeading.kt`, `EqualizerTrackIndicator.kt`, `AudioQualityBadgePill.kt`), unit tested in `AudioTrackRowTest.kt`. Gist: Slot-based audio row component adhering to <600 line limit with built-in marquee, equalizer, and quality badge. ([01-create-shared-audio-track-row-component.md](issues/01-create-shared-audio-track-row-component.md))

## Ticket Graph & Frontier
```text
[01-create-shared-audio-track-row] (resolved)
             │
   ┌─────────┴─────────┐
   ▼                   ▼
[02-migrate-browser] [03-migrate-playlist] (ready)
   │                   │
   └─────────┬─────────┘
             ▼
[04-migrate-search-and-queue-and-verify] (blocked by 02, 03)
```

1. `01-create-shared-audio-track-row-component.md`: 构建通用 `AudioTrackRow` 组件 (Status: resolved)
2. `02-migrate-browser-audio-track-item-row.md`: 迁移媒体库 `AudioTrackItemRow` (Status: open, Blocked by: None)
3. `03-migrate-playlist-track-row-with-gestures.md`: 迁移歌单 `PlaylistTrackRow` 保持 50% 阈值手势 (Status: open, Blocked by: None)
4. `04-migrate-search-and-queue-and-verify-compliance.md`: 迁移搜索与队列底抽，全量 UI 编译与回归验证 (Status: open, Blocked by: 02, 03)
