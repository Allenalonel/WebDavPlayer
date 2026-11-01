# Spec: 全局即时搜索与跨服务器自定义本地歌单 (Global Instant Search & Cross-Server Playlists)

Status: resolved

## Problem Statement

作为私有云 NAS/网盘音乐用户，曲库随着收藏日积月累，往往沉淀了成百上千个复杂的远程目录层级。当前 WebDavPlayer 仅提供基于物理目录树的层级浏览模式（Folder-based browsing），用户在找歌时必须层层人工滚动钻取目录；一旦忘记某首歌曲具体存放在哪个艺术家的哪张专辑文件夹下，寻道体验极为痛苦。

同时，用户收藏喜爱的曲目通常分散在不同目录、不同专辑甚至不同的 WebDAV 服务器中（例如家庭常驻 NAS、工作区便携云盘或 Alist 挂载源）。由于当前播放器缺乏“持久化歌单（Playlist）”机制，且回放队列受到严格的目录闭环（Folder Ring Playback）限制，用户无法将散落在各处的优质歌曲收录为主题歌单（如“华语经典”、“车载精选”），更无法跨目录顺畅连播。

此外，当网络波动、服务器离线或 NAS 上的源文件被外部整理重命名/删除时，主流播放器往往出现整个歌单不可用、列表崩溃报错或回放硬性中断等严重恶化体验的瑕疵；且在搜索与歌单场景下，CUE 虚拟分轨（Virtual Track）往往被降级忽略，无法独立被搜索或收藏。

## Solution

1. **增量 SWR 曲库即时搜索（SWR-Backed Instant Search）**：
   - 在曲库顶栏常驻 Material 3 沉浸式搜索入口，点击平滑展开。
   - 默认依托本地 Room 数据库中已访问目录的结构化镜像快照（`directory_cache`）与音频标签索引（`track_metadata`）提供毫秒级全文匹配，零网络开销、零 NAS 唤醒。
   - 搜索结果差异化分流：命中目录支持一键穿梭直达并联动顶部胶囊面包屑（Breadcrumb Strip）；命中音频文件执行即时插队播放（Ad-hoc Queue Insertion / Play Next），不破坏现有队列所属主体。
   - 深度支持 CUE 虚拟分轨，分轨单曲可直接独立命中搜索。

2. **跨服务器多源本地歌单（Cross-Server Multi-Source Playlists）**：
   - 顶层底栏新增独立的“歌单”一级 Tab，赋予自定义歌单直观一等的管理视窗；曲目列表与搜索结果菜单提供统一的“添加到歌单”动作。
   - 歌单音轨条目显式绑定所属 WebDAV 服务器标识与远程路径，单张歌单支持自由混合收纳来自不同服务器的普通曲目与 CUE 虚拟分轨。
   - 播放跨服务歌单时，会话层按需提取目标曲目所属的 Server Context，自动装配对应的认证凭据与 SSL 上下文，对底层流式渲染管道完全透明。

3. **失效曲目友好容错与自愈流转（Inactive Track Fault-Tolerance & Toast Feedback）**：
   - 当所属服务器离线或远程音频返回 404（已被外部重命名、移动或删除）时，歌单条目响应式置灰，点击弹出清晰明确的诊断 Toast 告知具体原因（服务器不可达 vs 文件失效）。
   - 播放流转遇到失效或 404 音轨时，会话层统一拦截并静默平滑跳跃至下一首有效音轨，杜绝卡死中断与歌单崩溃，并支持一键清理失效音轨。

4. **高度自由的歌单编排（Drag-and-Drop Reordering & Multi-Dimensional Sorting）**：
   - 歌单详情视图内支持长按列表项上下拖拽重排，顺序变更实时持久化；
   - 提供按“添加时间”、“歌曲名”、“艺术家”一键多维排序。

## User Stories

