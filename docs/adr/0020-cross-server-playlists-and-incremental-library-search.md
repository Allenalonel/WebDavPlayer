# 0020: 跨服务器本地歌单与增量曲库即时搜索 (Cross-Server Playlists and Incremental Library Search)

## Context

在 WebDavPlayer 的初始架构中，曲库浏览与回放完全基于单一活跃服务器与单一目录的闭环拓扑（`Folder Ring Playback`，见 ADR-0014）。然而，随着用户私有云音乐资产的扩充，这一局限暴露出两大核心体验瓶颈：

1. **曲目组织断层（无法跨目录/跨服务建歌单）**：
   - 用户无法将分散在不同专辑文件夹、甚至不同 WebDAV 服务器（例如家庭主 NAS 与便携随身云）中的喜爱曲目归集为统一的播放列表（Playlist）；
   - 之前为防止跨目录混乱，播放队列（Playback Queue）被严格限制在当前目录。

2. **庞大曲库寻道困难（缺乏全局检索）**：
   - 面对成百上千个目录层级，用户查找特定歌曲必须人工逐层翻找；
   - 远程 WebDAV 全量爬虫成本高昂（频繁唤醒 NAS 硬盘、耗费流量或触碰网盘速率限制），无法直接采用全量暴力扫描。

## Decision

在坚守纯流式零磁盘持久化占用（ADR-0002）与 `<= 7.5 MB` 发布包体积硬预算（ADR-0013）的前提下，确立以下架构设计：

### 1. 跨服务器多源歌单架构 (Cross-Server Multi-Source Playlist)
- **领域模型与持久化解耦**：
  - 引入持久化领域模型 `Playlist` 与 `PlaylistTrackEntry`，与运行时瞬态的 `Playback Queue` 明确解耦。
  - 歌单音轨条目显式持久化 `serverId`、`remotePath` 以及快照标签（标题、艺术家、专辑、时长），使得单张歌单支持自由混合添加来自多个不同 WebDAV 服务器的曲目。
- **动态凭据媒体源按需流转**：
  - 切歌流转至不同服务器的音轨时，`MusicPlayerAppSession` 动态提取该音轨所属的 `Server Context`，由 `WebDavMediaSourceAdapter` 按需装配对应的 Basic/Digest 鉴权头与 SSL 套接字工厂，对上层播放管道完全透明。

### 2. 失效曲目容错与友好诊断 (Inactive Track Fault-Tolerance & Toast Feedback)
- **多状态置灰呈现**：
  - 当所属 WebDAV Server 处于离线/未连接状态，或远程音频文件在流式探测时遭遇 HTTP 404（已被外部重命名、移动或删除）时，歌单 UI 响应式将对应曲目置灰，并禁用普通播放点击。
- **点击诊断与原因反馈**：
  - 用户点击置灰曲目时，弹出精准的诊断 Toast（如“所属服务器 [ServerName] 无法连接”或“音频文件已被重命名或删除”），告知用户不可用原因。
- **自动化流转静默跳过**：
  - 回放队列遇到失效音轨或瞬态 404 时，由 `MusicPlayerAppSession` 统一拦截并静默平滑跳跃至下一首有效曲目，坚决杜绝因单曲异常导致整个歌单报错崩溃或回放管线卡死。

### 3. SWR 增量全文搜索与分流交互 (SWR-Backed Instant Search & Split Navigation)
- **顶栏常驻 MD3 搜索入口**：
  - 在曲库顶栏（TopAppBar）右侧常驻搜索操作入口，点击平滑展开 Material 3 沉浸式搜索栏（SearchBar），提供即时输入过滤与历史搜索词记录；在任意深层目录均可一键即时呼出。
- **双模增量索引**：
  - 全局搜索优先依托本地 Room 数据库中已访问目录的 SWR 镜像快照（`directory_cache`）与元数据表（`track_metadata`）提供毫秒级全文检索，零远程网络开销与 NAS 唤醒；
  - 在偏好设置中提供可选的“曲库后台增量索引”开关，仅在用户显式开启且连接 Wi-Fi 时温和递归 PROPFIND 补充快照。
- **搜索结果差异化交互**：
  - **命中目录 (Remote Directory)**：点击后穿梭导航至所属目录视图，自动联动顶部胶囊面包屑（Breadcrumb Strip）；
  - **命中音频文件 (Audio Track)**：执行**即时插队播放 (Ad-hoc Queue Insertion / Play Next)**，将选定曲目插队至当前播放队列下一首或单曲即时起播，不摧毁现有队列的目录或歌单主体结构。

### 4. 歌单层级架构与编排交互 (Playlist Navigation & Interaction Mechanics)
- **底栏独立“歌单”一级页面**：
  - 顶层导航栏（`MainNavigationCoordinator`）拓展为三栏架构（曲库 / 歌单 / 服务器），赋予歌单独立一等页面地位，便于直观管理与随时访问。
  - 在曲库列表及搜索结果的曲目更多菜单（Overflow Menu）中，提供“添加到歌单”统一入口。
- **支持拖拽换位与多维排序**：
  - 歌单详情视图内支持长按列表项进行上下拖拽重排（Drag-and-drop Reordering），实时同步持久化至数据库；同时提供按“添加时间”、“歌曲名”、“艺术家”一键多维排序。
- **CUE 虚拟分轨一等公民深度支持**：
  - 充分整合已有 CUE 虚拟时间轴引擎，虚拟分轨在全局搜索中作为独立曲目被检索呈现，且支持单独添加至任意本地歌单；播放时底层统一透明驱动单物理流无缝虚拟时间轴流转。


## Consequences

- **坚守核心红线**：歌单仅持久化结构化引用（ServerId + Path + Metadata Snapshot），音频二进制数据绝不落盘，恪守 ADR-0002 约束。
- **零引入沉重第三方库**：完全复用 Room SQLite 与现有协程并发流，不增加额外包体积，确保发布 APK 严守 7.5 MB 预算。
- **用户体验显著提升**：彻底解决找歌难、无法收藏常用歌单、跨目录断播的行业标配体验痛点。
