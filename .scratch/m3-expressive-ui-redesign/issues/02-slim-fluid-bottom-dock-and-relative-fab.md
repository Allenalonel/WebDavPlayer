# 02: 紧凑流体底栏 (114dp) 与 Scaffold 相对 24dp 避让 FAB

**What to build:** 
用户在应用内浏览时，底部的 NavigationBar 高度收敛至 58dp，悬浮在上方播放的 MiniPlayer 胶囊瘦身为 52dp，底栏总高度降至 114dp，为页面长列表释放出 54dp（约 32%）的可视纵向空间。同时，在歌单与服务器界面，右下角的 FAB 悬浮新建按键采用基于 Scaffold 的相对流体堆叠布局，与下方的 MiniPlayer 保持 24dp 舒展黄金呼吸间距；当 MiniPlayer 出现或隐藏时，FAB 自动平滑相对升降，两者的相对位置清晰从容，彻底消除重叠遮挡与拥挤感。

**Blocked by:** 01: 品牌名称规范化与全天候全域音轨动态取色主题系统

**Status:** ready-for-agent

- [ ] NavigationBar 高度由默认 80dp 压缩至 58dp，图标与文字内边距收敛紧凑，各页面底部内边距随之更新
- [ ] MiniPlayer 胶囊高度收窄为 52dp，四角采用 26dp 饱满圆角，内嵌微型封面与紧凑播控键
- [ ] 底部总占屏高度严格控制在 114dp，页面内容浏览视野增加约 32% 纵向空间
- [ ] 彻底废除写死绝对像素坐标，FAB 接入 Scaffold 相对流体布局体系
- [ ] MiniPlayer 存在时 FAB 相对下方保持 24dp 净空呼吸间距，MiniPlayer 隐藏时 FAB 平滑回落至导航栏上方
- [ ] 在 Compact（360dp）、Standard（392dp）及 Expanded（430dp）设备上验证底栏与 FAB 的间距稳定性
