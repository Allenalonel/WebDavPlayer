# 04: 跨服务器流式起播与失效置灰自愈 (Cross-Server Playback and Inactive Track Fault-Tolerance)

**What to build:** 歌单详情页支持点击“播放全部”或点选单曲，将会话队列（`PlaybackQueue`）装载为当前歌单（来源标记为 `PlaylistQueue`），循环切歌遵循歌单闭环。跨服务器切歌时，会话层依据音轨所属的 `serverId` 动态检索并装配目标服务器上下文（Credentials + SSL），无缝流式跨服播放。当所属服务器离线或远程文件遭遇 404（重命名、移动或删除）时，条目响应式置灰并禁用普通起播，用户点击弹出详细诊断 Toast；播放流转遇到失效音轨或 404 时静默平滑跳跃至下一首有效曲目，歌单菜单提供一键“清理失效歌曲”。

**Blocked by:** 03 (Playlist Detail, Drag Reordering and Sorting)

**Status:** resolved

- [x] 歌单详情页提供“播放全部”与单曲点播操作，调用会话层 `playPlaylist(playlist, startIndex)` 装载整张歌单为 `PlaybackQueue`，设置来源为 `PlaylistQueue`
- [x] 会话层在加载跨服务器音轨时，根据 `track.serverId` 动态提取对应的 `Server Context`，由 `WebDavMediaSourceAdapter` 动态生成携带对应 Basic/Digest 凭证与 SSL 配置的媒体源
- [x] 结合 `WebDavServerRepository` 监听服务器存活性，当服务器不可达或文件已标记 404 时，歌单条目置为 `isInactive = true`，并在 UI 中以半透明灰色减淡呈现
- [x] 点击置灰失效曲目时，拦截普通播放动作并弹出明确的 Toast 提示具体不可用原因（如“所属服务器无法连接”或“音频文件已被重命名或删除”）
- [x] 回放流转（自然播放结束或主动切歌）遇到置灰音轨或底层流式 404 响应时，由会话层统一拦截并静默平滑跳过下一首有效歌曲，杜绝看门狗挂死或应用崩溃
- [x] 歌单详情顶部更多菜单提供“清理失效歌曲”选项，可一键批量从歌单中清除当前无法访问的条目
- [x] 编写针对跨服务器凭据动态切换、失效曲目置灰判定、点击诊断 Toast 派发及 404 静默自愈跳过的自动化测试

## Answer
- 扩展了 `MusicPlayerAppSession` 增加 `playPlaylist(playlist, startIndex)`，在 `PlaybackQueue` 中引入 `QueueOrigin.PLAYLIST`，切歌时遵循歌单闭环流转。
- 会话层加载音轨时动态根据 `track.serverId` 检索对应服务器并装配媒体源认证与 SSL 上下文，无缝支持跨服务器连续播放与预加载。
- 实现了失效音轨容错机制：当服务器离线或音轨遭遇 404 时，条目自动响应式置灰，点击弹出诊断 Toast 告知具体原因（`SERVER_UNREACHABLE` / `FILE_NOT_FOUND`）；播放流转中遇到失效音轨或 404 时静默平滑跳至下一首有效曲目。
- 歌单详情页提供“清理失效歌曲”菜单选项，支持一键移除不可用曲目。
- 编写并通过了跨服务器凭证动态切换、404 静默跳过、置灰与 Toast 触发的全部自动化测试。

