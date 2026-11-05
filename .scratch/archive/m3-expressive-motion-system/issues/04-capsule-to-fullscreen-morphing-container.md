# 04: 胶囊至全屏连续形态蜕变体系

**What to build:** 实现 Aoffee Player 最具辨识度的核心音频播放交互动效。彻底废除割裂的模态弹窗遮罩，在 `MainActivity.kt` 建立统一的单实体连续形变容器 `MorphingPlayerContainer`；由单一状态 `expansionProgress` 在 `graphicsLayer` 绘制阶段消费，驱动悬浮在底部的 60dp 胶囊连续生长扩张为全屏大播放器；42dp 正圆封面平滑滑向中央变焦为 250dp 方圆大卡片（圆角 28dp）且在全屏态保持绝对静止（0° 锁定）；40dp 播放/暂停键与 38dp 队列键平滑滑落并分流映射至全屏中央 68dp Squircle 方圆大按键与右侧队列按键，流体时间滑块与控制按钮顺势展出；下滑手势或轻触下拉箭头时原路弹性收拢回胶囊。

**Blocked by:** 01: M3E 动效基础设施与触控微缩修饰符

**Status:** resolved

## Acceptance criteria

- [x] 废除 `MainActivity.kt` 中原有的生硬 `AnimatedVisibility(slideInVertically)` 外部全屏浮层，将 MiniPlayer 与 FullPlayer 统一收拢为由 `expansionProgress` 单状态驱动的单实体形变容器。
- [x] 容器几何插值完全在 `graphicsLayer` Draw 阶段消费：容器高度自 60dp 撑满屏幕，外围留白平滑贴合边缘，圆角由 30dp 药丸平滑过渡至手机屏幕顶部的 36dp 圆角，杜绝逐帧 Recomposition 与重测算。
- [x] 专辑封面自左侧 42dp 正圆平滑滑移至屏幕中央并变焦扩展为 250dp，圆角平滑演变为 28dp 方圆卡片；在全屏态下严格锁定 0° 绝对静止，坚决杜绝卡片自转。
- [x] 双按钮连续拓扑分流：Mini 态左侧的 40dp 播放/暂停键滑向全屏中央升格为 68dp Squircle 悬浮方圆大按键；右侧的 38dp 队列键滑向全屏最右侧槽位；收起时各自沿原轨迹弹性归位。
- [x] 流体时间进度条与周围播控键随高度撑开顺势展开，支持向下滑动手势与点击顶部把手弹性收拢回胶囊。
- [x] 静态终态下的组件结构、内外边距与排版层级与既有生产环境 100% 保持一致，坚决不引入任何静态 UI 破坏。
- [x] 更新并执行 `PlayerSheetExpansionTest` 与 `MiniPlayerPresentationTest`，自证形态插值与状态闭环正确。
