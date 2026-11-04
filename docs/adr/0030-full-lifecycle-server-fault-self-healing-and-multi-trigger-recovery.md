# 0030: 全链路服务器故障自愈与多触发恢复网络 (Full-Lifecycle Server Fault Self-Healing and Multi-Trigger Recovery Network)

## Status

Accepted

## Context

在应用引入跨服务器歌单（ADR-0020）及歌单仓储连通性自愈（ADR-0022）后，音轨置灰与自愈机制在多服务节点场景下初具雏形。然而在真实家庭 NAS 与私有云运维场景（如群晖/AList/OpenList 服务端容器重启、网络抖动、Token 刷新延迟）中，暴露出了严重的状态机单向死锁与故障认知死角：

1. **瞬态凭据失效与连通性故障在 UI 层聚合却在自愈层割裂**：
   - 当 WebDAV 服务器重启或容器拉起过程中，TCP 端口往往优先就绪，但上层认证服务或反代网关尚在初始化，播放器拉流时服务端返回 `401 Unauthorized` 或 `403 Forbidden`；
   - 系统将该音轨标记为 `InactiveReason.SERVER_CREDENTIAL_INVALID`；
   - 在 UI 交互层（`PlaylistDetailViewModel`），`SERVER_CREDENTIAL_INVALID` 与 `SERVER_UNREACHABLE` 统一映射为用户易懂的提示：“**所属服务器无法连接**”；
   - 然而在仓储自愈层，原 `triggerReachabilitySelfHealing` 与 `clearServerUnreachableStatus` 仅硬编码匹配 `SERVER_UNREACHABLE`，导致由服务端重启造成的凭据失效记录永久滞留在本地 Room 数据库中，无法自愈。
2. **故障状态单向积累与闭环缺失（Symmetric Fault Lifecycle Defect）**：
   - 仓储层具备极其敏锐的写入置灰逻辑（`markTrackInactive`），但缺乏对等的恢复信道；
   - **测试连接成功脱节**：用户在服务器管理界面点击“测试连接”并成功返回 200/207 时，该事件完全没有通知歌单仓储清除该节点的不可达标记；
   - **播放成功状态未反哺**：通过切歌或外部意图成功播放某首置灰歌曲后，底层 ExoPlayer 已进入正常的 `PlaybackState.Playing`，但仓储层完全不知情，耳机里正常出声，歌单界面里该曲目却依然置灰且点击拦截；
   - **交互死锁（Deadlock）**：歌单详情界面对置灰曲目的点击事件进行绝对拦截并弹 Toast，不发起任何探活，导致一旦被标记为置灰，曲目就变成了不可恢复的“死节点”。
3. **故障标记粒度割裂（单曲 vs 整机）**：
   - 服务端宕机或重启是整机级别的故障，但系统此前仅在报错曲目上按单曲粒度标记置灰，后续排队曲目未被尝试加载时依然显示为高亮，造成同一服务器歌曲“半灰半亮”的混淆。
4. **自愈触发入口过于狭隘**：
   - 旧有自愈仅绑定 Android 本地网络广播切换（Wi-Fi/蜂窝断开重连），在手机网络始终完好、纯粹是远端服务器重启的场景下，自动自愈从未被激活。

## Decision

恪守深层模块设计准则与状态对称闭环原则，重构服务器故障自愈与恢复拓扑：

### 1. 故障域合并：统一将瞬态凭据失效纳入服务器级自愈
- 在 Room `PlaylistDao` 中扩展批量查询与清除能力：
  ```kotlin
  @Query("SELECT DISTINCT serverId FROM playlist_tracks WHERE isInactive = 1 AND (inactiveReason = 'SERVER_UNREACHABLE' OR inactiveReason = 'SERVER_CREDENTIAL_INVALID')")
  suspend fun getDistinctServerIdsForInactiveServers(): List<Long>

  @Query("UPDATE playlist_tracks SET isInactive = 0, inactiveReason = NULL WHERE serverId = :serverId AND (inactiveReason = 'SERVER_UNREACHABLE' OR inactiveReason = 'SERVER_CREDENTIAL_INVALID')")
  suspend fun clearServerInactiveStatus(serverId: Long): Int
  ```
- 将 `SERVER_UNREACHABLE` 与 `SERVER_CREDENTIAL_INVALID` 在数据库落盘与内存映射中统一作为“服务器级可自愈瞬态故障”管理，自愈探活通过 PROPFIND 验证连通性与 Basic/Digest 认证，两者通过后同步清除。

### 2. 建立四重自愈与状态恢复触发网络 (Multi-Trigger Network)
构建全方位的故障清除闭环，杜绝任何单向死锁：
1. **歌单详情主动探活**：
   - `PlaylistDetailViewModel.loadData()` 初始化时在后台主动调用 `triggerReachabilitySelfHealing()`；
   - 用户在歌单中点击任意服务器级置灰曲目（`SERVER_UNREACHABLE` 或 `SERVER_CREDENTIAL_INVALID`）时，除弹出友好 Toast 外，同步在后台异步触发一次即时探活，服务器已恢复时秒级解除置灰并点亮。
2. **服务器测试连接联动**：
   - 在 `ServerManagementViewModel` 中注入 `PlaylistRepository`；
   - 当用户在服务器设置界面点击“测试连接”且返回 `ConnectionResult.Success` 时，立即派发 `playlistRepository.clearServerUnreachableStatus(serverId)`，批量唤醒歌单中该服务器下的所有置灰曲目。
3. **播放引擎成功出声状态反哺 (Playback Success Feedback)**：
   - 在会话层 `PlaybackFaultTolerance.onPlayingStarted()` 监听到物理音频流进入 `PlaybackState.Playing` 时，既然当前曲目能成功播放解码，说明当前所属服务器与认证均处于完全正常状态，自动调用 `playlistRepository.clearServerUnreachableStatus(currentTrack.serverId)`，反向清除置灰。
4. **系统网络恢复广播 (Network Observer Fallback)**：
   - 保留原有的手机网络断开重连监听，作为兜底机制。

### 3. 整机故障标记一致性
- 在 `PlaylistDao` 与 `PlaylistRepository` 中引入 `markServerInactive(serverId, reason)`；
- 当播放遇到连接被拒、连接超时或服务器级认证异常时，统一将该服务器下的所有音轨置灰，杜绝“同属一个宕机服务器、有些灰有些亮”的不一致状态。

### 4. 前端 UI 响应性保障
- 在 `PlaylistDetailViewModel` 中比对新旧音轨列表时，将 `a.inactiveReason != b.inactiveReason` 纳入更新判断条件，确保数据库状态变更能即时触发 UI 重组刷新。

## Consequences

- **彻底消除置灰死锁**：服务器重启恢复后，无论通过测试连接、进入歌单、点击置灰曲目，还是切歌播放成功，均能 100% 自动解除置灰并恢复正常交互。
- **高韧性（High Resilience）**：完美免疫 NAS 定时重启、Docker 容器热重载、临时反代 401 波动等真实私有云网络颠簸。
- **状态对称闭环（Symmetric Lifecycle）**：确立了“有错误标记，就必须有对等自动清除”的架构纪律。
- **全量测试与打包通过**：相关单元测试（`PlaylistRepositoryTest`、`PlaybackFaultToleranceTest`、`ServerManagementViewModelTest`、`PlaylistDetailViewModelTest`）全部绿色通过，Release 构建与打包验证（6.82 MB $\le$ 7.5 MB 预算）全部达标。
