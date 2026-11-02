# 0027: 批量添加到歌单与上下文多选模式 (Batch Playlist Addition and Contextual Selection)

## Context

在先前的实现中，向自定义歌单（Playlist）添加音频曲目仅支持单曲维度操作（通过条目溢出菜单调起 `AddToPlaylistDialog`）。在用户整理大量曲目或从媒体库及全库检索结果中批量收录音乐时，单曲逐一添加存在巨大交互负担：

1. **缺乏多选状态机（Selection Mode）**：
   - 媒体库（`DirectoryBrowserScreen`）与全局搜索结果（`SearchResultsViews`）列表均缺乏条目多选状态维护；
   - 用户无法一次性勾选多个音频曲目进行批量处理。

2. **歌单入库缺乏原子去重机制**：
   - 当用户批量勾选的曲目中已存在部分已入库歌曲时，直接入库会导致歌单内部产生重复条目；
   - 缺乏精准的 `(addedCount, skippedCount)` 反馈通道，无法向用户呈现清晰友好的 Toast 提示。

3. **Material Design 3 交互协同一致性**：
   - 搜索页面与媒体库页面的多选状态切换、顶栏转换与物理返回键退出需要遵循统一的层级导航契约；
   - 搜索场景下如果混杂目录匹配结果，若不加以保护容易引发误选文件夹的异常操作。

## Decision

对齐 Material Design 3 规范与主流流媒体播放器体验，落地**批量添加到歌单与上下文多选模式（Plan A 悬浮胶囊浮岛）**：

### 1. 仓储层自动去重与原子入库 (`PlaylistRepository`)
- 增强 `PlaylistRepository.addTracksToPlaylist(playlistId: Long, tracks: List<AudioTrack>)`：
  - 事务内先行读取目标歌单中已有音轨的唯一键集合（`TrackKey(serverId, remotePath)`）；
  - 自动过滤剔除已存在的曲目，仅针对全新曲目分配自增 `sortOrder` 并执行批量插入；
  - 对外明确返回原子入库成功的曲目数量；
- 在 `PlaylistListViewModel` 暴露统一回调：
  `fun addTracksToPlaylist(playlistId: Long, tracks: List<AudioTrack>, onResult: (addedCount: Int, skippedCount: Int) -> Unit)`，提供确定性的去重反馈信息。

### 2. 通用化改造 `AddToPlaylistDialog`
- 升级 `AddToPlaylistDialog` 组件入参，接收 `tracks: List<AudioTrack>`（兼容单曲与多曲）；
- 顶部呈现 Material Design 3 选歌摘要卡片，展示已选曲目总数及自动去重说明；
- 点击已有歌单或新建歌单时，统一派发批量入库事件。

### 3. 上下文多选动作栏与手势体系 (Plan A Floating Pill Bar)
- **手势触发**：长按媒体库或搜索结果中任意音频条目触发进入多选态，首项默认勾选；
- **顶栏状态流转**：
  - 媒体库顶栏与搜索输入栏在多选态下平滑过渡为 Contextual Top App Bar，展示 `[✕ 取消]`、`已选 N 项` 以及 `[全选 / 取消全选]` 按钮；
  - 搜索场景下进入多选态**自动收起软键盘**，避免输入法遮挡列表与操作；
  - 搜索结果中的文件夹卡片自动置为只读保护态，禁止勾选；
- **底部浮岛操作栏 (`FloatingMultiSelectBottomBar`)**：
  - 悬浮于屏幕底部安全区之上，提供「下一首播放（Play Next）」与突出权重的「添加到歌单 (N)」胶囊按钮；
  - 提供平滑的向上滑入滑出动画；
- **层级退出契约**：物理返回键及顶栏 `✕` 优先退出多选模式，清空勾选状态，不触发页面返回或搜索退出。

## Consequences

- **操作效率极大提升**：用户可在媒体库与全库搜索中毫秒级批量多选音轨，一键收录或插队播放；
- **幂等与健壮的数据一致性**：仓储层自动去重机制保证了歌单中绝对不产生重复音轨，并为用户提供准确的 Toast 反馈；
- **统一纯粹的 MD3 交互范式**：媒体库与搜索页复用统一的多选动作栏、浮岛操作栏及退出状态机，符合主流 Android 14+ 沉浸设计语言。
