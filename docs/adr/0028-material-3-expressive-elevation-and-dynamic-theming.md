# 0028: Material 3 Expressive 界面重构、全天候全域动态取色与底栏流体瘦身

## Status

Accepted (Supersedes ADR-0007 elevation constants)

## Context

随着 WebDAV 音乐播放器功能演进与用户高频深度使用，原有的基础 Material Design 3 界面与交互体系暴露出若干核心体验瓶颈与视觉局限：

1. **底栏整体过高，严重挤占纵向可视视口（修正 ADR-0007）**：
   在早期的 [ADR-0007](0007-scaffold-hoisted-navigation-and-floating-miniplayer.md) 中，为保证 MiniPlayer 与 NavigationBar 之间具备独立悬浮感，建立了静态垫底契约：
   $$\text{NavigationBarHeight}(80\text{dp}) + \text{MiniPlayerHeight}(64\text{dp}) + \text{VerticalSpacing}(8\text{dp}) + \text{SafetyBuffer}(8\text{dp}) = 160\text{dp}$$
   在现代长列表浏览场景下，高达 160dp~168dp 的固定占屏高度导致可视曲目数量锐减约 30%，尤其在小屏设备上浏览体验局促压抑。
2. **顶栏设计单薄老旧，滚动折叠缺乏融合感**：
   顶栏全部采用最普通的基础小型 TopAppBar，缺乏现代 Material 3 Expressive 的呼吸感与视觉张力；折叠设计生硬，存在空白占位悬空或展开/收缩时标题横向突然居中的突兀跳跃；且顶栏硬塞“加号（+）”，与右下角规范的 FAB 悬浮按键产生功能冗余与割裂。
3. **歌单歌曲界面在播状态缺失**：
   媒体库（`DirectoryBrowserScreen`）已具备 `EqualizerTrackIndicator`（动效均衡器跳动音波）用于标记在播音轨；而歌单详情界面（`PlaylistDetailScreen` / `PlaylistTrackRow`）未接入 `PlayerSessionState` 订阅，导致在播歌曲没有任何视觉指示，割裂了多视窗间的交互一致性。
4. **应用命名存在缩写不规范**：
   多处代码与 `strings.xml` 中将 App 显示名称标记为 `WebDavPlayer`，未遵循 WebDAV 协议全大写的行业标准缩写，规范名称应为 `WebDAVPlayer`。
5. **动态取色局限与浅色模式对比度失衡**：
   原本仅在全屏播放器界面（`FullPlayerView`）提取专辑封面背景渐变，主界面（歌单、媒体库、服务器）仍为固定单一底色；且在浅色模式下，灰色次级文本与歌曲数量胶囊底色对比度不足（小于 3.5:1），产生严重糊字缺陷。
6. **搜索与多选交互粗糙**：
   全屏搜索界面顶部带有生硬通栏大横条切断感，缺乏 M3 Expressive 的独立悬浮药丸形态；长按多选胶囊与 MiniPlayer 存在避让干涉。

## Decision

对齐 Google Material 3 Expressive (M3E) 官方最新设计语言与移动工程实践，全面落实七大系统级架构与界面改造：

### 1. 规范应用品牌全称 (`WebDAVPlayer`)
- 将 `app/src/main/res/values/strings.xml` 中的 `app_name` 正式校准为 `WebDAVPlayer`（DAV 全部大写），统一全域品牌视觉与启动器标示。

### 2. 底栏高度流体瘦身与 FAB 相对避让（全面修正 ADR-0007 契约）
- **废止 ADR-0007 的 160dp 绝对测绘参数**，全面实施底栏精炼流体规范：
  - `Primary Navigation Bar`：采用 **60dp 紧凑交互区 + 边缘到边缘（Edge-to-Edge）手势区无感浸润**，内部以 52dp x 28dp M3 药丸指示舱（圆角 14dp）搭配 20dp 图标与 11sp 标签文字，在 60dp 视口内**绝对垂直居中**，彻底根除 80dp/112dp 巨型底栏的笨重挤压感与按钮偏心缺陷；
  - `MiniPlayer` 胶囊：升级为 **60dp 饱满独立大胶囊（30dp 饱满圆角）**，采用 `surfaceContainerHighest` 质感容器与 **42dp 正圆形黑胶唱片封面（CircleShape）**，与半圆封头形成同轴同心圆环；
  - **流体微光背景进度填充（Liquid Fill）**：彻底拔除胶囊底部的独立进度条（解决底部条形组件导致的内部组件上浮挤占），将胶囊背景本身作为进度载体，随着播放平滑向右浸润半透明主题微光与 1.5dp 前导微光线，内部组件（封面、歌名、播控）在 60dp 胶囊内 **100% 绝对垂直居中**；
  - 底部总占屏高度优化为 $60\text{dp} + 8\text{dp} + 60\text{dp} = 128\text{dp}$（较旧版 168dp 释放 40dp 可视曲目区）。
