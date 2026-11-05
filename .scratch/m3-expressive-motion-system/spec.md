# Spec: Material 3 Expressive 动效系统与胶囊全屏连续形态蜕变体系

Status: ready-for-agent

## Problem Statement

在 Aoffee Player 既有架构中，尽管视觉层已初步引入 Material 3 Expressive 调色板与卡片轮廓，但应用的交互动效体系存在严重的系统性断层与生硬跳变，极大损害了高保真音乐播放器的流畅质感与操作确定性：

1. **核心导航缺乏物理连续性与空间坐标**：
   - 应用底栏一级视窗（歌单、媒体库、服务器）切换此前采用强制位移（`translationX = 99999f`）暴力离屏，没有任何位移或淡入淡出过渡，给用户带来严重的白屏闪烁感与视线迷失感；
   - 歌单详情下钻与媒体库子目录穿梭依赖布尔状态硬切换，缺乏明确的前进与后退空间层级模型。
2. **播放器展开体验与胶囊形态割裂**：
   - 悬浮在底部的 MiniPlayer 药丸胶囊在展开为全屏大播放器时被粗暴隐藏，而全屏播放器仅仅作为一个独立的底部模态弹窗自下而上遮盖屏幕；
   - 42dp 黑胶唱片封面与全屏 250dp 方圆大封面之间无任何空间联系，无法呈现“从 MiniPlayer 胶囊形态直接生长、展开为全屏大播放器”的物理连续感。
3. **顶栏搜索与次级交互缺乏形变过渡**：
   - 点击放大镜搜索按钮时缺少容器形态扩展，媒体库子目录进入缺乏视差后退；
   - 播放队列切换随机洗牌模式时，歌曲列表条目生硬跳变闪烁，未能直观向用户呈现洗牌重排的物理位移。
4. **严格的工程约束与零 UI 视觉改动铁律**：
   - 动效重构方案必须完全聚焦于运动学规律与物理弹簧，**绝对严禁更改既有界面的静态 UI 布局、内外边距、按钮样式与排版层级**。

## Solution

对标 Google Android 15 与 Material 3 Expressive 官方动效标准，在不改动任何既有 UI 视觉元素与排版结构的前提下，构建一套高内聚、低耦合、高性能的分层式动效系统（Layered Motion System）：

1. **分层式动效系统基础设施**：
   - 统一定义物理基准 Tokens（`SpatialSpring` 空间位移弹簧、`EffectsSpring` 透明度平滑弹簧、`FastSpatialSpring` 触控回弹弹簧）；
   - 沉淀语义化转场 Spec 契约与全局触控微缩回弹（`expressivePress`）扩展修饰符。
2. **胶囊至全屏连续形态蜕变（Capsule-to-Fullscreen Morphing）**：
   - 彻底废除外部割裂弹窗，将 MiniPlayer 药丸胶囊作为单一连续实体驱动；
   - 胶囊边界从 60dp 药丸平滑拉伸撑满全屏，42dp 正圆黑胶封面平滑变焦放大至 250dp 且在全屏态保持高雅静止（圆角 28dp 方圆卡片）；
   - MiniPlayer 右侧的 40dp 播放键与 38dp 队列键平滑滑落并分流映射至全屏中央 68dp 方圆播放大键与右侧队列按键。
3. **双向对称 M3 共享轴向导航过渡（Shared Axis Navigation Transition）**：
   - 一级 Tab 切换严格根据目标索引方向，沿水平 X 轴执行 15%~25% 短距推移与交叉淡化（右切自右入、左切自左入），底栏药丸指示仓同步展现果冻拉伸回弹（Elastic Pill Stretch）；
   - 二级层级下钻（歌单详情、媒体库子目录）执行 100% 右侧推入伴随父级 25%~30% 视差后退（Parallax Retreat），返回时对等逆向拉回。
4. **M3E 悬浮药丸搜索舱与跨维度跳转**：
   - 顶栏 38dp 放大镜按钮以弹性弹簧向四周撑开扩张为 26dp 饱满圆角独立悬浮药丸搜索舱（Floating Search Pill Capsule），全屏搜索视图（SearchView）柔和下展；
   - 搜索结果点击“文件夹”实现跨维度收缩归位并定焦降落（Focal Landing）。
5. **队列平滑重排动效（Animated Queue Placement）**：
   - 播放队列抽屉顶栏对齐 MD3 AssistChip 规范，循环切换 `列表循环` → `单曲循环` → `随机播放`；
   - 进入随机播放时当前曲目锁顶，后续曲目通过稳定 Key 与列表排版插值器平滑滑向新物理槽位，切换回循环模式时顺畅恢复原序镜像。

