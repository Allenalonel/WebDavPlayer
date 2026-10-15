# 04: 会话健康追踪与冷启动强杀感知 (Session Health Tracking & Kill Detection)

**What to build:** 追踪播放会话的健康退出状态；在应用因系统激进省电或锁屏清理被异常杀死后，冷启动时能准确识别被强杀事实并向外广播感知标记，为界面横幅提供精准上下文。

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] 在 `PlaybackSessionData` 模型与持久化数据存储中增加退出健康度标记 `cleanExit: Boolean`。
- [ ] 在开始播放或播放推进时将 `cleanExit` 写为 `false`；在用户主动暂停、正常切歌或正常退出时将 `cleanExit` 置为 `true`。
- [ ] 会话管理层在冷启动恢复会话时，若检测到 `cleanExit == false` 且历史状态为播放中，准确研判为被系统强杀（如 MIUI `LockScreenClean`），并在播放会话状态流中置位 `wasKilledBySystem = true`。
- [ ] 当用户在新会话中开始交互或重新点击播放后，平滑重置 `wasKilledBySystem` 标记。
- [ ] 编写测试覆盖正常退出与异常崩溃重启场景下的健康标志比对与状态流推导。