1. 作为用户，我希望在曲库浏览界面的顶栏右侧能看到清晰的搜索图标，以便在任意深层目录均能一键呼出搜索功能。
2. 作为用户，当我点击搜索图标时，我希望界面平滑展开 Material 3 沉浸式搜索栏，并将输入光标自动聚焦，以便我立即开始键入关键字。
3. 作为用户，在搜索框中键入曲名、歌手名、专辑名或文件夹名称时，我希望搜索结果能在毫秒内即时联动更新，无明显界面卡顿。
4. 作为用户，我希望搜索匹配对大小写不敏感，并自动忽略首尾多余空格，以便输入时更加自由从容。
5. 作为用户，在搜索结果列表中，我希望清晰区分“文件夹目录”与“音频曲目”两类条目，以便准确获取我想要的目标类型。
6. 作为用户，当搜索命中 CUE 虚拟分轨时，我希望能在结果中看到具体的单曲标题、艺术家与所在大文件名称，以便直接定位特定分轨。
7. 作为用户，当我点击搜索结果中的某个“文件夹目录”时，我希望应用立即退出搜索并穿梭导航至该目录，同时顶部胶囊面包屑路径条联动更新为该目录层级。
8. 作为用户，当我点击搜索结果中的某个“音频文件”或“虚拟分轨”时，我希望播放器能将该歌曲即时插队（Play Next）到当前播放队列并起播，而不摧毁我当前正在收听的目录或歌单主体结构。
9. 作为用户，我希望搜索栏提供一键清空按钮，并能记录我最近搜索的关键词历史，以便下次快速重用高频搜索。
10. 作为私有云用户，我希望默认搜索完全基于手机本地已缓存的快照，不向 NAS 发起暴力的全盘扫描，以免频繁唤醒 NAS 硬盘或耗费宝贵流量。
11. 作为用户，我希望在底部导航栏以「歌单 — 媒体库 — 服务器」顺序快速切换，并且 APP 冷启动时能自动恢复到上次退出时停留的底栏位置。
12. 作为用户，在“歌单”页面中，我希望能够点击醒目的悬浮按钮或操作项新建歌单，并自定义歌单名称。
13. 作为用户，在曲库目录、搜索结果或当前播放队列中，当我点击任意歌曲的三点菜单时，我希望看到“添加到歌单”选项，以便随时将喜欢的歌曲收藏起来。
14. 作为拥有多个 WebDAV 节点（如家庭 NAS 与办公室网盘）的用户，我希望同一个歌单能够混合收藏来自不同服务器的歌曲，以便将我最爱的音乐汇总在一处。
15. 作为用户，在歌单页面中，我希望能够对歌单进行重命名或删除操作，删除歌单时能弹出二次确认，防止误触。
16. 作为用户，我希望歌单支持添加 CUE 虚拟分轨，以便我能将大型交响乐或演唱会中的某首精彩片段单独收藏进精选歌单。
17. 作为用户，在歌单详情页中，我希望顶部直接展示大字标题与「播放全部」及「随机播放」双操作药丸按键（不放置笨重的大海报框），顶栏不重复堆叠名称。
18. 作为用户，在歌单歌曲列表中，常规模式下我希望界面纯净，仅展示歌曲序号、44dp 方形专辑封面海报与歌曲信息，隐藏拖拽手柄；当在排序菜单中点击「自定义排序」时，才显现拖拽手柄并于顶栏提供「保存」按钮，支持随意跨多曲连续上下拖拽排序。
19. 作为用户，当我完成拖拽重排并点击保存后，我希望新的顺序能立即自动持久化保存，且手柄恢复隐藏，拖拽过程不出现闪烁。
20. 作为用户，我希望歌单详情页提供排序菜单，支持一键按「自定义排序」、「添加时间正序/倒序」、「歌曲标题」、「艺术家」重排歌单。
21. 作为用户，在歌单歌曲上向右滑动超过 50% 并伴随震动松手时，能将歌曲从歌单中移出（若滑回小于 50% 则安全取消）；向左滑动超过 50% 并伴随震动松手时，将歌曲添加到下一首播放；点击每首歌的三点菜单可查看该歌曲的「详细信息」对话框（包含来源目录与完整路径）。
22. 作为用户，当某个 WebDAV 服务器离线、密码失效或无法连接时，我希望歌单中属于该服务器的歌曲显示为灰色，且不发生歌单加载崩溃。
23. 作为用户，当我点击置灰的不可用歌曲时，我希望屏幕弹出清晰的 Toast 提示，明确告诉我无法播放的具体原因（如“所属服务器无法连接”或“音频文件已被重命名或删除”）。
24. 作为用户，当播放整张歌单遇到置灰歌曲或网络 404 时，我希望播放器能自动静默平滑跳跃至下一首有效歌曲继续播放，绝不发生中断或抛出未捕获异常。
25. 作为用户，当歌单中累积了因 NAS 文件清理而失效的歌曲时，我希望歌单菜单提供一键“清理失效歌曲”选项，以便一键维护歌单纯净度。

## Implementation Decisions

