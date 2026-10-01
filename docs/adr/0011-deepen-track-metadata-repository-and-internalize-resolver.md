# Deepen Track Metadata Repository and Internalize Resolver

## Context

在近期的缓存失效韧性（Cache Invalidation Resilience）演进中，为了防止应用清空本地缓存后死缩略图路径破坏播放器 UI 与系统通知，系统引入了缩略图存活性判断与自愈逻辑。然而在原有实现中：
1. **基础设施依赖泄漏**：`TrackMetadataRepository` 接口过于浅显，直接返回数据库中的持久化路径。会话层 `MusicPlayerAppSessionImpl` 不得不直接注入底层的 `CoverArtStorage`，在 6 处调用 `isValidThumbnailFile` 检查文件是否存在，并在会话层维护并行动行 `latestMetadataCache` 与手动触发自愈协程。
2. **虚设接缝与调用端分支**：代码库同时暴露了公共领域接口 `TrackMetadataResolver` 和 `TrackMetadataRepository`。前者仅有 `DefaultTrackMetadataResolver` 一个适配器，违背了“单一适配器代表虚设接缝，两个适配器才代表真实接缝”的设计原则；这导致上游调用方（如 `LyricsRepositoryImpl`）构造函数必须同时接受两者，并编写繁琐的条件分支逻辑。

## Decision

1. **加深 TrackMetadataRepository，内化封面存活性与自愈（Deep Repository Seam）**：
   - 将 `CoverArtStorage` 存活性校验与后台自愈完全收敛在 `TrackMetadataRepositoryImpl` 内部。
   - 对外担保：调用端（Session、Notification、UI）获取到的任何 `TrackMetadata`，只要其 `coverThumbnailPath` 非空，就必然指向物理存在的真实文件。
   - 当检测到底层文件丢失时，仓储向调用端先行同步返回封面为空的自洽元数据（杜绝死图或加载异常），同时内部自主排队发起后台异步 HTTP Range 提取与缩略图落盘，完成后写回数据库并通过 Room 数据流自然广播刷新。
2. **折叠 TrackMetadataResolver 为内部网络提取适配器（Internalize Resolver）**：
   - 撤销 `TrackMetadataResolver` 的公共顶级领域接口地位。
   - 保留 `DefaultTrackMetadataResolver` 作为 `internal` 实现类，作为 `TrackMetadataRepositoryImpl` 专用的私有网络适配器。
   - 净化 `LyricsRepositoryImpl`，仅依赖唯一的深层 `TrackMetadataRepository` 接口，消除外部双重依赖与 `when` 分支。
3. **净化会话层 MusicPlayerAppSessionImpl（Purify Domain Session）**：
   - 彻底移除 `MusicPlayerAppSessionImpl` 中对 `CoverArtStorage` 的依赖，删除 `isValidThumbnailFile`、`sanitizeTrackWithCoverValidation` 及临时二级缓存 `latestMetadataCache`。
   - 会话层只消费深层仓储接口，将全部元数据一致性心智卸载至仓储层。

## Consequences

- **高局部性（Locality）**：封面失效检测、数据修复与自愈并发控制统一收归在元数据仓储模块内部，会话层不再承担底层文件系统检查的职责。
- **高杠杆（Leverage）**：单一深层接口同时服务播放会话、前台通知、歌词探测与目录浏览，所有调用端免费享受零文件丢失担保。
- **消除虚设接缝**：外部领域接缝减少一个，避免上层模块多头注入与测试时的双重 Mock。
