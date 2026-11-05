# 01: M3E 动效基础设施与触控微缩修饰符

**What to build:** 为整个应用建立 Material 3 Expressive 物理动效基准与触控微交互体系。定义收拢统一的 Spring 刚度与阻尼常量（`SpatialSpring` 空间位移、`EffectsSpring` 透明度平滑、`FastSpatialSpring` 触控回弹），并原生支持 Android 系统无障碍“减少动态效果（Reduce Motion）”零时长优雅降级；封装可插拔的 `Modifier.expressivePress(scaleFactor = 0.965f)` 与 `bouncyClickable()` 扩展修饰符，使用户在按压音轨行、服务器卡片与操作按钮时获得即时、细腻的弹性微缩反馈，松手时物理弹簧回弹。

**Blocked by:** None (can start immediately)

**Status:** resolved

## Acceptance criteria

- [x] 创建 `com.webdav.player.ui.motion.MotionTokens`，统一定义 `SpatialSpring`（`StiffnessMediumLow`, `DampingRatioLowBouncy`）、`EffectsSpring`（`StiffnessMedium`, `DampingRatioNoBouncy`）与 `FastSpatialSpring`（`StiffnessMedium`, `DampingRatioMediumBouncy`）。
- [x] 在 `MotionTokens` 中实现无障碍合规：当系统设置开启“减少动态效果（Reduce Motion）”时，所有动画时长自动折叠为 0ms，提供即时切换降级。
- [x] 创建 `com.webdav.player.ui.motion.MotionModifiers`，封装 `Modifier.expressivePress(scaleFactor = 0.965f)` 与 `Modifier.bouncyClickable()`，通过 `graphicsLayer` 消费交互状态并实现松手弹簧回弹。
- [x] 将 `expressivePress` 与 `bouncyClickable` 应用于音轨列表行（`AudioTrackRow`）、服务器卡片（`ServerCardItem`）及核心独立按钮，赋予全局实体触控质感。
- [x] 编写并执行单元测试 `MotionTokensTest`，验证刚度阻尼参数符合 M3E 规范，且无障碍模式下正确返回零时长。