### 1. 领域模型与实体解耦 (Domain Models & Entities)
- **Playlist 实体**：定义纯领域模型 `Playlist(id, name, createdAtMs, updatedAtMs, trackCount)`，彻底与瞬态的 `PlaybackQueue` 运行时会话解耦。
- **PlaylistTrackEntry 实体**：定义歌单条目模型，显式持久化 `(playlistId, serverId, remotePath, sortOrder, title, artist, album, durationMs, format, coverThumbnailPath, cuePath, virtualTrackId)`，使音轨即便在远程离线时依然具备完整的界面展示元数据快照。
- **InactiveReason 诊断枚举**：定义状态属性 `isInactive: Boolean` 与诊断枚举 `InactiveReason { SERVER_UNREACHABLE, FILE_NOT_FOUND, SERVER_CREDENTIAL_INVALID }`，为 UI 置灰与 Toast 反馈提供结构化数据支撑。
- **Queue Origin 概念引入**：在 `PlaybackQueue` 中引入队列来源标记（`DirectoryQueue` vs `PlaylistQueue` vs `AdHocQueue`），当处于 `PlaylistQueue` 时，切歌循环边界为歌单所有有效曲目；处于 `DirectoryQueue` 时保持原有的 `Folder Ring Playback` 闭环。

### 2. 持久化层与数据库演进 (Room Persistence & Migration)
- **数据表新增**：在 `AppDatabase` 中增加实体：
  - `playlists`（主键 `id: Long` 自增，`name: TEXT`，`createdAtMs: Long`，`updatedAtMs: Long`）；
  - `playlist_tracks`（复合索引 `(playlistId, sortOrder)` 与外键级联删除 `playlistId`，包含 `serverId`、`remotePath` 与快照元数据）。
- **轻量迁移策略**：执行标准数据库 Migration（版本 4 $\to$ 5），保证老用户已有的 SWR 目录缓存与服务器列表零丢失。
- **Room FTS / 索引强化**：为 `directory_cache` 的 `path` 与 `track_metadata` 的 `title`、`artist`、`album` 字段添加全文或 B-Tree 前缀复合索引，保障万级数据量下的检索耗时小于 10 毫秒。

### 3. 曲库搜索引擎与增量装载 (Library Search Seam)
- **LibrarySearchRepository 接缝**：定义专职领域仓储接口 `LibrarySearchRepository`，对外暴露 `search(query: String): Flow<LibrarySearchResult>`。
- **双模结果归集**：`LibrarySearchResult` 包含 `matchedDirectories: List<RemoteDirectory>` 与 `matchedTracks: List<AudioTrack>`（含虚拟分轨解析）。
- **分流调度契约**：
  - 目录命中项被点击后，通知 `MainNavigationCoordinator` 导航并驱动 `DirectoryBrowserViewModel.loadDirectory(path)`，自动刷新顶部面包屑；
  - 音频命中项被点击后，通过 `MusicPlayerAppSession.playNext(track)` 执行即时插队起播，不替换当前播放队列的背景来源。

### 4. 会话层动态凭据切换与容错流转 (Session Multi-Server Progression)
- **动态 Server Context 路由**：在 `MusicPlayerAppSessionImpl` 加载音轨（`loadTrackIntoEngine`）时，根据 `track.serverId` 动态检索 `WebDavServerRepository`：
  - 若目标服务器未启用或不存在，抛出受控的 `ServerUnavailableException`，会话层将其判定为失效音轨并自动调度 `skipToNext()`；
  - 若服务器正常，由 `WebDavMediaSourceAdapter` 即时组装携带该服务器凭证与自签名 SSL 套接字的 `Authenticated MediaSource`。
- **404 / 失效熔断防御**：当底层流式请求返回 404（`InvalidResponseCodeException: 404`）时，`MusicPlayerAppSession` 拦截错误，在本地数据库将该条目置位不可用，并平滑触发下一首调度，杜绝重连看门狗陷入死循环。

### 5. 表现层与导航架构扩展 (UI & MD3 Navigation)
- **顶层导航扩展与冷启动记忆**：`MainNavigationCoordinator` 扩展底栏导航枚举：`AppDestination.PLAYLISTS`（歌单）、`AppDestination.DIRECTORY_BROWSER`（媒体库）、`AppDestination.SERVER_LIST`（服务器），并通过 `webdav_nav_prefs` 持久化记忆最后停留的目的地，冷启动时精准复原。
- **媒体库顶栏与极简通透搜索栏**：
  - 媒体库（`DirectoryBrowserScreen`）顶栏去除冗余的左上角返回图标，由面包屑胶囊条与系统手势承接；右上角从左到右依序为「刷新」与「搜索」；
  - 搜索视图采用全通透极简沉浸设计（无通栏底部分割横线），内嵌浅灰圆角输入框与右侧「取消」文字按钮；搜索历史记录持久化至本地存储，支持全局跨服务器已缓存快照毫秒级检索。
