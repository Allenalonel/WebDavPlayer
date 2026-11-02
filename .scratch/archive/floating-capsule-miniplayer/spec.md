# Spec: Floating Capsule Docked Mini-Player

Status: completed

## Problem Statement

当前应用中的 `Docked Mini-Player` 被硬编码放置在 `MainActivity` 的 `Scaffold(bottomBar = { Column { MiniPlayer; NavigationBar } })` 容器中。这种内嵌结构带来了若干直接破坏用户体验与界面美感的问题：

1. **生硬的矩形色块与缺乏独立悬浮感**：MiniPlayer 与底部的 `Primary Navigation Bar` 共享同一底栏测量槽位，导致胶囊四周预留的外边距（Margin/Padding）直接透出 Scaffold 的底层固定纯色背景，在胶囊两端和上下形成了生硬的白色/容器色矩形色块。它看起来像被死死挤压在抽屉盒子里，而非真正浮动在内容之上的独立浮岛。
2. **列表滑动生硬截断，缺乏穿透流动感**：Scaffold 内部将整个底部 Column 的总高度作为 `calculateBottomPadding()` 强行向上推挤主内容视口。用户在浏览歌曲或目录时，列表在 MiniPlayer 顶端就被生硬截断，无法滑入胶囊后方，彻底失去了现代主流音乐播放器（如 Apple Music、Spotify）标志性的半透明穿透与流动纵深体验。
3. **单调的操作手势与按键功能冗余**：目前右侧仅提供固定的「播放/暂停」与「下一曲」图标按钮，缺乏单手在胶囊上快速滑动切歌的手势支持；同时，用户若想查看当前播放队列，必须先上滑点开大播放器再打开队列抽屉，操作链路冗长。

## Solution

将 MiniPlayer 彻底重构成真正遵循 Material Design 3 (MD3) 规范的**独立动态原色晶透悬浮胶囊（Liquid Glass Floating Capsule Island）**：

1. **顶层骨架解耦（Decoupled Overlay Architecture）**：将 MiniPlayer 从 `Scaffold.bottomBar` 槽位中彻底抽离，提升为顶层独立的悬浮层（Overlay），悬停在 `Primary Navigation Bar` 之上，留出纯净的横向呼吸边距与纵向悬停间隙。
2. **全屏沉浸穿透与静态安全垫（Penetrative Scrolling with ContentPadding）**：主视图列表（`LazyColumn`）完全延伸至底部，通过尾部静态注入的安全内边距（`contentPadding` 约 160dp）确保用户滑动时列表自然从胶囊背后穿透滑过；当滑动至最底端时，最后一首曲目完全露出在胶囊上方，且彻底避免切歌或播放栏显隐时引起的任何布局跳变（Layout Jump）。
3. **纯正 MD3 原生高阶浮岛材质（Pure MD3 Elevated Solid Island & Contrast Guard）**：
   - **真机实测驱动的决策演进**：真机验证表明 Compose 原生 `RenderEffect` 仅作用于当前 Layer 本身（会模糊前景文字自身），而纯半透明又会导致底层滑动曲目文字严重穿透打架；因此全面收敛至 Material Design 3 官方推荐的实体色调模型；
   - **材质与轮廓规范**：采用 100% 不透明纯色 `surfaceContainerHigh` 表面 + `tonalElevation = 6dp` + `shadowElevation = 8dp` 柔光扩散投影 + 1dp `outlineVariant` (25% alpha) 细边框，提供 100% 满分无障碍可读性与 Google Pixel 原生系统质感；
   - **内嵌微跑道进度条**：底端进度指示条采用两端各内缩 `14dp`、高度 `3dp`、圆角 `3dp` 的悬浮胶囊微跑道，与 `28dp` 跑道外廓形成严谨的 MD3 几何比例呼应。
4. **手势增强与按键重构（Swipe-to-Skip & Queue Access）**：
   - 胶囊卡片全面支持**水平轻扫滑动手势**（左滑切下一曲、右滑切上一曲），并在触发切歌阈值时提供清脆的系统级**触觉震动反馈（HapticFeedback）**；
   - 原「下一曲」图标按钮因与手势功能重叠予以替换，改设为**「打开播放队列 (QueueMusic)」按钮**，点击直接弹出应用内已有的 `Playback Queue Bottom Sheet`；
   - 完整继承原有的上滑/点击呼出全屏大播放器、跑马灯标题、微型跑道进度条及网络异常重试条。