## User Stories

1. As a listener, I want smooth horizontal directional sliding when switching bottom navigation tabs, so that I maintain a clear spatial orientation of where screens reside.
2. As a listener, I want bottom navigation tabs to slide in from the right when switching to a rightward tab, so that forward navigation feels intuitive.
3. As a listener, I want bottom navigation tabs to slide in from the left when switching to a leftward tab, so that backward navigation feels natural.
4. As a listener, I want tab transitions to use a subtle 15% to 25% short-distance travel rather than full-width jumps, so that I don't experience visual dizziness or motion sickness.
5. As a listener, I want the 52dp pill indicator in the bottom navigation bar to stretch organically like fluid mercury when moving between tabs, so that the bar feels tactile and reactive.
6. As a listener, I want navigation transitions to never flash white screens or empty content during rapid back-and-forth clicking, so that the interface feels solid and unbreakable.
7. As a music curator, I want playlist detail screens to push in smoothly from the right edge of the screen, so that I perceive entering a deeper hierarchy.
8. As a music curator, I want the parent playlist list to retreat slightly to the left with a 25% parallax offset when entering a playlist detail, so that a clear depth relationship is established.
9. As a music curator, I want pressing the back button to pull the playlist detail screen smoothly back to the right, so that returning to the main list is physically symmetric.
10. As an audiophile browsing WebDAV folders, I want opening a subfolder to push the subfolder's file list from the right without vertical jumps, so that navigating directories feels instantaneous and cohesive.
11. As an audiophile browsing WebDAV folders, I want the directory breadcrumb strip to organically expand a new chip at the end when drilling down, so that the navigation trail is visually animated.
12. As an audiophile browsing WebDAV folders, I want tapping an ancestor chip in the breadcrumb bar to smoothly collapse subsequent chips, so that jumping back across multiple folder levels feels clean.
13. As a listener enjoying streaming music, I want tapping the floating mini-player capsule to physically expand the capsule itself into the full-screen player, so that the transition feels continuous rather than a disconnected popup.
14. As a listener, I want the 42dp circular album art disc in the mini-player to smoothly glide towards the screen center and expand to 250dp, so that the visual anchor of the song is never interrupted.
15. As a listener, I want the album artwork corner radius to morph smoothly from a 50% circle into a 28dp squircle card during expansion, so that the transformation feels organic.
16. As a listener, I want the expanded 250dp album artwork in the full-screen player to remain completely static and stationary without spinning, so that album cover art is displayed with maximum fidelity and clarity.
17. As a listener, I want the track title and artist typography to smoothly translate from the mini-player's horizontal baseline to underneath the large artwork, so that metadata doesn't pop or abruptly jump.
18. As a listener, I want the mini-player's 40dp primary play/pause button to smoothly glide to the center of the full-screen transport controls and expand into a 68dp squircle button, so that my primary touch affordance is maintained throughout the motion.
19. As a listener, I want the mini-player's 38dp queue music button to smoothly glide to the rightmost slot of the full-screen transport controls, so that both action buttons retain their relative spatial topology.
20. As a listener, I want the fluid progress slider, timestamp markers, and secondary transport controls to fan out organically as the capsule height expands past 40%, so that full controls emerge gracefully.
21. As a listener, I want dragging downwards or tapping the top collapse chevron in the full-screen player to smoothly fold all elements back into the 60dp floating capsule, so that dismissing the player feels light and effortless.
22. As an audiophile searching a massive music library, I want tapping the 38dp magnifying glass button to expand it smoothly with a spring container transform into a 26dp rounded floating search pill capsule, so that the search bar feels alive.
23. As an audiophile, I want the full-screen search view overlay with search history chips and live results to slide down smoothly underneath the search pill, so that search suggestions are immediately accessible.
24. As an audiophile, I want tapping the back arrow in the search capsule to smoothly shrink the capsule back into the 38dp circular button at the top-right, so that exiting search returns smoothly to the top bar.
25. As an audiophile, I want clicking a folder result in search to smoothly collapse the search view and land precisely on the target folder in the browser with a subtle focal zoom-in, so that cross-dimensional navigation feels like an accurate landing rather than a disorienting horizontal slide.
26. As a listener, I want the playback queue bottom sheet header to feature a dedicated AssistChip that displays the current playback mode, so that I clearly know the active queue progression rule.
27. As a listener, I want tapping the playback mode chip to cycle deterministically through list loop, single loop, and shuffle, so that I can control my queue playback behavior effortlessly.
28. As a listener, I want switching to single loop to update the chip icon and label without unnecessary track movements, so that the interface stays calm.
29. As a listener, I want switching to shuffle mode to keep the currently playing track pinned at the very top of the queue while the subsequent queued tracks smoothly glide past each other into their newly randomized slots, so that I can clearly see how the queue was reordered.
30. As a listener, I want switching back from shuffle mode to list loop to smoothly glide all tracks back into their original un-shuffled mirror order, so that restoring original order is physically tangible and reassuring.
31. As a user tapping track rows, server cards, and buttons, I want interactive surfaces to scale down slightly to 0.965f upon being pressed and bounce back with a spring upon release, so that touches feel tactile and responsive.
32. As a user opening dialogs such as "Add to Playlist", "Edit Server", or "Track Details", I want the dialog window to pop in with a gentle spring scale-in from 0.92f and fade out smoothly upon dismissal, so that modal popups feel cohesive with the rest of the application.
33. As an Android user who values visual consistency, I want all static layouts, padding, margins, colors, and typographic styling to remain 100% identical to the production application before and after motion, so that no UI design regressions are introduced.

