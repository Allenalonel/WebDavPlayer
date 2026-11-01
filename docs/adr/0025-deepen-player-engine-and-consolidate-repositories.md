# 0025: 深化播放引擎与收敛仓储层 (Deepen Player Engine and Consolidate Repositories)

## Context

在先前的架构审查中，系统的底层播放核心与数据仓储层暴露了多处虚设接缝、假性委托、跨层强转及横向数据依赖缺陷：

1. **`WebDavMediaSourceAdapter` 虚设适配器接缝**：
   - 接口仅有 `DefaultWebDavMediaSourceAdapter` 单一实现，违背了“单一适配器代表虚设接缝，两个适配器才代表真实接缝”的核心准则；
   - 其暴露的 `createMediaSource` / `getDataSourceFactory` 等方法全系统仅由 `Media3AudioPlayerEngine` 单一消费，却穿透层层构造函数污染了 `PlaybackSessionHost`；
   - 单元测试（`WebDavMediaSourceAdapterTest`）因接口未能暴露内部参数，多达 10 处被迫使用 `adapter as DefaultWebDavMediaSourceAdapter` 强制向下转型，单测极其脆弱。

2. **`PlaybackSessionHost` 伪装实现引擎接口与启动循环依赖**：
   - `PlaybackSessionHost` 通过 `AudioPlayerEngine by playerEngine` 伪装实现底层引擎，并在内部留有 `seekToNext()` / `seekToPrevious()` 等空实现；
   - 由于宿主需要持有会话派发系统播控意图，而会话又通过 `playerEngine` 持有宿主，导致 `WebDavApplication` 启动时必须通过 `(playerEngine as? PlaybackSessionHost)?.bindAppSession(...)` 进行运行时强转与反向绑定，存在启动死锁与断链隐患。

3. **歌词与元数据两套仓储撕裂与双重缓存**：
   - `TrackMetadata` 领域模型中本已包含 `lyrics` 字段，但系统额外定义了 `LyricsRepository` 与 `LyricsRepositoryImpl`；
   - `LyricsRepository` 仅有 `PlaybackMetadataCoordinator` 单一生产调用方，其内部在回退提取内嵌歌词时反向依赖 `TrackMetadataRepository`；
   - 两个仓储各自独立维护缓存（`LyricsRepository` 独立维护 100 条 LRU 缓存，`TrackMetadataRepository` 维护 Room 数据库与内存缓存），不仅浪费内存，且造成单测需双重 Mock。

4. **`DirectoryRepository` 废弃死接口与 CUE 跨包私有类侵入**：
   - 在 ADR-0024 引入 `observeDirectoryContent` 后，原有的 `observeDirectory` 与 `listDirectory` 已无生产调用；
   - `DirectoryRepositoryImpl` 为了生成初始 CUE 占位骨架，直接跨包调用了 `data/cue/CueAssociationHelper` 私有类，破坏了 CUE 领域模块的信息隐藏。

5. **服务器注销孤儿数据滞留**：
   - 删除服务器时仅删除了服务器账号与封面文件，`directory_cache` 与 `track_metadata` 滞留永久孤儿记录。

## Decision

落实深模块（Deep Module）设计准则，对播放内核与仓储层进行四大维度收拢深化：

### 1. 内化 WebDavMediaSourceAdapter 至底层播放引擎
- 撤销 `WebDavMediaSourceAdapter` 顶级接口地位，将其内部化为 `Media3AudioPlayerEngine` 的内部私有协作者（`internal class WebDavMediaSourceBuilder`）；
- `Media3AudioPlayerEngine` 直接管理专属 OkHttp 客户端、数据源工厂与流式媒体源构建，对外仅暴露窄小的 `AudioPlayerEngine` 接口；
- 净化 `PlaybackSessionHost`，彻底移除对其构造参数的污染；
- 彻底拔除测试中全部 10 处 `as DefaultWebDavMediaSourceAdapter` 向下强转。

