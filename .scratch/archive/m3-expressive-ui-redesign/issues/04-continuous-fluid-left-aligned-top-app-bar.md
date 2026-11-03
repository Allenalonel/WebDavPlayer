# 04: M3 Expressive 连续流体折叠顶栏（全程严格左对齐与纯净化）

**What to build:** 
用户在媒体库、歌单列表、歌单详情及服务器等主要视窗中向上滑动页面时，顶栏大标题高度平滑连续收缩至 0，紧凑顶栏无缝锁定在 56dp，内容滑过 24px 毛玻璃雾化弥散层后方，彻底消除空白悬空与生硬切断感。最关键的是，**顶栏必须严格遵守 Compose 嵌套滚动测量协议，设置正确的 heightOffsetLimit，绝对不允许消费吃掉 LazyColumn 的上下滑动手势**；不折叠的页面严禁挂载 `nestedScrollConnection`。展开大标题与收起后小标题的水平基准线全程严格保持左对齐，文字仅在纵向上平滑上浮微缩，绝对不发生任何横向突兀居中跳跃。顶栏彻底移除冗余加号按键，仅保留圆形 Tonal 搜索与更多菜单，视觉极其纯净呼吸。

**Blocked by:** 01: 品牌名称规范化与全天候全域音轨动态取色主题系统

**Status:** resolved

- [x] 顶栏折叠机制必须严格遵守 Compose 嵌套滚动协议：基于官方组件封装或在测量时正确配置 `heightOffsetLimit`，折叠完成后立即释放滑动分量，**绝对保证下层 LazyColumn 上下滑动 100% 畅通无阻**
- [x] 凡不需要大标题折叠的视窗（如歌单详情页），绝对严禁在外层 Scaffold 挂载 `scrollBehavior.nestedScrollConnection`，杜绝任何误拦截手势
- [x] 展开大标题与收起小标题统一锁定左侧垂直基准线（Consistent Left Alignment），收起折叠过程中绝对不发生突兀居中跳跃
- [x] 顶栏在滚动后呈现 24px 柔和毛玻璃弥散雾化效果，与全域动态背景柔和浸润，页面滑过时无生硬色块切断
- [x] 彻底移除各视窗顶栏右侧所有的冗余“加号（+）”按钮，保持顶栏纯净留白
- [x] 服务器管理界面在浅色模式下的卡片、图标与属性标签完成色彩重构，满足高对比度要求，彻底消除黑底糊字
- [x] 在多种滑动速度与边界回弹场景下严格核验所有页面 LazyColumn 上下滑动与顶栏折叠的连通性
- [x] 严格保证既有顶栏与界面功能零破坏：媒体库界面的下拉刷新手势（`pullRefreshState`）与顶栏折叠手势协同工作绝无冲突、手动刷新 Toast 提示、服务器管理界面全生命周期（`ServerEditDialog` 增删改、`onTestConnection` 连通性测试与状态标签、删除服务器级联确认对话框）100% 完整可用

## Answer
- 封装 `com.webdav.player.ui.common.FluidTopAppBar`，严格遵守 Compose 嵌套滚动测量协议：通过 `SideEffect` 动态配置 `scrollBehavior.state.heightOffsetLimit = calculateHeightOffsetLimit(expandedHeightPx)`，大标题折叠完成后消费归零，彻底释放滑动分量至下层 LazyColumn，100% 杜绝手势死锁或截胡。
- 在不需要大标题折叠的歌单详情页（`PlaylistDetailScreen`，已有 Hero Header），顶栏传入 `scrollBehavior = null` 与 `expandedHeight = 0.dp`，且外层 Scaffold 绝不挂载 `nestedScrollConnection`，杜绝手势误拦截。
- 展开大标题与收起小标题严格锁定左侧垂直基准线（无导航图标 16dp，有导航图标 66dp），顶栏左右统一校准为 **标准 16dp 对称外边距**（`padding(start = 16.dp, end = 16.dp)`），左侧返回键与右侧操作键共享统一网格，杜绝贴边局促。
- 滚动时伴随 88% alpha 柔和半透明 `surfaceContainer` 弥散层与动态高度投影（0~3dp），与全域动态背景无缝浸润。
- 媒体库界面（`DirectoryBrowserScreen`）与歌单列表（`PlaylistListScreen`）全面接入 `FluidTopAppBar`，移除顶栏与面包屑冲突的重复返回键与重复刷新键，仅保留 38dp 圆形 Tonal 药丸搜索按键。
- **手势协同仲裁（`Coordinated Pull-to-Refresh`）**：构建 `coordinatedRefreshConnection`，向下回滑时优先消费手势全额展开折叠顶栏；仅在顶栏完全展开且位于列表最顶端时才触发下拉刷新指示器，彻底根除手势抢占死锁。
- 服务器管理界面（`ServerListScreen`）卡片完成色彩重构：卡片底色采用 `surfaceContainer`，活动卡片采用 `surfaceContainerHigh` 伴随 Primary 边框，浅色模式下副标题与标签具备高对比度，移除顶栏冗余“+”按键，完整保留增删改测全生命周期。
- 编写 `FluidTopAppBarTest`，严格覆盖高度坍缩计算、滚动偏移释放（无手势吞食验证）、左对齐基准线不变性契约以及滚动状态检测。
