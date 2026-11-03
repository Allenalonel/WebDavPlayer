# 02: 紧凑流体底栏 (60dp) 与流体微光 MiniPlayer 胶囊及 24dp 动态避让 FAB

**What to build:** 
用户在应用内浏览时，底部的 NavigationBar 采用 60dp 紧凑交互区并自适应手势条，内部按钮绝对垂直居中；悬浮在上方播放的 MiniPlayer 胶囊高度为 60dp（圆角 30dp），配备 42dp 正圆黑胶唱片封面（CircleShape），融入「方案三：流体微光背景填充 (Liquid Fill)」，内部组件 100% 垂直居中且无挤压。同时，在歌单与服务器界面，右下角的 FAB 悬浮新建按键采用基于 Scaffold 的相对流体堆叠布局，与下方的 MiniPlayer 在全机型手势条高度下均严格保持 24dp 舒展黄金呼吸间距；当 MiniPlayer 出现或隐藏时，FAB 自动平滑相对升降，两者的相对位置清晰从容，彻底消除重叠遮挡与拥挤感；且底栏骨架绝不阻断任何下层手势。

**Blocked by:** 01: 品牌名称规范化与全天候全域音轨动态取色主题系统

**Status:** resolved

- [x] NavigationBar 采用 60dp 紧凑交互区 + 手势条自适应浸润，52x28 M3 药丸与标签文字绝对垂直居中，杜绝顶头挤压
- [x] MiniPlayer 胶囊高度调整为 60dp，采用 30dp 饱满圆角与 42dp 正圆黑胶唱片封面（CircleShape），内部空间舒展充裕
- [x] 落地「方案三：流体微光背景填充 (Liquid Fill)」，彻底拔除底部独立横条，内部组件 100% 绝对垂直居中
- [x] 彻底废除写死绝对像素坐标，FAB 接入全机型自适应动态流体公式：`innerPadding + 8dp + 60dp + 24dp - 16dp`
- [x] MiniPlayer 存在时 FAB 相对下方保持精准 24dp 净空呼吸间距，MiniPlayer 隐藏时 FAB 平滑回落至导航栏上方 16dp 处
- [x] 在真机屏幕与 Compact（360dp）、Standard（392dp）及 Expanded（430dp）设备上验证底栏与 FAB 的间距稳定性与手势可达性
- [x] 确保底栏与 MiniPlayer 的区域之外没有任何透明手势拦截层阻断底层页面的上下滑动
- [x] 严格保证既有播控功能零破坏：MiniPlayer 点击展开、上滑展开、横滑切歌手势（Swipe-to-skip）、队列按钮唤起 `HoistedPlaybackQueueSheet`、重试回放横幅及导航栏三大目的地切换逻辑 100% 完整保留

## Answer
- 在 `MiniPlayerDefaults` 中将 `CapsuleHeight` 设为 `60.dp`，`CapsuleCornerRadius` 为 `30.dp`，`CoverSize` 为 `42.dp`，`PenetrativeContentBottomPadding` 为 `150.dp`。
- `MiniPlayer` 全面落地「方案三：流体微光背景填充」，彻底移除原底部 `LinearProgressIndicator`，胶囊背景根据 `progressFraction` 浸润半透明渐变微光与 1.5dp 前导线；封面采用正圆 `CircleShape`（42dp），内部所有组件在 60dp 胶囊内绝对垂直居中。
- 在 `MainActivity.kt` 中重构底栏为 60dp M3 Expressive 原生药丸底栏，手势条自动浸润并居中；FAB 接入动态公式，在 MiniPlayer 出现时维持 `innerPadding + 8dp + 60dp + 24dp - 16dp` 目标值，精准保持 24dp 净空。
- 更新并通过 `MiniPlayerPresentationTest` 相关几何、避让与垫底规范测试。
