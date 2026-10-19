# 01: 缓冲阶段防外放保护与安全降级 (Buffering Becoming-Noisy Protection and Safe Pausing)

**What to build:**
当用户点选曲目、切歌或遇到弱网重新缓冲等处于 `Buffering` 缓冲阶段时，全面纳管 `BecomingNoisyReceiver` 广播监听。在音频数据装入缓冲区尚未发声的窗口期内，若用户拔出有线耳机、蓝牙音箱突发关机或超出连接范围，系统发出的 `ACTION_AUDIO_BECOMING_NOISY` 广播能够被精准拦截并派发 `appSession.pause()`，原子化取消起播意图与弱网看门狗；当底层音频流缓冲就绪时，严格保持在 `Paused` 状态，彻底消灭安静场合扬声器意外公放的隐患。

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `PlaybackSessionHost` 将 `BecomingNoisyReceiver` 的有效注册生命周期扩展至 `PlaybackState.Buffering` 阶段（且 `playWhenReady == true` 时保持注册）。
- [ ] 在 `Buffering` 阶段收到 `ACTION_AUDIO_BECOMING_NOISY` 时，能够可靠向高层会话层派发 `appSession.pause()`。
- [ ] 会话层在缓冲期收到暂停指令时，重置弱网看门狗并释放流媒体硬件锁（WakeLock/WifiLock）。
- [ ] 底层引擎在缓冲完成进入就绪状态时，尊重已更新的暂停状态机，不再意外自动起播发声。
- [ ] 编写针对 `PlaybackSessionHost` 在 `Buffering` 状态下的外放广播拦截与状态收敛单元测试，断言安全降级为 `Paused`。
