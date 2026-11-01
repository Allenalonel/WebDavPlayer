# 04: 全场景声学边界与端到端系统中断回归自证 (End-to-End System Focus, Noisy and Audio Route Regression Suite)

**What to build:**
构建端到端集成测试集，跨越 `MusicPlayerAppSession`、`PlaybackSessionHost` 与底层流式渲染管道接缝，联合模拟“起播缓冲时拔耳机”、“通话打断期间通知栏手动暂停”、“系统切换器手动切设备”、“多设备连接抖动与非标采样率重混音”等极限声学边界，执行全局构建与全量测试验证，交付工业级声学稳定性。

**Blocked by:** 01: 缓冲阶段防外放保护与安全降级 (Buffering Becoming-Noisy Protection and Safe Pausing), 02: 音频焦点单向意图汇流与幽灵复播熔断 (Unidirectional Audio Focus Intent Arbitration and Ghost Resumption Guard), 03: 系统媒体输出切换器双轨路由感知与发声管道自愈 (Media Output Switcher Dynamic Routing Observer and AudioSink Flush Resynchronization)

**Status:** resolved

- [x] 编写端到端联合集成测试，完整串联模拟：起播缓冲状态下的耳机拔出 -> 触发安全暂停 -> 缓冲完成后依然维持暂停不发声。
- [x] 编写端到端联合集成测试，完整串联模拟：通话打断 -> 通话中用户点击通知栏暂停 -> 通话结束系统下发 Gain -> 确认不发生自动播放。
- [x] 编写端到端联合集成测试，完整串联模拟：永久失焦后焦点退栈与状态稳定。
- [x] 运行工程全量单元测试与集成测试套件（`gradlew testDebugUnitTest`），确保无任何断言失败或回归破损。
- [x] 验证系统各边界异常处理与前后台生命周期的整体健全性。

## Comments

### Resolution
- 新建端到端联合集成测试类 `EndToEndSystemFocusAndRouteRegressionTest`，跨越 `MusicPlayerAppSessionImpl`、`PlaybackSessionHost`、`WebDavMediaService` 与底层渲染管道接缝：
  1. `bufferingBecomingNoisy_unplugHeadphonesDuringBuffering_interceptsAndSafelyDegradesToPaused`：完整串联模拟起播缓冲状态下的耳机拔出/蓝牙断开（`ACTION_AUDIO_BECOMING_NOISY`），验证派发会话级安全暂停、释放硬件锁、注销广播接收器，且缓冲完成后保持在 `Paused` 状态坚决不外放。
  2. `ghostResumptionGuard_incomingCall_userPausesInNotification_callEnds_blocksGhostResumption`：完整模拟电话呼入触发瞬态失焦，用户在通话期间于通知栏点击暂停，通话结束后系统下发 `AUDIOFOCUS_GAIN`，验证原子化阻断幽灵自动复播。
  3. `transientFocusLoss_withoutUserIntervention_automaticallyResumesPlayback`：完整模拟通话期间无用户主动干预，通话结束后平滑恢复播放。
  4. `permanentAudioFocusLoss_pausesSession_abandonsFocus_andIgnoresSubsequentFocusGain`：模拟外部第三方应用抢占永久音频焦点，应用主动调用 `abandonAudioFocusRequest` 退栈并稳定停留在暂停状态。
  5. `dynamicAudioRouting_systemMediaOutputSwitcherTransfer_flushesActiveSinkViaHost`：模拟系统媒体输出切换器流转设备，底层 `AudioSink.flush()` 被触发以清空陈旧 PCM 数据并重新握手时钟。
  6. `multiBoundaryAcousticJitter_racingInterruptionAndRoutingEvents_convergesSafely`：模拟缓冲、路由切换、来电瞬态打断、通知栏手动暂停与系统广播竞态等极限组合边界，断言状态机收敛与干净恢复。
- 优化测试 JVM 堆配置（`maxHeapSize = "2048m"`），全量运行 814 项单元测试与集成测试（`gradlew testDebugUnitTest`），全部绿色通过无回归破损。
