# 04: 全场景声学边界与端到端系统中断回归自证 (End-to-End System Focus, Noisy and Audio Route Regression Suite)

**What to build:**
构建端到端集成测试集，跨越 `MusicPlayerAppSession`、`PlaybackSessionHost` 与底层流式渲染管道接缝，联合模拟“起播缓冲时拔耳机”、“通话打断期间通知栏手动暂停”、“系统切换器手动切设备”、“多设备连接抖动与非标采样率重混音”等极限声学边界，执行全局构建与全量测试验证，交付工业级声学稳定性。

**Blocked by:** 01: 缓冲阶段防外放保护与安全降级 (Buffering Becoming-Noisy Protection and Safe Pausing), 02: 音频焦点单向意图汇流与幽灵复播熔断 (Unidirectional Audio Focus Intent Arbitration and Ghost Resumption Guard), 03: 系统媒体输出切换器双轨路由感知与发声管道自愈 (Media Output Switcher Dynamic Routing Observer and AudioSink Flush Resynchronization)

**Status:** ready-for-agent

- [ ] 编写端到端联合集成测试，完整串联模拟：起播缓冲状态下的耳机拔出 -> 触发安全暂停 -> 缓冲完成后依然维持暂停不发声。
- [ ] 编写端到端联合集成测试，完整串联模拟：通话打断 -> 通话中用户点击通知栏暂停 -> 通话结束系统下发 Gain -> 确认不发生自动播放。
- [ ] 编写端到端联合集成测试，完整串联模拟：永久失焦后焦点退栈与状态稳定。
- [ ] 运行工程全量单元测试与集成测试套件（`gradlew testDebugUnitTest`），确保无任何断言失败或回归破损。
- [ ] 验证系统各边界异常处理与前后台生命周期的整体健全性。
