# 06: 全屏方形大封面、双语动感歌词与多端响应式双分栏

**What to build:** 
用户点击 MiniPlayer 播控栏时，以平滑的容器形变（Container Morphing）展开为全屏大播放器视图。播放器核心告别老旧的圆形黑胶唱片，升级为现代大气的 28dp 方形圆角大卡片大封面（带对应色系柔和环境光晕与 clamp 流体宽高比）。在动感歌词界面中，当前播唱句以粗体放大并带有主题色发光高亮，非当前句微透渐隐，用户点击任意一句歌词即可精准 Seek 跳转到该时间点。在折叠大屏或平板设备（660dp+ 宽屏模式）下，全屏播放器自动自适应为左右双栏分屏（左侧方形大封面，右侧沉浸歌词），充分利用大屏横向视野；全屏视图内滑动手势与歌词上下滑动毫无阻塞。

**Blocked by:** 01: 品牌名称规范化与全天候全域音轨动态取色主题系统, 02: 紧凑流体底栏 (114dp) 与 Scaffold 相对 24dp 避让 FAB

**Status:** resolved

- [x] 点击 MiniPlayer 时触发物理容器形变展开动效（Container Morphing），收起时原路平滑缩回 MiniPlayer 胶囊
- [x] 顶部落地“正在播放自 WebDAV”轻量纯净顶栏，集成返回把手与分享菜单
- [x] 全屏播放器核心升级为 28dp 方形圆角大卡片大封面（Squircle 250x250），外带与当前专辑色呼应的环境光晕柔和投影
- [x] 播控主按键升级为 M3E 标志性的 68dp Squircle（26dp 圆角方块）主播放键，左右伴随 FilledTonal 药丸切歌按键，彻底淘汰原本简陋的小圆圈键
- [x] 彻底废除写死像素，封面卡片采用比例与 clamp() 响应式自适应伸缩，在任何手机屏幕比例下黄金居中留白
- [x] 双语歌词界面高亮当前播唱句并放大字号，非当前句优雅微透，点击任意歌词行实现毫秒级精准 Seek 跳转
- [x] 遵循 WindowSizeClass 标准：在宽屏与折叠屏/平板模式（660dp+）下，全屏播放器自动自适应为左右双栏并排分屏
- [x] 必须与 `.tmp/prototype_m3_expressive.html` 交互原型进行像素级视觉保真度核验，大卡片与 68dp Squircle 播控键形态 100% 吻合
- [x] 确保全屏下滑收起手势与歌词 LazyColumn 上下滑动互不冲突、丝滑顺畅
- [x] 严格保证既有播放器核心播控与守护功能零破坏：厂商电池优化白名单常驻横幅（`BatteryWhitelistBanner`）及跳转忽略电池优化弹窗、弱网自动重试横幅（`FullPlayerRetryBanner`）、播放模式轮转（单曲/列表/随机）、进度滑块拖拽 Seek、不支持 Range 重播弹窗及 `HoistedPlaybackQueueSheet` 唤起 100% 完整保留
- [x] 针对歌词点击 Seek 与多端断点分屏切换进行针对性测试验收

## Answer

- **轻量顶部纯净栏**：落地“正在播放自 WebDAV”轻量纯净顶栏，集成折叠返回按钮把手（`AppIcons.KeyboardArrowDown`）、当前专辑信息与添加到歌单更多操作按键，向下拖拽或点击把手均可平滑收起。
- **28dp 方形圆角大卡片与环境光晕**：`PlayerVinylArtwork` 升级为 28dp Squircle（`FullPlayerDefaults.ArtworkSquircleShape`），加入 24dp 柔和环境光晕投影（联动主题主色），并以 `sizeIn(minWidth = 190.dp, maxWidth = 280.dp)` 响应式伸缩自适应黄金居中留白。
- **68dp Squircle 播控与 FilledTonal 药丸切歌按键**：主播放/暂停键升级为 68dp Squircle（26dp 圆角，8dp 环境光晕阴影），左右伴随 48dp FilledTonal 药丸切歌键（`FullPlayerDefaults.SkipButtonShape`），完全对齐 `.tmp/prototype_m3_expressive.html` 交互原型。
- **双语动感歌词发光与精准 Seek**：当前播唱句放大加粗（20sp Bold）、着色 Primary 并添加环境发光背景胶囊，非当前句微透渐隐；点击任意歌词行精准 Seek 跳转。
- **660dp+ 响应式双分栏**：在 `FullPlayerDefaults.TabletWideBreakpoint`（660dp+）下，自动自适应为左右并排双分栏（左侧：方形封面、歌曲元数据与播控控件；右侧：沉浸式双语动感歌词），小屏自动切换为横向 Pager 与分页圆点。
- **核心守护功能零破坏**：完整保留厂商电池优化白名单横幅、弱网重试横幅、单曲/列表/随机模式轮转、滑块 Seek 与播放队列抽屉。
- **定向测试验证**：`FullPlayerPresentationTest` 与 `LyricsViewPresentationTest` 覆盖 68dp 主播放键、28dp 封面圆角、660dp 响应式断点及动感歌词排版验证，测试 100% 通过。
