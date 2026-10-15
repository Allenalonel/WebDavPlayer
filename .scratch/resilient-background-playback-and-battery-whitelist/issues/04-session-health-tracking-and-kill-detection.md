# 04: 会话健康追踪与冷启动强杀感知 (Session Health Tracking & Kill Detection)

**What to build:** 追踪播放会话的健康退出状态；在应用因系统激进省电或锁屏清理被异常杀死后，冷启动时能准确识别被强杀事实并向外广播感知标记，为界面横幅提供精准上下文。

**Blocked by:** None (can start immediately)

**Status:** completed

- [x] 在 `PlaybackSessionData` 模型与持久化数据存储中增加退出健康度标记 `cleanExit: Boolean`。
- [x] 在开始播放或播放推进时将 `cleanExit` 写为 `false`；在用户主动暂停、正常切歌或正常退出时将 `cleanExit` 置为 `true`。
- [x] 会话管理层在冷启动恢复会话时，若检测到 `cleanExit == false` 且历史状态为播放中，准确研判为被系统强杀（如 MIUI `LockScreenClean`），并在播放会话状态流中置位 `wasKilledBySystem = true`。
- [x] 当用户在新会话中开始交互或重新点击播放后，平滑重置 `wasKilledBySystem` 标记。
- [x] 编写测试覆盖正常退出与异常崩溃重启场景下的健康标志比对与状态流推导。

## Answer

1. 在 `PlaybackSessionData` 模型与 `DataStorePlaybackSessionStore` 中添加 `cleanExit: Boolean = true` 属性与持久化存取键；
2. 在 `PlayerSessionState` 中添加 `wasKilledBySystem: Boolean = false`；
3. 在 `MusicPlayerAppSessionImpl` 中：
   - 当加载音轨回放推进或周期刷写时写为 `cleanExit = false`；
   - 当用户暂停、停止或正常切歌时写回 `cleanExit = true`；
   - 冷启动调用 `restoreSession()` 时，若检测到 `cleanExit == false` 且存在有效播放曲目，自动识别为系统强杀（如 MIUI LockScreenClean），置位 `wasKilledBySystem = true`；
   - 用户后续点击播放、交互或调用 `clearKilledBySystemFlag()` 时平滑重置该标记；
4. 在 `DataStorePlaybackSessionStoreTest` 与 `PlaybackSessionResumptionTest` 中增加了针对健康退出标记持久化、系统强杀推导及用户交互重置标志的完整单元测试。