- **歌单详情排版与封面海报**：
  - 歌单详情顶部去除大方块海报，改用大字标题与「播放全部」/「随机播放」双药丸操作按键；顶栏去除重复的歌单小标题；
  - 歌曲条目回归原生 MD3 沉浸式 `ListItem`，每行集成 44dp 专辑封面海报，并在后台自动自愈提取缺失的封面；
  - 隐藏常驻拖拽手柄，仅在排序菜单选中「自定义排序」时才显示拖拽手柄与顶栏「保存」按键，解耦动态索引以支持无中断跨多曲连续拖拽；
  - 左右滑动采用严格 50% 最大物理宽度阈值与触感震动反馈（左滑下一首播放，右滑从歌单中删除，滑回 < 50% 自动取消）；三点菜单升级为「详细信息」对话框。

## Testing Decisions

### 1. 良好测试准则
- **行为驱动（Behavior-driven）**：测试完全针对模块公开接缝（Public Seams）与用户可观察的领域状态流，严禁测试 Room SQL 拼装细节或 ViewModel 内部私有变量。
- **零网络/零磁盘依赖**：使用 Mock WebServer 或虚拟 InMemory Room 数据库，所有异步协程使用 `StandardTestDispatcher` 执行确定性调度。

### 2. 测试覆盖矩阵
- **PlaylistRepository 契约测试**：
  - 歌单 CRUD 及重命名，级联清理歌单条目；
  - 混合添加不同服务器的曲目（含 CUE 虚拟分轨）；
  - 拖拽重排与按时间/标题/艺术家排序后索引的绝对单调递增性；
  - 模拟服务器离线状态，断言 `isInactive` 与 `inactiveReason` 的准确计算。
- **LibrarySearchRepository 检索测试**：
  - 模糊匹配大小写不敏感、中英文混合检索；
  - 空输入、单字符输入、特殊符号防 SQL 注入；
  - 准确分离返回目录列表与音频曲目列表。
- **MusicPlayerAppSession 跨服务器流转与容错测试**：
  - 验证歌单中连续两首分别来自服务器 1 与服务器 2 的曲目，切歌时底层 `MediaSourceAdapter` 是否正确切换了凭证与 BaseUrl；
  - 验证当遇到失效条目或 404 响应时，会话层是否安全自动跳至下一首有效曲目；
  - 验证搜索结果单曲点选时，当前已有队列未被清空且新曲目精准插入当前位置之后。
- **端到端与包体积门禁测试**：
  - `EndToEndMusicPlayerPipelineTest` 扩充歌单创建、添加音轨、起播整张歌单全链路回归；
  - 执行 `verifyReleasePackaging` 持续守门：Release APK 严格约束在 `<= 7.5 MB`，`classes.dex <= 4.0 MB`。

### 3. 代码库先例 (Prior Art)
- 参考 `DirectoryRepositoryTest` 与 `TrackMetadataRepositoryTest` 的 Room/SWR 单元测试设计模式。
- 参考 `EndToEndPlaybackPipelineIntegrationTest` 的多曲目调度与断点自愈测试范式。

## Out of Scope

- **在线全量音乐库爬虫**：绝不默认在后台向 WebDAV 发起未受控的暴力全盘遍历，仅基于已访问 SWR 缓存检索（严格保护 NAS 硬盘与手机流量）。
- **音频二进制离线下载落盘**：恪守 ADR-0002 纯流式零磁盘持久化原则，歌单仅保存结构化音轨元数据引用，音频本身绝不持久化落盘。
- **远端 WebDAV 服务器上的歌单写入**：不向远程服务器写入任何私有文件或反向修改远程文件夹结构，所有歌单数据纯本地持久化。
- **第三方私有协议库引入**：不引入 SMB、Plex、Jellyfin 等重型第三方 SDK，杜绝二进制体积膨胀。

## Further Notes

- **弱网与断网体验**：当手机完全断网时，用户仍可顺畅浏览已创建的歌单列表与搜索已缓存的曲目快照；仅在点击起播时触发提示，提供优雅的降级体验。
- **冷启动与会话恢复**：`PlaybackSessionState` 同步支持记录当前队列是否来源于某个自定义歌单，冷启动后能够完整复原歌单播放上下文。
