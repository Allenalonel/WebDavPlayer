# 03: 迁移播放会话层至纯流管道与独立洗牌 (Migrate MusicPlayerAppSession to Stream Pipeline)

**What to build:** 改造应用播放会话层核心实现，使其全面接管播放队列状态与随机洗牌序列的计算。会话层彻底停止向底层播放引擎传递多曲目列表，停止依赖恒为 null 的幽灵洗牌属性，在切歌、自然完播与自适应预卷时，完全以单向命令驱动底层播放引擎的新流式接口。

**Blocked by:** 01: 直连媒体会话切歌意图至播放会话 (Direct MediaSession Skip Intent to AppSession), 02: 扩展流式播放管道接口与底层实现 (Expand Streaming Pipeline Interface in Engine)

**Status:** completed

- [x] 播放会话层内的切歌与起播逻辑全面改用底层引擎的 `loadTrack`，不再向底层传递多曲目列表或外部索引
- [x] 随机播放模式下的置换序列完全由播放队列数据结构及会话层自身管理，彻底消除对底层播放引擎洗牌属性的任何读取
- [x] 自适应预卷逻辑改用无外部曲目索引的新版 `prerollTrack`，并在切歌、暂停或网络卡顿退避时调用 `clearPrerollTrack`
- [x] 会话层不再向底层播放引擎注册反向切歌拦截器，切歌控制权 100% 收敛于会话层本身
- [x] 针对播放会话层的单轨调度、列表循环（Folder Ring）、单曲循环与随机洗牌测试通过