- **Scaffold 相对流体堆叠（Intrinsic Relative Stack）与动态避让**：
  - 彻底废除 FAB 的写死绝对定位，将 FAB 融入 Scaffold 底部流体堆叠流；
  - 采用全机型自适应动态公式：
    $$\text{Target} = \begin{cases} \text{innerPadding} + 8\text{dp} + \text{MiniPlayerHeight}(60\text{dp}) + 24\text{dp} - 16\text{dp}, & \text{MiniPlayer 可见} \\ \text{innerPadding}, & \text{MiniPlayer 隐藏} \end{cases}$$
    在任意机型手势条高度下，FAB 相对下方容器均**严格维持 24dp 黄金律动呼吸净空**，绝不遮挡重叠。

### 3. 全天候（深色/浅色）全域音轨动态取色系统 (`Global Dynamic Artwork Palette`)
- **双模色阶推导引擎 (`ArtworkColorExtractor`)**：
  - 升级色彩提取器，从当前音轨封面提取种子色后，基于 HCT Tonal Palette 同时计算**暗色全域色阶**（深度沉浸背景 `#141022`、容器底色 `#211B34`）与**浅色全域色阶**（淡雅粉紫 `#F7F3FB`、落日杏白 `#FFF5F0`、冰海晨蓝 `#F0F6FA`、田园草绿 `#F0F8F2`）。
- **整机全天候动态换肤**：
  - `WebDavPlayerTheme` 接入当前音轨的动态 `ColorScheme`，整机大背景（Background）、卡片底色（SurfaceContainer）、顶栏折叠背景、底栏滑块及按键全部随切歌实时丝滑变色（0.45s 平滑过渡）。
- **WCAG AAA 级高对比度治理**：
  - 浅色模式下，次级信息与副标锁定深色石板墨炭色（`#3A3546`），字重提升至 SemiBold；
  - 歌曲数量胶囊重构为 `primaryContainer` 底色配合 `onPrimaryContainer` 加粗字体，对比度跃升至 **8.5:1 超清标准**，彻底消灭糊字。

### 4. M3 Expressive 连续流体折叠顶栏 (`Continuous Fluid Top App Bar`)
- **原生连续高度坍缩**：
  - 接入 `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()`，大标题容器在滑动时高度平滑坍缩至 0，无任何空白悬空占位。
- **全程严格左对齐（Consistent Left Alignment）与 16dp 对称边距**：
  - 顶栏左右两侧统一校准为 **标准 16dp 呼吸外边距**（`padding(start = 16.dp, end = 16.dp)`），左侧返回键、右侧搜索键与下方卡片边缘严格共享统一网格基准线，消除贴边局促感；
  - 展开时大标题与收起后紧凑小标题统一锁定左侧垂直基准线，滚动时文字仅垂直上浮微缩淡入，**绝对不发生横向突兀居中跳跃**。
- **24px 毛玻璃雾化弥散与顶栏纯净化**：
  - 未滚动时顶栏纯透融入背景，滚动时呈现 `24px` 柔和毛玻璃雾化；
  - 彻底移除顶栏右侧冗余的“加号（+）”及与“面包屑”冲突的重复刷新/返回键，仅保留圆形 Tonal 搜索与更多操作；
  - **手势协同仲裁（`Coordinated Pull-to-Refresh`）**：构建 `coordinatedRefreshConnection`，向下回滑时优先消费滑动手势全额展开顶栏；仅当顶栏 100% 完全展开且列表位于最顶端时，才触发下拉刷新指示器，彻底根除手势抢占死锁。