5. **决策归档**：原地更新 `docs/adr/0007-scaffold-hoisted-navigation-and-floating-miniplayer.md`。

## User Stories

1. As a listener, I want the MiniPlayer to appear as a detached, floating capsule island with rounded pill contours and ambient elevation, so that it feels like a modern and polished music app rather than an embedded box.
2. As a listener, I want the music track list to smoothly slide underneath and behind the MiniPlayer when I scroll, so that I experience a cohesive sense of depth and fluid immersion.
3. As a listener, when I scroll to the absolute bottom of any audio track list, I want the last track to stop cleanly above the floating capsule, so that no track is obscured or unclickable.
4. As a listener, when playback starts or stops and the MiniPlayer appears or disappears, I want the browsing list position to remain perfectly stable without jarring layout jumps, so that my reading flow is not interrupted.
5. As a listener, I want the floating capsule to feature a pure solid MD3 surface container with clean outline highlights, so that underlying list text never bleeds through or obscures track readability.
6. As an Android user across all API levels (API 29–34+), I want the floating capsule to render with zero graphics errors, zero dropped frames, and standard MD3 ambient elevation shadows.
7. As a listener, I want to swipe horizontally left or right on the floating capsule, so that I can quickly skip to the next or previous track with one hand.
8. As a listener, when my horizontal swipe crosses the threshold to trigger track switching, I want to feel a crisp haptic vibration tick, so that I have clear sensory confirmation of the action.
9. As a listener, I want a dedicated queue icon button on the right side of the floating capsule, so that I can immediately open the `Playback Queue Bottom Sheet` with a single tap.
10. As a listener, I want a centered circular filled/tonal button on the capsule, so that I can reliably toggle play and pause without looking closely.
11. As a listener, I want to tap anywhere on the capsule or drag upwards, so that I can seamlessly expand into the `Full Player Sheet` with album art and synchronized lyrics.
12. As a listener, when touching or dragging in the breathing margins around the capsule or between the capsule and the bottom navigation bar, I want my touch gestures to pass through to scroll the underlying list, so that there are no dead touch zones near the edges of the screen.
13. As a listener, when a remote streaming error occurs, I want a compact retry banner to be clearly visible within the capsule, so that I can easily retry playback without opening the full player.
14. As a listener, I want a sleek micro progress indicator integrated along the bottom curve of the capsule, so that I can glance at current track playback progress without visual clutter.
15. As a listener, I want track titles that exceed the capsule's width to smoothly marquee scroll, so that I can always read the full song name regardless of its length.
16. As a listener, I want consistent high-contrast typography in both light and dark themes, so that track titles and controls meet WCAG readability standards under any ambient lighting.

## Implementation Decisions

### 1. Structural Hoisting and Overlay Layout
- MiniPlayer is completely decoupled and removed from the `bottomBar` parameter of the top-level `Scaffold` in the main activity view hierarchy.
- The `Scaffold.bottomBar` slot is dedicated exclusively to the persistent `Primary Navigation Bar`.
- The floating capsule is hoisted into an overlay `Box` placed directly above the scaffold content, aligned to `BottomCenter`.
- Positioning is governed strictly by an offset/padding constraint placing the capsule above the navigation bar with dedicated lateral breathing margins (14–16dp) and vertical elevation spacing (8–10dp).
- The overlay container wrapper must be pass-through for touch hit-testing outside the capsule surface bounds, ensuring lateral padding regions do not intercept list scrolling.

### 2. Penetrative Scrolling and Static Inset Contract
- Screen content views (`DirectoryBrowserScreen`, `PlaylistDetailScreen`, `ServerListScreen`) render their scrollable collections (`LazyColumn`) edge-to-edge.
- A standardized bottom content padding contract is established across all main list views:
  `contentPadding.bottom = NavigationBarHeight + MiniPlayerHeight + VerticalSpacing + SafetyBuffer` (approx. 160dp).
- This inset is statically preserved regardless of whether the mini-player is currently visible, completely eliminating layout jumping, list recreation, or stutter when tracks begin playback or pause.

