# Scaffold-Hoisted Navigation and Floating Docked Mini-Player

## Context

原应用界面最初采用 `MainActivity` 的简单 Crossfade 在「服务器列表」与「目录浏览」两屏之间切换，MiniPlayer 仅嵌入在局部屏底栏。在早期架构改造中，MiniPlayer 与 `Primary Navigation Bar` 一同被提升至顶层 `Scaffold(bottomBar = { Column { MiniPlayer; NavigationBar } })`。

然而，将 MiniPlayer 直接硬编码嵌套在 `Scaffold.bottomBar` 槽位中带来了新的视觉与交互硬伤：
1. **生硬的矩形色块与缺乏独立悬浮感**：MiniPlayer 与 NavigationBar 共享测量槽位，胶囊四周的外边距透出底栏的底层固定色背景，形成生硬的矩形色块，缺乏真正浮动在内容之上的独立浮岛感。
2. **列表滑动截断**：Scaffold 内部将底栏 Column 的总高度作为 `calculateBottomPadding()` 强行推挤内容视口，导致歌曲列表在 MiniPlayer 顶端就被截断，无法滑入胶囊后方，丧失现代主流音乐播放器的半透明穿透与纵深流动感。
3. **单调的手势与按键冗余**：右侧仅提供固定的播放/暂停与下一曲按钮，缺少水平滑动切歌的手势支持，且无法一键打开播放队列。

## Decision

1. **顶层骨架解耦与独立悬浮覆盖层 (Decoupled Overlay Architecture)**：
   - 将 `Docked Mini-Player` 从 `Scaffold.bottomBar` 彻底抽离，提升为顶层独立的悬浮层（Overlay），以 `Alignment.BottomCenter` 悬停在 `Primary Navigation Bar` 之上。
   - `Scaffold.bottomBar` 仅保留持久常驻的 `Primary Navigation Bar`。
   - 胶囊四周设置独立横向呼吸边距（14dp）与纵向悬停间距（8dp），呼吸边距区域完全不拦截点击与滑动，手势穿透直达底层视图。
2. **全屏沉浸穿透与静态安全垫 (Penetrative Scrolling with ContentPadding)**：
   - 主视图列表（`DirectoryBrowserScreen`、`PlaylistDetailScreen`、`ServerListScreen`）容器完全延伸至底部，不被底栏计算出的 Padding 截断。
   - 建立标准化的静态底部安全内边距契约：
     `contentPadding.bottom = NavigationBarHeight (80dp) + MiniPlayerHeight (~64dp) + VerticalSpacing (8dp) + SafetyBuffer (8dp) = 160dp`。
   - 该内边距静态常驻，不受 MiniPlayer 显隐影响，彻底杜绝切歌或播放暂停引起的任何布局跳变（Layout Jump）与重绘卡顿。
3. **跨版本自适应晶透材质 (Adaptive Glass & Contrast Guard)**：
   - **Android 12+ (API 31+)**：启用硬件级 GPU 高斯模糊（`RenderEffect.createBlurEffect(20dp)`）+ 85% 半透明 `surfaceContainerHighest` 质感 + 1dp 晶莹边框（`outlineVariant` 35% alpha）。
   - **Android 10/11 (API 29/30)**：自动优雅降级为 94% 高遮蔽纯净卡片（`surfaceContainerHigh`）+ MD3 柔和多阶扩散投影（6dp 阶调色调，8dp 投影阴影），杜绝 GPU 显存过载与跨版本兼容崩溃。
   - 外廓采用全胶囊形态（`CapsuleCornerRadius = 28dp`，完全圆角跑道胶囊）。
4. **手势增强与按键重构 (Swipe-to-Skip & Queue Access)**：
   - 胶囊卡片支持水平轻扫滑动手势（左滑切下一曲、右滑切上一曲），跨越 40dp 阈值时触发系统级触觉震动反馈（`HapticFeedbackType.LongPress`）。
   - 保留上滑展开全屏大播放器（阈值 < -20f）与全卡片轻触展开手势。
   - 替换原先与手势功能重叠的「下一曲」图标按钮，改设为「打开播放队列 (`QueueMusic`)」按钮，一键弹出 `Playback Queue Bottom Sheet`。
5. **沉浸式 Full Player 配合手势歌词联动**：提取当前音频封面色彩生成动态渐变氛围底色，支持左右滑动手势或点击在唱片封面与逐行同步歌词之间无缝切换。

## Consequences

- 实现了对标 Apple Music、Spotify 等主流流媒体应用的现代流体晶透悬浮胶囊体验。
- 列表滑动具有真实的半透明穿透与流动纵深，且最后一行曲目恰好稳稳停留在悬浮胶囊上方。
- 静态 160dp 垫底规避了复杂的动态 Padding 动画重组，界面在曲目切换与显隐过程中零跳变、零卡顿。
- API 29 与 API 31+ 平台表现一致性得到强保障，既具备现代设备的材质质感，又守住了老旧机型的流畅底线。