## Implementation Decisions

### 1. Motion Architecture & Layering Model
The motion system will be structured into three internal packages under `ui/motion/`:
- **`MotionTokens`**: Centralized repository of Material 3 Expressive spring physics:
  - `SpatialSpring`: Low stiffness, low bounce spring (`StiffnessMediumLow`, `DampingRatioLowBouncy`) dedicated to element translation, size morphing, and container expansion.
  - `EffectsSpring`: Medium stiffness, zero bounce spring (`StiffnessMedium`, `DampingRatioNoBouncy`) dedicated to alpha fading, color transitions, and non-spatial properties.
  - `FastSpatialSpring`: Medium stiffness, medium bounce spring (`StiffnessMedium`, `DampingRatioMediumBouncy`) dedicated to press micro-interactions and button scale bounces.
  - `AccessibilitySupport`: Automatic collapse of all motion durations to 0ms when the system's "Remove animations" accessibility preference is active.
- **`MotionTransitions`**: High-level transition specifications encapsulating enter/exit transition pairs for Shared Axis horizontal navigation, drilldown push/pop, and search view expansions.
- **`MotionModifiers`**: Reusable Compose modifiers including `Modifier.expressivePress()` for 0.965f touch scaling with spring release, and `Modifier.fluidIndicator()` for bottom bar pill stretch.

### 2. Capsule-to-Fullscreen Morphing Container
- **Single Living Entity**: Rather than mounting a disconnected `ModalBottomSheet` on top of an invisible mini-player, the application will hoist a unified `MorphingPlayerContainer` inside `MainActivity`.
- **Interpolation Model**: A single normalized `expansionProgress: Float` (0.0f = collapsed capsule, 1.0f = full-screen player) drives all geometry via Compose `lerp()` inside `graphicsLayer` blocks to defer execution entirely to the Draw phase:
  ```kotlin
  // Geometry interpolation specification derived from prototype
  val containerHeight = lerp(MiniPlayerDefaults.CapsuleHeight, screenHeight, expansionProgress)
  val containerCornerRadius = lerp(MiniPlayerDefaults.CapsuleCornerRadius, WindowCornerRadius, expansionProgress)
  val coverSize = lerp(MiniPlayerDefaults.CoverSize, FullPlayerDefaults.ArtworkSize, expansionProgress)
  val coverCornerRadius = lerp(coverSize / 2f, FullPlayerDefaults.ArtworkSquircleRadius, expansionProgress)
  val playButtonSize = lerp(MiniPlayerDefaults.PlayPauseButtonSize, FullPlayerDefaults.PlayButtonSize, expansionProgress)
  val queueButtonSize = lerp(MiniPlayerDefaults.QueueMusicButtonSize, 48.dp, expansionProgress)
  ```
- **Cover Artwork Static Rendering**: The album art cover will remain strictly stationary with 0° rotation throughout the entire transition. In the collapsed mini-player, it renders within a circle clip; as it expands, the corner radius interpolates to 28dp squircle, smoothly landing at the center artwork position without spinning or tilting.
- **Dual Buttons Handover**: The mini-player's left-positioned 40dp filled primary play button and right-positioned 38dp tonal queue button smoothly transition along curved spatial paths into the full-screen player's centered 68dp squircle play button and rightmost 48dp queue button, preserving their relative left-to-right topology without cross-overs.

