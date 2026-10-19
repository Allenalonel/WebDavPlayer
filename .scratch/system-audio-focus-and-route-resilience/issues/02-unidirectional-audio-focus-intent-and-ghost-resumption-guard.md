# 02: 音频焦点单向意图汇流与幽灵复播熔断 (Unidirectional Audio Focus Intent Arbitration and Ghost Resumption Guard)

**What to build:**
消除 `AudioFocusHandler` 对底层播放引擎的直接调用，将其全面收拢至应用会话层（`MusicPlayerAppSession`）的单向意图接缝。实现通话瞬态失焦（`AUDIOFOCUS_LOSS_TRANSIENT`）的会话级暂停与精确断点保存；当用户在通话被打断期间于通知栏、锁屏或屏幕主动点击了暂停，原子化清除待恢复标志，挂断电话后绝对不发生“幽灵自动复播”；永久失焦时主动释放系统音频焦点；修复系统导航短提示音的应用层双重音量压低。

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `AudioFocusHandler` 移除对底层引擎直接控制的依赖，改由应用会话层接缝（`MusicPlayerAppSession`）统一响应音频焦点事件。
- [ ] 瞬态失焦（`AUDIOFOCUS_LOSS_TRANSIENT`）转化为会话级暂停意图，停止播放时钟并刷盘保存当前毫秒级进度。
- [ ] 引入瞬态暂停仲裁标记：在通话打断期间，若用户通过任何途径主动触发了 `pause()`、`togglePlayPause()` 或 `stop()`，原子化重置恢复标志，通话结束后收到 `AUDIOFOCUS_GAIN` 坚决维持暂停状态，阻断幽灵复播。
- [ ] 永久失焦（`AUDIOFOCUS_LOSS`）时，会话层执行暂停并调用 `abandonAudioFocus()`，主动退出系统焦点管理栈。
- [ ] 规范化导航短通知等声音压低（Ducking）处理，消除应用层与系统层的重复音量衰减。
- [ ] 编写焦点打断、通话中主动暂停阻断幽灵复播、永久失焦释放焦点的单元测试集。
