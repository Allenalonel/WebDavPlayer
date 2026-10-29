# 02: 跨服务器歌单持久化与底栏管理 (Cross-Server Playlist Persistence and Management)

**What to build:** 数据库无损迁移升级（版本 4 $\to$ 5），建立 `playlists` 与 `playlist_tracks` 持久化表，支持单张歌单混合收纳来自不同 WebDAV 服务器的普通音频曲目与 CUE 虚拟分轨（持久化快照元数据与所属 `serverId`）。主界面底栏导航扩展独立的“歌单”一级 Tab，提供歌单卡片列表展示、新建歌单对话框与重命名/删除管理；在曲库列表、搜索结果及当前播放队列的曲目菜单中增加统一的“添加到歌单”选项。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] 实现 Room 数据库版本 4 $\to$ 5 平滑迁移（`MIGRATION_4_5`），新增 `playlists` 与 `playlist_tracks` 表，保持老版本 SWR 目录缓存与服务器列表无损
- [x] 构建 `PlaylistRepository` 领域仓储接缝，提供歌单 CRUD、音轨批量追加及级联删除接口
- [x] 歌单音轨条目显式持久化 `serverId`、`remotePath` 以及标题、艺术家、专辑、时长快照与 CUE 虚拟分轨标识
- [x] 主底栏导航（`MainNavigationCoordinator`）拓展为三栏架构（曲库 / 歌单 / 服务器），新增独立的歌单广场视图（`PlaylistListScreen`）
- [x] 歌单广场提供新建歌单浮动操作按钮（FAB），支持输入歌单名称并做空值与重名校验；提供歌单卡片重命名与带二次确认的删除操作
- [x] 在曲库目录浏览、搜索结果列表及播放队列抽屉的歌曲三点菜单中，增加“添加到歌单”操作弹窗，支持勾选已有歌单或即时新建并添加
- [x] 编写针对 `PlaylistRepository` 契约与跨服务器音轨持久化的全量单元测试

## Answer
- 实现了 Room 数据库从版本 4 到 5 的平滑迁移（`MIGRATION_4_5`），新增 `playlists` 与 `playlist_tracks` 表及级联删除与复合索引。
- 构建了纯领域模型 `Playlist`、`PlaylistTrackEntry` 以及仓储接缝 `PlaylistRepository` / `PlaylistRepositoryImpl`。
- 将主界面底栏导航扩展为三栏架构（媒体库、歌单、服务器），新增 `PlaylistListScreen` 与 `PlaylistListViewModel`，提供歌单卡片展示、新建歌单 FAB、重命名与删除二次确认。
- 在曲库列表、搜索结果及当前播放队列的曲目菜单中新增统一的 `AddToPlaylistDialog` 弹窗。
- 编写并通过了 `DatabaseMigrationTest`、`PlaylistRepositoryTest`、`PlaylistListViewModelTest`、`MainNavigationCoordinatorTest` 全量测试。

