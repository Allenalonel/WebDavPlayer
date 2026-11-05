# 02: 双向 M3 共享轴向 Tab 导航与底栏药丸拉伸

**What to build:** 彻底消除应用底栏一级导航（歌单、媒体库、服务器）切换时的暴力离屏与白屏闪烁缺陷。重构 `MainActivity.kt`，建立双向对称的 M3 共享轴向（Shared Axis X）过渡系统：向右切 Tab 时新页面从右侧以 20% 短距推移滑入且旧页面向左平滑退场，向左切 Tab 时对等逆向；同时为底栏 52dp 药丸指示仓增加横向流体拉伸回弹（Elastic Pill Stretch）微动效，并确保快速连续切换时画面始终稳定不空白。

**Blocked by:** 01: M3E 动效基础设施与触控微缩修饰符

**Status:** claimed

## Acceptance criteria

- [ ] 彻底移除 `MainActivity.kt` 中 `translationX = 99999f` 与硬性 alpha 切换代码。
- [ ] 在 `MainNavigationCoordinator` 或导航状态中建立前后目的地索引跟踪（`previousDestination` 与 `currentDestination`），计算出严格确定性的位移方向（`isForward`）。
- [ ] 接入 M3 共享轴向转场：前向切换时旧视窗左滑 20% 淡出、新视窗右滑 20% 淡入；后向切换时旧视窗右滑 20% 淡出、新视窗左滑 20% 淡入。
- [ ] 切换过渡期间保持两个视图在界面层级中均处于活跃状态，动画结束后精确清理退场组件，彻底杜绝快速切 Tab 时的白屏、空内容闪烁问题。
- [ ] 底部导航栏中的 52dp 药丸指示仓在目的地切换时呈现流体果冻拉伸（`scaleX` 弹性伸缩回弹），增强底栏动态反馈。
- [ ] 更新并执行 `MainNavigationCoordinatorTest` 单元测试，验证双向索引方向计算与连续切换状态一致性。