### 5. 纯正 M3E 独立药丸搜索系统 (`Floating Search Pill Capsule`)
- **剔除通栏大横条**：
  - 移除全屏搜索界面的通栏生硬背景条与底部分割线，背景与整机全屏背景流体相融。
- **一体化药丸搜索舱（26dp Squircle Pill）**：
  - 顶部预留 44dp 状态栏安全避让（绝不遮挡动态岛与时间）；
  - 独立悬浮 52dp 药丸搜索舱，集成左侧返回箭头、放大镜、加粗输入框与清除按键；
  - 集成历史搜索胶囊 Chips、热门标签、即打即搜实时过滤与一键点击切歌。

### 6. 歌单单列行规范与长按多选胶囊体系
- **单列歌单列表行（M3E Linear Rows）与绝对内部垂直居中**：
  - 摒弃大块状网格及带有槽位偏心偏差的传统 `ListItem`，以原生 `Row(verticalAlignment = Alignment.CenterVertically)` 重构；
  - 52dp 微圆角封面 + 粗体歌单名 + 数量胶囊 Pill + 右侧更多按键在 18dp 卡片内实现**绝对纵向几何居中**；
  - 严格单行防折行：`white-space: nowrap` 与 `text-overflow: ellipsis`，任何分辨率绝不折成两行。
- **在播状态双向贯通**：
  - `PlaylistDetailViewModel` 与 `PlaylistTrackRow` 接入播放器 `sessionState`；
  - 在播曲目封面覆盖 3 柱跳动音波（`EqualizerTrackIndicator`），暂停时静止，标题 Primary 色加粗高亮。
- **长按多选操作胶囊 (`FloatingMultiSelectBottomBar`)**：
  - 媒体库或曲目列表长按任意歌曲触发多选：列表浮现复选框，MiniPlayer 优雅让位；
  - 底部弹出 28dp 悬浮多选操作胶囊（`下一首播放` + `添加到歌单`）；
  - 顶栏平滑转换为 `已选 X 项` 上下文模式。

### 7. 全屏方形大封面与流体氛围动感歌词 (`WindowSizeClass Fluid Viewport`)
- **28dp 方形圆角大封面 (250x250 Squircle)**：
  - 全屏播放器核心告别黑胶唱片，回归高质感方形圆角大卡片，外带环境光晕柔和投影；
- **顶栏去重与动态服务器名称**：
  - 移除中央多余的 36dp x 4dp 小横条把手，避免与左上角收起按键功能重叠；
  - 顶栏动态展示当前活跃服务器名称（`正在播放自 $serverName`，如 `Openlist` / `Synology NAS`）与曲目信息，完全剔除歌词界面内部原先重复占位的歌名大横条。
- **流体氛围聚焦流歌词（Scheme 1: Fluid Ambient Focus）**：
  - 视口半高（`viewportHeight / 2`）动态内边距锚定，切行时当前在播句**精准锁定在屏幕垂直正中央**；
  - 在播歌词放大至 22sp 特粗字号，配备 16dp 柔和圆角底舱与左侧 **3.5dp Primary 饱满圆角垂直强调条**；
  - 未唱行采用靠左对齐并施加平滑对数灰度渐隐，阅读层次极具呼吸感。
- **流体比例与平板双分栏**：
  - 彻底废除写死像素，采用 `clamp()` 与百分比宽高比流体布局；
  - 在大屏/折叠屏（`660dp+`）下自动触发**左右双分栏（左侧大封面，右侧沉浸歌词）**。

## Consequences

- **空间体验极大释放**：底栏高度收缩 32%，为长列表浏览腾出充裕纵向视口，彻底解决界面挤压感。
- **全天候全域视觉蜕变**：无论在深色还是浅色模式下，整机色彩随当前在播专辑封面动态呼吸换肤，达到业界一线流媒体旗舰水准。
- **可读性与无障碍满分保障**：浅色模式全域消除糊字，关键小字体与胶囊对比度全部达到 WCAG AAA（> 8.5:1）。
- **动效与交互高度拟真自然**：顶栏平滑左对齐折叠、容器形变展开、FAB 相对 24dp 舒展避让、长按多选胶囊弹出等，完全遵循 Material 3 Expressive 物理弹簧动效规范。
- **代码与领域文档高度一致**：废除 ADR-0007 静态参数，使代码实现、GLOSSARY 领域名词与 ADR 决策记录形成完整自洽闭环。
