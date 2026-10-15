# 04: 收缩播放引擎接口并铲除历史遗留 (Contract AudioPlayerEngine and Remove Legacy Seam)

**What to build:** 执行 Expand–Contract 模式中的收缩阶段（Contract）：从播放引擎公共接口及实现类中物理删除所有已废弃的多曲目旧方法、幽灵属性与反向回调接口，彻底根除双队列控制与虚构状态机。

**Blocked by:** 03: 迁移播放会话层至纯流管道与独立洗牌 (Migrate MusicPlayerAppSession to Stream Pipeline)

**Status:** claimed

- [ ] 从播放引擎接口中完全删除多曲目方法：`playTracks`、`seekToTrack`、`removeTrack`、`insertTrack` 及带索引的旧版 `updateTrack`
- [ ] 从播放引擎接口中完全删除多曲目状态：`currentTrackIndex`、`playbackMode`、`setPlaybackMode` 及幽灵属性 `shufflePermutation`
- [ ] 从播放引擎接口中完全删除反向切歌拦截器：`SkipHandler`、`setSkipHandler`、`skipToNext`、`skipToPrevious`
- [ ] 真实播放引擎实现与前台服务宿主类中同步清理所有旧多曲目列表缓存（如 `loadedTracks`）和无效分发逻辑
- [ ] 主工程源码完全移除了对旧接缝的所有依赖，工程编译零警告、零遗留残留