### 3. Bidirectional M3 Shared Axis Navigation
- **Top-Level Tabs (Playlists / Browser / Servers)**:
  - Direction computation: A state tracker records `previousDestinationIndex` and `currentDestinationIndex`.
  - When `current > previous` (forward navigation): outgoing view slides out to the left with 20% travel and fades; incoming view slides in from the right with 20% travel and fades.
  - When `current < previous` (backward navigation): outgoing view slides out to the right with 20% travel and fades; incoming view slides in from the left with 20% travel and fades.
  - Both views are actively rendered in the composition tree during the transition window with distinct animation keys to eliminate white screen flashing.
- **Bottom Navigation Pill Stretch**: The active tab's 52dp pill indicator executes a horizontal spring bounce (`scaleX: 0.7f → 1.15f → 1.0f`) upon destination changes.
- **Drilldown Navigation (Playlist Details & Subfolders)**:
  - Entering a detail screen or subfolder applies a 100% horizontal push from the right edge, while the parent container retreats with a 25% to 30% parallax offset and slight dimming.
  - Returning applies the exact inverse motion.

### 4. Floating Search Pill Capsule Morphing
- When search is activated in the directory browser, the 38dp circular search button expands via container transform into the 26dp rounded `FloatingSearchPillCapsule` spanning the top area, while the collapsible top app bar title fades out.
- The `SearchResultsContent` and search history chips slide in from underneath the search capsule.
- Selecting a directory in search results dismisses the search view, shrinks the search capsule back to the 38dp circular button, and switches the directory browser to the target path with a subtle focal scale-fade transition (`scaleIn(0.96f) + fadeIn()`).

### 5. Playback Queue Mode Cycling & Placement Animation
- In `PlaybackQueueBottomSheet`, the header displays a Material 3 `AssistChip` cycling through `PlaybackMode.LIST_LOOP` (🔁), `PlaybackMode.SINGLE_LOOP` (🔂), and `PlaybackMode.SHUFFLE` (🔀).
- In the `LazyColumn`, the items key contract is updated from `key = { index, track -> "track_${track.id}_$index" }` to stable unique track identity `key = { _, track -> track.id }`.
- Each queue item row is equipped with `Modifier.animateItemPlacement(spring(...))` so that transitioning into and out of shuffle mode causes the list rows to smoothly glide across each other to their new slots without recreation.

### 6. Strict Zero UI Mutation Contract
- The static layout hierarchy, paddings, color schemes, typography, button sizes, and composable definitions of `MiniPlayer`, `FullPlayerView`, `DirectoryBrowserScreen`, and `PlaybackQueueBottomSheet` are kept 100% unchanged.
- All motion logic wraps around existing components or acts via `graphicsLayer` matrix manipulations, guaranteeing zero visual drift when motionless.

## Testing Decisions

### Seam Strategy
Testing will be anchored at the highest possible architectural seams to verify behavior rather than transient animation frames:
1. **Motion Tokens & Specs Unit Seam (`MotionTokensTest`)**:
   - Verify that spring stiffness and damping ratios conform to Material 3 Expressive specifications.
   - Verify that when accessibility settings request reduced motion, motion durations collapse to zero.
2. **Navigation Directionality Coordination Seam (`MainNavigationCoordinatorTest`)**:
   - Verify that destination transitions calculate the correct forward vs backward shared axis direction based on destination order (`PLAYLISTS: 0`, `DIRECTORY_BROWSER: 1`, `SERVER_LIST: 2`).
   - Verify that consecutive rapid tab switching preserves destination consistency without state corruption.
3. **Queue Placement Stability Seam (`PlaybackQueueBottomSheetTest`)**:
   - Verify that queue item keys remain stable and independent of list indices during shuffle mode transitions.
   - Verify that cycling playback mode triggers the correct state transitions between `LIST_LOOP`, `SINGLE_LOOP`, and `SHUFFLE`.
4. **Packaging & Resource Seam (`PackagingSeamVerificationTest`)**:
   - Verify that no external runtime animation libraries are added, that all animations rely purely on official `androidx.compose.animation` APIs, and that release APK size stays well within the 7.5 MB budget.

## Out of Scope

- Introducing external Lottie, Rive, or third-party animation runtimes.
- Altering the visual design, typography, color palettes, or layout hierarchies of any existing screen or component.
- Modifying audio decoding, streaming buffer pipelines, WebDAV networking, or playback state machines.
- Adding complex canvas-drawn waveform particle visualizers or dynamic physics ragdolls.

## Further Notes

- The complete interaction model, geometry interpolation math, dual-button handover paths, and bidirectional shared axis transitions have been prototyped and validated in `.tmp/m3e_motion_prototype.html`.
- Architectural context and decision records are codified in `docs/adr/0031-material-3-expressive-motion-and-capsule-morphing.md` and `GLOSSARY.md`.
