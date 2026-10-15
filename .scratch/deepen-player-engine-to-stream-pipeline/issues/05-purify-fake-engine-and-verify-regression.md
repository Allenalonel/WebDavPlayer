# 05: 净化仿真测试桩与全量回归验证 (Purify Fake Engine and Verify Regression)

**What to build:** 依照“接口即测试表面”（The interface is the test surface）原则，重构并精简仿真测试播放引擎（`FakeAudioPlayerEngine`），删除其中模拟的多曲目列表和洗牌算法，使其成为纯粹记录当前单轨状态与进度的深模块测试桩。同时更新受影响的全部单元测试用例，执行全量自动化回归验证。

**Blocked by:** 04: 收缩播放引擎接口并铲除历史遗留 (Contract AudioPlayerEngine and Remove Legacy Seam)

**Status:** claimed

- [ ] 仿真播放引擎（`FakeAudioPlayerEngine`）大幅瘦身，仅实现收窄后的流式管道接口，代码量减少 70% 以上
- [ ] 移除测试桩中所有的列表管理逻辑、洗牌模拟逻辑与反向回调模拟代码
- [ ] 更新 `Media3AudioPlayerEngineTest`，针对 `loadTrack`、`prerollTrack`、`clearPrerollTrack` 与 `updateTrackMetadata` 编写完备的单测
- [ ] 更新 `WebDavMediaSessionCallbackTest` 与 `MusicPlayerAppSessionTest`，全面覆盖切歌直连、CUE 分轨推进与双轨预卷
- [ ] 运行全工程单元测试，所有测试 100% 绿色通过，自证零回归