### 2. 解除 PlaybackSessionHost 假性委托，理顺系统宿主角色
- 移除 `PlaybackSessionHost` 对 `AudioPlayerEngine` 的类委托（`by playerEngine`），彻底删除空实现的伪跳曲方法；
- `PlaybackSessionHost` 专职作为 Android 系统前台服务、`MediaSession` 与 `AudioFocus` 的系统级宿主适配器；
- 会话层 `MusicPlayerAppSessionImpl` 直接注入纯物理流引擎 `Media3AudioPlayerEngine`；
- 宿主构造时或启动时显式单向绑定 `MusicPlayerAppSession`，彻底根除启动期 `(playerEngine as? PlaybackSessionHost)` 运行时强转。

### 3. 折叠双源歌词解析至 TrackMetadataRepository
- 在深层 `TrackMetadataRepository` 接口中暴露统一的歌词查询方法：
  `suspend fun resolveLyrics(server: WebDavServer, track: AudioTrack): Lyrics`；
- 依据删除测试彻底删除 `LyricsRepository` 接口及 `LyricsRepositoryImpl` 类；
- 将同目录 `.lrc` 探测与内嵌歌词回退统一收拢至元数据仓储内部，废弃独立的 100 条二级歌词内存缓存，统一复用元数据仓储持久化与缓存通道；
- `PlaybackMetadataCoordinator` 仅依赖单一的 `TrackMetadataRepository`。

### 4. 剪除 DirectoryRepository 遗留接口并收纳 CUE 骨架生成
- 剪除 `DirectoryRepository` 中废弃的 `observeDirectory` 与 `listDirectory` 接口及实现，统一以 `observeDirectoryContent` 为核心测试表面；
- 在 `CueSheetResolver` 接口中扩充非阻塞初始骨架探测能力（`resolveInitialCueAlbums(directory: RemoteDirectory): List<CueAlbumItem>`），封堵仓储对 `CueAssociationHelper` 的越权跨包侵入；
- 在 `ServerRepositoryImpl.deleteServer(id)` 中增加对关联 `directory_cache` 与 `track_metadata` 数据库记录的原子级联删除：
  - **外键权衡与否决（Trade-off & Rejection）**：团队曾推敲是否在 `directory_cache` 与 `track_metadata` 实体声明 SQLite 级强外键（`@ForeignKey(onDelete = CASCADE)`）。实际落地验证证实，数据库级强外键与**跨服务器歌单（ADR-0020）及离线曲目自愈（ADR-0022）**存在根本冲突：当服务器处于离线、凭据失效或被移除状态时，歌单条目以 `InactivePlaylistTrack`（置灰减淡音轨）存在，系统仍须安全读写其快照标签；若施加数据库强外键，不仅会连坐抹杀歌单中仅存的歌曲名称与时长信息导致死链，还会诱发 `SQLiteConstraintException` 崩溃。因此，级联删除由 `ServerRepositoryImpl.deleteServer` 在领域层显式原子协调（物理封面文件、网络客户端缓存、目录快照与歌曲元数据），底层缓存实体表严禁添加硬外键。

## Consequences

- **消灭虚设接缝（Zero Hypothetical Seams）**：彻底清除 `WebDavMediaSourceAdapter` 与 `LyricsRepository` 两个无实际多态价值的单一适配器接口；
- **消灭全部测试与启动强转（Zero Downcasts）**：拔除 10 处单测强转以及启动期的 `as? PlaybackSessionHost` 强转；
- **高局部性与强内聚**：元数据与歌词完全归口单一仓储，媒体源组装归口引擎，系统宿主专职系统服务生命周期；
- **缓存弹性与领域自洽**：明确确立缓存表不设底层硬外键约束，既确保用户删除服务器时磁盘与数据库孤儿数据 100% 连坐清理，又完美保护跨服务器离线歌单与弱网元数据自愈能力；
- **100% 绿色回归验证**：现有单元测试套件全部平滑迁移至真实接缝，所有核心业务类严格维持在 600 行红线以内。
