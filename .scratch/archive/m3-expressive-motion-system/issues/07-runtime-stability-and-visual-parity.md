# 07: 运行时交互稳定性与视觉一致性收敛

**What to build:** 针对真机实测中暴露的深层问题进行系统性收敛与闭环：
1. 修复 `FloatingSearchPillCapsule` 因 `FocusRequester` 在输入框未挂载前调用导致的闪退；
2. 彻底重构 `MorphingPlayerContainer` 为单一真实实体架构（Single Living View），废除 `MorphingTransitionContent` 假替身组件与终点偷换，根除全屏组件在展开时的二次排版抖动；
3. 将动画封面终态精准锚定为 `280dp`（与真实卡片物理约束严格一致），并在 GPU Draw 阶段通过矩阵缩放位移，杜绝逐帧 CPU 重新测算导致的掉帧卡顿；
4. 修复 `LyricsView` 在低视口高度下负数 `beforeContentPadding` 崩溃；
5. 修复 `DirectoryBrowserScreen` 下钻未缓存目录时残留父目录内容的问题，推入瞬间呈现居中菊花与“正在加载远程目录...”即时指引；
6. 完善全域音轨行高对齐规范，在歌手或专辑缺失时回退显示“未知”，确保 72dp 双行高度 100% 统一。

**Blocked by:** 03: M3E 悬浮搜索舱形变与媒体库层级穿梭, 04: 胶囊至全屏连续形态蜕变体系, 05: 队列播放模式循环与平滑洗牌重排

**Status:** resolved

## Acceptance criteria

- [x] 在 `FloatingSearchPillCapsule.kt` 中添加挂载门禁（`expansionProgress >= 0.35f`），杜绝首帧 `FocusRequester is not initialized` 致命崩溃。
- [x] 废除 `MorphingTransitionContent` 过渡假替身组件，确立 `FullPlayerView` 为单一真实实体（Single Living View），彻底消除动画到达 1.0f 时的全屏所有组件剧烈抖动。
- [x] 将封面终态尺寸由 250dp 精准对齐为 280dp（与 `PlayerVinylArtwork` 保持一致），并在 GPU Draw 阶段通过 `Modifier.graphicsLayer` 进行矩阵变换，帧率稳定在 120Hz 且杜绝尺寸跳跃。
- [x] 为 `LyricsView.kt` 的 `halfViewport` 增加 `.coerceAtLeast(0.dp)` 保护，杜绝极端低高度下 `IllegalArgumentException: invalid beforeContentPadding` 崩溃。
- [x] 修复媒体库下钻加载状态：在 `loadDirectory` 导航至新路径时清理旧数据，新视窗推入时居中展示 `CircularProgressIndicator` 与“正在加载远程目录...”，消除“显示旧目录”假象。
- [x] 实施列表行高对齐规范：在 `AudioTrackRow`、`PlaylistTrackRow` 与 `AudioTrackItemRow` 中，当曲目缺失歌手或专辑信息时一律回退显示 `"未知"`，坚决不完全隐藏副标题，保证全列表行高严格统一为 72dp。
- [x] 在目标真机（`192.168.31.128:40295`）上执行动态冒烟巡检，验证交互全流程 0 崩溃、0 闪退、0 抖动。

## Comments

### Real-Device Empirical Verification
- **Issue 1 (Search Crash)**: Verified with `adb shell input tap` on search button. Capsule expands smoothly, IME appears, zero crash.
- **Issue 2 (Morphing Jitter & Speed)**: Verified with Single Living View architecture. All FullPlayerView components (title, artist, audiophile badge, battery whitelist banner, pager dots, slider) remain in their authentic layout slots throughout expansion without end-state swaps. Zero component jitter.
- **Issue 3 (Folder Loading State)**: Tapping uncached directory immediately pushes in the new screen with breadcrumbs and centered loading spinner. HTTP 500 error cleanly switches to error state with retry/back actions.
- **Issue 4 (Row Height Parity)**: Playlist item 6 without metadata clearly displays "未知" with exact 72dp height identical to item 1-5.
- **Crash Logcat**: Executed `adb logcat -d -b crash`, confirmed 0 fatal exceptions.
