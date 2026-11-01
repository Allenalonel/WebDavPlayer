# 01: Becoming Noisy 防意外外放保护与会话暂停 (Becoming Noisy Protection & Safe Pausing)

**What to build:** 当正在播放的蓝牙音箱、车载蓝牙或有线耳机由于断开连接、走出范围、电量耗尽突发关机或物理拔出时，播放宿主实时拦截系统级 `ACTION_AUDIO_BECOMING_NOISY` 广播事件，严格遵循 ADR-0018 单向架构向应用会话层派发暂停指令。播放器毫秒级停止播放时钟推进、取消弱网缓冲看门狗定时器、异步刷盘保存断点毫秒级进度，并释放 CPU/Wi-Fi 流式硬件锁；同时，广播接收器遵循与活跃播放绑定的动态生命周期（仅在播放时注册，暂停与停止时注销），当设备重新连回后保持安全暂停状态，彻底消除意外扬声器外放。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] 在 `PlaybackSessionHost` 中实现动态 `BecomingNoisyReceiver`，仅在播放状态变为 `Playing` 时注册，在进入 `Paused`、`Idle`、`Ended` 时安全注销。
- [x] 监听到 `AudioManager.ACTION_AUDIO_BECOMING_NOISY` 广播时，通过单向意图接缝调用 `appSession?.pause()`，触发完整的会话级暂停、持久化和硬件锁释放逻辑。
- [x] 在 `Media3AudioPlayerEngine` 的 ExoPlayer 构建参数中启用 `.setHandleAudioBecomingNoisy(true)` 作为底层引擎保底。
- [x] 蓝牙设备重新连接后保持安全暂停状态，不自动恢复播放，必须由用户在 UI 或耳机按键上主动触发播放。
- [x] 编写并执行单元测试，验证在活跃播放时广播触发会话暂停，在非播放状态下广播被安全忽略且无资源泄漏。

## Answer

已在 `PlaybackSessionHost` 与 `Media3AudioPlayerEngine` 中完成 Becoming Noisy 防外放拦截机制：
1. `PlaybackSessionHost` 内部接入 `becomingNoisyReceiver`，动态监听 `AudioManager.ACTION_AUDIO_BECOMING_NOISY` 广播。
2. 动态生命周期绑定：仅在状态跃迁为 `PlaybackState.Playing` 或前台服务提升且处于 `Playing` 时动态注册；进入 `Paused`、`Idle`、`Ended`、`Error`、`detachService`、`stop` 及 `release` 时安全注销，杜绝泄露与空闲误触。
3. 单向架构意图收敛：收到广播时优先调用 `appSession?.pause()`，无缝复用会话层状态跃迁、硬件锁释放与持久化落盘；当未绑定会话时保底调用 `playerEngine.pause()`。
4. 底层引擎保底：在 `Media3AudioPlayerEngine` 中为 `ExoPlayer.Builder` 开启 `.setHandleAudioBecomingNoisy(true)`。
5. 蓝牙重连策略：重连后保持安全暂停，不自动发声。
6. 在 `PlaybackSessionHostTest` 中增加完整单元测试覆盖（包括播放中广播暂停、暂停/空闲时广播安全忽略、未绑定会话保底暂停），全部测试通过。Commit `a8cdb1b`。
