# 0031: Material 3 Expressive 动效系统与胶囊全屏连续形态蜕变 (Material 3 Expressive Motion System and Capsule Morphing)

## Status

Accepted

## Context

在应用完成 Material 3 Expressive 视觉与自适应调色板升级（ADR-0028）后，界面静态视觉质感大幅提升。然而在用户实际操作走读中，交互动效存在严重的断层与生硬突变：

1. **一级导航与层级穿梭缺失物理坐标**：
   - 底栏 Tab 切换此前在 `MainActivity.kt` 采用 `alpha = 1f/0f` 与 `translationX = 99999f` 暴力移出屏幕，完全无任何位移或交叉过渡；
   - 歌单详情（`PlaylistDetailScreen`）与媒体库目录穿梭（`DirectoryBrowserScreen`）依靠布尔状态硬切换，缺乏进入与退出的空间轴向连续性。
2. **全屏播放器展开割裂为外部模态弹窗**：
   - 尽管原有术语提到了“容器形变”，但实际代码仅仅是一个普通的 `slideInVertically(tween(300))` 浮层自底向上遮盖；
   - 42dp 黑胶小唱片与全屏 250dp 方圆大封面割裂，底部的 60dp 胶囊在展开时突兀消失，无法体现“从 MiniPlayer 胶囊形态直接生长为全屏播放器”的物理连续感。
3. **严格的工程约束与边界契约**：
   - 用户明确指定核心铁律：“**确保方案只调整动画，而不更改系统UI界面**”。
   - 必须保持既有所有 Screen、BottomBar、TopBar、组件内外边距、色彩阶层及排版 100% 不变，重构严格收拢于动效层、过渡规范与形态插值器。

## Decision

恪守“零 UI 侵入、纯动效增强”的重构准则，构建分层式 Material 3 Expressive 动效架构：

### 1. 建立分层式动效系统 (Layered Motion Architecture)

在 `ui/motion/` 下收拢动效底层实现，严禁在业务 Composable 中散落魔数：
- **`MotionTokens.kt`**：对标 Google M3 Expressive Motion 规范，统一定义 Spring 刚度（Stiffness）与阻尼（Damping）：
  - `SpatialSpring`（用于位移、尺寸生长的中低刚度带弹性弹簧：`DampingRatioLowBouncy` + `StiffnessMediumLow`）；
  - `EffectsSpring`（用于透明度、色彩渐变的快速平滑无回弹弹簧：`DampingRatioNoBouncy` + `StiffnessMedium`）；
  - `FastSpatialSpring`（用于微交互按压回弹的中刚度弹簧：`DampingRatioMediumBouncy` + `StiffnessMedium`）。
- **`MotionTransitions.kt`**：封装预置转场 Spec（如 Shared Axis 水平/深度位移与交叉渐变）。
- **`MotionModifiers.kt`**：封装可插拔修饰符（如按压微缩回弹 `expressivePress()` 与弹性可点击 `bouncyClickable()`）。

### 2. M3 共享轴向导航过渡体系 (Shared Axis Navigation Transition)

- **一级 Tab 切换**：依据目标 Tab 的相对索引方向，沿水平 X 轴执行 15%~25% 短距离方向性平滑推移与交叉淡化（右切自右入、左切自左入），彻底根除瞬时跳变与全距位移的眩晕感；
- **二级层级下钻**：歌单详情与子目录进入时自右侧 100% 推入，父级列表以 30% 视差后退（Parallax Retreat）并轻度暗化；物理返回时对等逆向拉回。

### 3. 胶囊至全屏连续形态蜕变体系 (Capsule-to-Fullscreen Morphing)

彻底废除割裂的模态弹窗，将 MiniPlayer 药丸胶囊作为单一连续实体驱动：
- **单一过渡进度驱动 (`expansionProgress: Float`)**：
  - 0f 为底部 60dp 悬浮胶囊态，1f 为全屏播放器态；
- **几何与轮廓连续插值**：
  - 容器尺寸从 `height: 60.dp, width: 100% - 28.dp` 平滑拉伸撑满全屏；
  - 容器圆角从 `30.dp` 药丸圆角平滑演变为屏幕顶部的 `36.dp`；
  - 封面从左侧 `42.dp` 正圆黑胶（`CircleShape`），沿物理轨迹滑向中央放大至 `250.dp` 并变形为方圆大卡片（`RoundedCornerShape(28.dp)`），且在全屏态完全静止不自转；
  - 双按钮连续拓扑映射：左侧 40dp 播放/暂停键（Primary）滑向中央升格为 68dp Squircle 方圆大按键；右侧 38dp 队列键（Tonal）滑向右侧槽位；
  - 流体进度条与次级控制键随高度撑开顺势展开，收起时原路弹性收拢回胶囊。

### 4. 队列洗牌重排与模态微动效拓展 (P0 / P1 / P2 Scope)

在核心骨架之上，纳入深度触感与辅助交互动效闭环：
- **P0 队列洗牌与增删平滑排版 (`Animated Queue Placement`)**：
  - 在 `PlaybackQueueBottomSheet.kt` 中引入 `Modifier.animateItemPlacement()`，切换随机洗牌（Shuffle）与单曲侧滑移除时，列表条目平滑滑向新槽位，杜绝瞬态跳跃；
- **P1 底栏药丸果冻拉伸与弹窗 M3E 缩放**：
  - 底栏 52dp 药丸指示仓在 Tab 切换时加入横向流体拉伸回弹（Elastic Pill Stretch）；
  - 全局 Dialogs（`AddToPlaylistDialog`、`ServerEditDialog`、`PlaylistTrackDetailDialog`）采用 M3E 弹簧缩放弹出（`scaleIn(0.92f) + fadeIn()`）；
- **P2 全局可交互卡片与按钮触控微缩回弹 (`expressivePress`)**：
  - 封装轻量扩展 `Modifier.expressivePress(scaleFactor = 0.97f)`，在服务器卡片、歌单封面行、音轨条目与独立按钮上手势按下时微缩至 0.97f，抬起物理弹簧回弹。

### 5. 零 UI 视觉改动承诺 (Zero UI Mutation Contract)

- 仅修改元素在状态流转时的 `graphicsLayer`（位移 offset、缩放 scale、透明度 alpha、剪裁 shape）与容器外围插值约束；
- 所有组件在终态（Mini 态与 Full 态）的静态呈现、文字排版、字体字号与内边距与原有一致，绝对不引入破坏性视觉重构。

## Consequences

- **空间体验跃升**：彻底消除了全 App 的白屏闪烁、暴力离屏与生硬弹窗，实现真正的现代旗舰级流体连贯性。
- **视觉安全与低风险**：严格坚守“零 UI 布局变更”的红线，杜绝误触碰既有排版。
- **高性能与高可测性**：纯数学插值（`lerp`）与单状态机解耦，在各阶 Android 设备上稳定跑满 120Hz 刷新率。
