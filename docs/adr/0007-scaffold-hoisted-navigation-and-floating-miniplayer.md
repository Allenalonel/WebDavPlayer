# Scaffold-Hoisted Navigation and Floating Docked Mini-Player

## Context

原应用界面采用 `MainActivity` 的简单 Crossfade 在「服务器列表」与「目录浏览」两屏之间切换。然而，MiniPlayer 仅嵌入在 `DirectoryBrowserScreen` 内部底栏。当用户返回服务器列表或切换源时，MiniPlayer 彻底消失，破坏了音乐播放器最基础的“随处可控”预期。同时，原有界面缺乏标准的 Material Design 3 顶层导航体系，大播放器与歌词界面的交互也缺乏主流音乐播放器的手势与沉浸感。

## Decision

1. **顶层骨架重塑**：在 `MainActivity` 建立统一的 Material 3 Scaffold 顶层骨架，引入 `Primary Navigation Bar`，以双 Tab（「曲库浏览」+「服务器」）承载核心能力。
2. **全局常驻浮动 MiniPlayer (`Docked Mini-Player`)**：将 MiniPlayer 提升（Hoist）至顶层 Scaffold，悬浮于 NavigationBar 之上。在任一页面导航切换时保持常驻，采用 MD3 浮动卡片形态（16dp 圆角与外边距），支持点击或上滑展开全屏大播放器。
3. **沉浸式 Full Player 配合手势歌词联动**：提取当前音频封面色彩生成动态渐变氛围底色，支持左右滑动手势或点击在唱片封面与逐行同步歌词（平滑滚动 + 点击跳转）之间无缝切换。
4. **浏览体验与层级跳转现代化**：目录浏览采用 MD3 胶囊横向面包屑（`Directory Breadcrumb Strip`），曲目条目增加格式音质徽章（`Audio Quality Badge`），服务器列表支持点选直接切源并平滑过渡到曲库浏览。

## Consequences

- 彻底统一了跨页面的播放控制状态，彻底解决切屏时播控栏闪退/丢失的问题。
- 遵循 Material Design 3 最新规范（动态色彩、Tonal Elevation、卡片悬浮、层级排版），达到现代主流音乐播放器的审美水准与操作直觉。
- 状态提升（State Hoisting）需要将播放会话状态与大播放器展开状态统一在顶层或 ViewModel 中规范管理。
