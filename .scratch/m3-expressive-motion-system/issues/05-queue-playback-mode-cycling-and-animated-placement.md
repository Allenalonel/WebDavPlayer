# 05: 队列播放模式循环与平滑洗牌重排

**What to build:** 优化播放队列管理抽屉（`PlaybackQueueBottomSheet.kt`）的交互规范与动画质感。顶栏右侧严格对齐 MD3 `AssistChip` 规范，支持点击循环切换播放模式（`列表循环 🔁` → `单曲循环 🔂` → `随机播放 🔀`）；重构 `LazyColumn` 列表项的 Key 契约，由与索引绑定的 key 改为基于曲目自身的稳定唯一标识（`track.id`），并为条目挂载 `Modifier.animateItemPlacement(spring(...))`；在切换至随机播放时，当前正在播放的曲目严格锁定在首行不动，其余待播曲目伴随物理弹簧平滑交错滑动重排到新槽位；切回列表循环时无损恢复原序镜像，彻底根除列表生硬跳变与条目闪现。

**Blocked by:** 01: M3E 动效基础设施与触控微缩修饰符

**Status:** claimed

## Acceptance criteria

- [ ] 在 `PlaybackQueueBottomSheet.kt` 顶栏右上角实现规范的 MD3 `AssistChip`，正确展示图标与文案，点击按 `LIST_LOOP` → `SINGLE_LOOP` → `SHUFFLE` 循环流转。
- [ ] 优化 `LazyColumn` 的 `itemsIndexed` 声明，将 `key = { index, track -> "track_${track.id}_$index" }` 重构为稳定键 `key = { _, track -> track.id }`，确保 Compose 正确追踪条目实体跨槽位移动。
- [ ] 为队列曲目条目挂载 `Modifier.animateItemPlacement(spring(stiffness = StiffnessMediumLow, dampingRatio = DampingRatioLowBouncy))`，实现底层硬件加速平滑排版。
- [ ] 切换至随机播放（`SHUFFLE`）时，当前在播曲目严格置顶保持第 1 行静止，后续待播曲目平滑滑行就位；切回列表循环时条目平滑滑回原序镜像。
- [ ] 侧滑移除单曲时，下方曲目平滑上浮填补空缺，杜绝生硬吸附。
- [ ] 更新并执行 `PlaybackQueueBottomSheetTest`，验证稳定 Key 契约与播放模式循环状态机。
