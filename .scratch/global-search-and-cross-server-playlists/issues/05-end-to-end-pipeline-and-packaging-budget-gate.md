# 05: 全链路端到端集成、弱网回归与发布包体积守门 (End-to-End Pipeline, Weak Network Regression and Packaging Gate)

**What to build:** 编写端到端全链路自动化集成测试套件，完整验证“新建歌单 $\to$ 搜索跨服曲目/CUE虚拟分轨并添加 $\to$ 拖拽重排 $\to$ 跨服播放流转 $\to$ 离线置灰与 Toast 点击 $\to$ 冷启动断点恢复”完整生命周期。执行 `verifyReleasePackaging` 门禁任务与 `PackagingSeamVerificationTest`，守门 Release APK 包体严格 $\le$ 7.5 MB，`classes.dex` $\le$ 4.0 MB，确保无任何冗余代码引入。

**Blocked by:** 01 (SWR Instant Search and Ad-hoc Queue Insertion), 04 (Cross-Server Playback and Inactive Track Fault-Tolerance)

**Status:** claimed

- [ ] 编写端到端全链路集成回归测试（`EndToEndPlaylistAndSearchPipelineTest`），验证从创建歌单、搜索穿梭、插队起播到跨服播放的全链路状态一致性
- [ ] 验证包含跨服务器歌单上下文的会话持久化与冷启动无缝断点恢复（`PlaybackSessionState` 正确恢复歌单队列与对应服务器上下文）
- [ ] 验证包含 CUE 虚拟分轨的歌单在跨服播放与切歌时的绝对无缝衔接与虚拟时间轴换算正确性
- [ ] 运行本地全量测试套件 `./gradlew testDebugUnitTest`，确保所有既有音频焦点、Becoming Noisy 防外放、流式保活与新特性测试全部绿灯通过
- [ ] 执行 `PackagingSeamVerificationTest` 与 `./gradlew verifyReleasePackaging` 自动化门禁，确保 Release APK 总大小严格维持在 7.5 MB 以内，单 `classes.dex` 大小严格维持在 4.0 MB 以内，纯 64 位 `arm64-v8a` 架构无多余架构膨胀
