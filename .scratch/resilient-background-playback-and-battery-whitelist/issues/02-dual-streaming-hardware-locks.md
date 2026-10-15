# 02: 流式双硬件锁联动机制 (Dual Streaming Hardware Locks)

**What to build:** 在流式音频播放与网络缓冲期间，系统自动持有 CPU 唤醒锁与 Wi-Fi 高性能锁，防止手机锁屏息屏后网络降频与 CPU 停频导致声音断流；当用户暂停或停止播放时，立即释放两把硬件锁，允许设备进入深层节能休眠。

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] 封装硬件锁管理抽象（`StreamingHardwareLockManager`），统一管控 CPU WakeLock 与 Wi-Fi Lock。
- [ ] 底层播放器内核启用网络唤醒模式（`.setWakeMode(C.WAKE_MODE_NETWORK)`），在数据消费与解码流水线运转时自动持有 `PARTIAL_WAKE_LOCK`。
- [ ] 数据源层装配 Wi-Fi 高性能锁（`WIFI_MODE_FULL_HIGH_PERF`，标示为 `"WebDavPlayer:StreamingWifiLock"`）。
- [ ] 当状态进入 `Playing` 或 `Buffering` 时，自动 `acquire()` Wi-Fi 锁与 CPU 唤醒锁，防止锁屏流式数据断流。
- [ ] 当状态进入 `Paused`、`Idle`、`Ended` 或引擎 `release` 时，立即 `release()` Wi-Fi 锁与 CPU 唤醒锁，严格杜绝无谓的待机电池消耗。
- [ ] 编写单元与集成测试，验证播放、缓冲、暂停、停止不同阶段双硬件锁的获取与释放生命周期。
