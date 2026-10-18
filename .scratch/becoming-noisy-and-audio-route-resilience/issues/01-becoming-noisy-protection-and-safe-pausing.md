# 01: Becoming Noisy 防意外外放保护与会话暂停 (Becoming Noisy Protection & Safe Pausing)

**What to build:** 当正在播放的蓝牙音箱、车载蓝牙或有线耳机由于断开连接、走出范围、电量耗尽突发关机或物理拔出时，播放宿主实时拦截系统级 `ACTION_AUDIO_BECOMING_NOISY` 广播事件，严格遵循 ADR-0018 单向架构向应用会话层派发暂停指令。播放器毫秒级停止播放时钟推进、取消弱网缓冲看门狗定时器、异步刷盘保存断点毫秒级进度，并释放 CPU/Wi-Fi 流式硬件锁；同时，广播接收器遵循与活跃播放绑定的动态生命周期（仅在播放时注册，暂停与停止时注销），当设备重新连回后保持安全暂停状态，彻底消除意外扬声器外放。

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] 在 `PlaybackSessionHost` 中实现动态 `BecomingNoisyReceiver`，仅在播放状态变为 `Playing` 时注册，在进入 `Paused`、`Idle`、`Ended` 时安全注销。
- [ ] 监听到 `AudioManager.ACTION_AUDIO_BECOMING_NOISY` 广播时，通过单向意图接缝调用 `appSession?.pause()`，触发完整的会话级暂停、持久化和硬件锁释放逻辑。
- [ ] 在 `Media3AudioPlayerEngine` 的 ExoPlayer 构建参数中启用 `.setHandleAudioBecomingNoisy(true)` 作为底层引擎保底。
- [ ] 蓝牙设备重新连接后保持安全暂停状态，不自动恢复播放，必须由用户在 UI 或耳机按键上主动触发播放。
- [ ] 编写并执行单元测试，验证在活跃播放时广播触发会话暂停，在非播放状态下广播被安全忽略且无资源泄漏。