### 3. Adaptive Surface Material and Rendering Compatibility
- Adopt the pure Material Design 3 Elevated Solid Island surface architecture:
  - **Color & Opacity**: 100% solid, opaque `surfaceContainerHigh` surface tint, preventing any underlying list text or complex artwork from bleeding through or colliding with miniplayer foreground text.
  - **Elevation & Contour**: Standardized `tonalElevation = 6.dp` and `shadowElevation = 8.dp` ambient elevation shadow, plus 1dp `outlineVariant` border with 25% alpha, delivering true 3D spatial separation.
  - **Micro Progress Track**: Inset by 14dp on both lateral ends, 3dp height, fully rounded corners (`RoundedCornerShape(3.dp)`), floating elegantly at the capsule bottom.
- The capsule boundary strictly adopts a full pill geometry (`CapsuleCornerRadius = 28.dp`, `CircleShape`).

### 4. Gestural Dynamics and Tactile Feedback
- **Horizontal Swipe Detection**: Incorporates pointer-input tracking for horizontal drag gestures over the capsule body.
  - Dragging beyond a defined horizontal threshold (approx. 40dp) to the left dispatches `PlaybackIntent.skipToNext`.
  - Dragging beyond the threshold to the right dispatches `PlaybackIntent.skipToPrevious`.
  - Crossing the threshold invokes `HapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)` (or equivalent confirmation tick).
- **Vertical Drag and Tap Detection**: Retains vertical upward drag tracking (threshold < -20f) and standard click handling to invoke the full player expansion coordinator callback.

### 5. Control Cluster Refactoring
- The right-hand action group is consolidated into two distinct touch-target buttons adhering to MD3 touch clearance:
  1. **Play/Pause Toggle**: Retained as a circular FilledTonalIconButton with clear state transitions.
  2. **Queue Music Button**: Replaces the redundant "Next Track" button. Displays the standard `QueueMusic` icon and dispatches the action to expand the `Playback Queue Bottom Sheet`.

### 6. Domain Vocabulary Alignment & ADR Maintenance
- In accordance with `GLOSSARY.md`, the component is canonically recognized as `Docked Mini-Player`.
- Existing architectural record `docs/adr/0007-scaffold-hoisted-navigation-and-floating-miniplayer.md` is updated in-place to document the evolution from bottom-bar embedding to independent floating overlay, detailing the penetrative list contract and API 29/31 rendering trade-offs.

## Testing Decisions

### What Makes a Good Test
- Tests must verify external component contracts, geometric bounds, touch clearance, state transformations, and subtitle formatting without coupling to private internal composable implementation details.
- Unit tests must run swiftly on JVM without Android device dependencies wherever possible.

### Modules and Seams to Test
- **Geometry & Dimension Contract**: Update `MiniPlayerPresentationTest` to validate:
  - Full capsule pill corner radius and height bounds.
  - Safe interactive touch clearance between the Play/Pause button and the newly introduced Queue Music button (ensuring MD3 minimum touch target separation >= 48dp).
  - Subtitle formatting for diverse combinations of artist, format, and missing metadata.
  - Retry banner visibility state machine conditions across playback error states.
- **Gesture Threshold Constants**: Validate that horizontal swipe thresholds and vertical expansion thresholds maintain predictable mathematical boundaries preventing accidental triggers.

### Prior Art
- `app/src/test/java/com/webdav/player/ui/player/MiniPlayerPresentationTest.kt` serves as the direct precedent and testing foundation for this component.

## Out of Scope

- Redesigning the `Full Player Sheet` interior layout (album disc, audio quality pills, or synced lyrics views).
- Altering the playback engine pipeline, streaming audio decoders, or HTTP range reconnection mechanisms.
- Changing the destination structure or navigation routes of the `Primary Navigation Bar`.
- Introducing third-party blur libraries or native C++ renderers for pre-Android 12 devices.

## Further Notes

- An interactive, runnable HTML prototype demonstrating the liquid glass aesthetics, list scroll-through penetration, and variant comparison has been verified and staged in `.tmp/mini-player-capsule-prototype.html`.
- Once implementation is authorized, tickets can be generated and executed with targeted unit tests following the standard issue tracker workflow.
