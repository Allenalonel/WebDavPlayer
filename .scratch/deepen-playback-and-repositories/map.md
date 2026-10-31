# Feature Map: Deepen Playback Engine and Consolidate Repositories

## Summary
阶段一（底层与数据层）：深化播放引擎内核并收敛仓储层接缝。
彻底消除单一适配器虚设接缝、假性委托、启动期循环强转与跨仓储横向耦合。

## Decisions So Far
- **ADR-0025**: 《0025: 深化播放引擎与收敛仓储层 (Deepen Player Engine and Consolidate Repositories)》
  - 媒体源构建内化为 `Media3AudioPlayerEngine` 私有协作者，消除 `WebDavMediaSourceAdapter` 虚设接缝。
  - 剥离 `PlaybackSessionHost` 伪引擎委托，以纯物理流引擎直连会话，彻底消除启动期 `as? PlaybackSessionHost` 强转。
  - 双源歌词下沉至 `TrackMetadataRepository`，删除 `LyricsRepository` 及其独立 100 条 LRU 缓存。
  - 剪除 `DirectoryRepository` 废弃的 `observeDirectory` / `listDirectory` 死接口，CUE 初始骨架由 `CueSheetResolver` 闭环生成。
  - 在 `ServerRepository.deleteServer` 中实现事务级联清除 `directory_cache` 与 `track_metadata` 孤儿记录。
- **Ticket 01 Decision**: 媒体源组装与连接池管理收拢至 `WebDavMediaSourceBuilder` 并由 `Media3AudioPlayerEngine` 私有协作者持有，废除 `WebDavMediaSourceAdapter` 虚设接缝，清除全量 10 处测试强转与宿主参数穿透。详见 [.scratch/deepen-playback-and-repositories/issues/01-fold-media-source-adapter-into-player-engine.md](issues/01-fold-media-source-adapter-into-player-engine.md)。
- **Ticket 02 Decision**: 剥离 `PlaybackSessionHost` 的 `by playerEngine` 假性类委托，清除 `ForwardingPlayer` 空桩并对接 `appSession`；会话直连纯 `Media3AudioPlayerEngine`，启动期彻底消除 `as? PlaybackSessionHost` 向下转型。详见 [.scratch/deepen-playback-and-repositories/issues/02-decouple-playback-session-host-and-break-circular-loop.md](issues/02-decouple-playback-session-host-and-break-circular-loop.md)。

## Ticket Graph & Frontier
1. `01-fold-media-source-adapter-into-player-engine.md`: 内化媒体源构建至播放引擎，拔除 10 处测试强转 (Status: resolved, Blocked by: None)
2. `02-decouple-playback-session-host-and-break-circular-loop.md`: 解耦宿主伪委托，消除启动循环依赖与强转 (Status: resolved, Blocked by: 01)
3. `03-consolidate-dual-source-lyrics-into-track-metadata-repository.md`: 收拢双源歌词至元数据仓储，删除多余仓储 (Status: open, Blocked by: 02)
4. `04-prune-directory-repository-and-cascade-server-deletion.md`: 剪除目录仓储死接口，级联清理服务器孤儿缓存并全量回归验证 (Status: open, Blocked by: 03)
