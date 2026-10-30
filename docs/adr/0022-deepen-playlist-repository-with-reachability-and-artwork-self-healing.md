# 0022: 歌单仓储加深与下沉连通性及封面自愈 (Deepen Playlist Repository with Reachability and Artwork Self-Healing)

## Context

在引入跨服务器歌单（ADR-0020）后，音轨条目（`PlaylistTrackEntry`）支持引用来自不同 WebDAV 服务器的音频文件。然而在原有实现中，`PlaylistRepository` 仅作为针对本地 Room `PlaylistDao` 的浅层透传模块（Shallow Module），自身缺乏服务器连通性判断与元数据自愈能力。

这引发了严重的代码局部性（Locality）缺失与功能死角：
1. **核心业务逻辑泄露至前端 UI**：`PlaylistDetailViewModel` 不得不额外注入 `ServerRepository` 与 `TrackMetadataRepository`，在 ViewModel 内部手动拼接流比对服务器连通性（`InactiveReason.SERVER_UNREACHABLE`），并在 `Dispatchers.IO` 上手动开启协程遍历缺失封面的音轨、逐个请求元数据并反写回仓储。
2. **多入口数据表现不一致（功能死角）**：当用户在非详情页场景（例如歌单列表直接播放全部、播放队列回放、通知栏/耳机切歌流转）消费歌单时，由于没有经过 `PlaylistDetailViewModel`，缺失的封面永远无法被自愈修补，断网服务器也无法提前被置灰标记，导致切歌时直接抛出网络异常。
3. **测试表面过于宽大**：为了测试一个简单的歌单详情展示界面，单元测试必须为该 ViewModel 构造并 Mock 4 个独立的仓储依赖。

## Decision

恪守深层模块（Deep Module）设计准则，将服务器连通性校验与封面自愈逻辑完全下沉至仓储接缝（Seam）内部：

### 1. 歌单仓储深层接缝构建
- 在 `PlaylistRepositoryImpl` 构造函数中引入 `ServerRepository` 与 `TrackMetadataRepository` 适配器（均为可选，未提供时保持纯 DAO 行为，确保轻量单测兼容性）；
- 统一在 `getTracksForPlaylistFlow(playlistId)` 与挂起函数 `getTracksForPlaylist(playlistId)` 内部完成两级数据装配：
  - **服务器连通性协调**：响应式结合 `ServerRepository.getAllServers()`，当音轨所属服务器不存在或 URL 为空时，自动赋予 `isInactive = true` 与 `InactiveReason.SERVER_UNREACHABLE`；
  - **同步缓存富化与后台异步自愈**：当音轨缺失缩略图时，优先从本地已解析的元数据缓存中同步补齐并持久化；对尚未解析的活跃音轨，内部通过并发控制触发单次受限异步自愈提取，落盘并写回 Room，通过数据库数据流自然触发 UI 刷新。

### 2. 净化与瘦身前端 ViewModel
- 彻底移除 `PlaylistDetailViewModel` 中的 `serverRepository` 与 `trackMetadataRepository` 依赖；
- 移除 ViewModel 中手动合并服务器流与遍历自愈封面的近百行协程代码；
- `PlaylistDetailViewModelFactory` 简化为仅注入 `PlaylistRepository` 与 `MusicPlayerAppSession`；
- UI 彻底成为纯粹的状态投影器（State Projector）。

## Consequences

- **高局部性（High Locality）**：歌单封面自愈与失效状态处理集中在歌单领域模块内部，修改与自愈策略演进不再波及前端 UI；
- **高杠杆（Leverage）**：单一深层 `PlaylistRepository` 接缝同时服务详情界面、会话层（`MusicPlayerAppSessionImpl.playPlaylist`）及播放队列，所有入口均能透明获取已完成连通性标记与自愈修复的音轨；
- **测试切面精准**：仓储层独立测试服务器断网置灰与封面自愈，ViewModel 单元测试摆脱沉重的 Mock 依赖；
- **100% 绿色闭环验证**：全量单元测试套件全部通过。
