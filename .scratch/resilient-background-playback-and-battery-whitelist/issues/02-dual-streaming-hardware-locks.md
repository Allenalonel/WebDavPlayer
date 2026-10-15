# 02: 流式双硬件锁联动机制 (Dual Streaming Hardware Locks)

**What to build:** 在流式音频播放与网络缓冲期间，系统自动持有 CPU 唤醒锁与 Wi-Fi 高性能锁，防止手机锁屏息屏后网络降频与 CPU 停频导致声音断流；当用户暂停或停止播放时，立即释放两把硬件锁，允许设备进入深层节能休眠。

**Blocked by:** None (can start immediately)

**Status:** completed

- [x] 封装硬件锁管理抽象（`StreamingHardwareLockManager`），统一管控 CPU WakeLock 与 Wi-Fi Lock。
- [x] 底层播放器内核启用网络唤醒模式（`.setWakeMode(C.WAKE_MODE_NETWORK)`），在数据消费与解码流水线运转时自动持有 `PARTIAL_WAKE_LOCK`。
- [x] 数据源层装配 Wi-Fi 高性能锁（`WIFI_MODE_FULL_HIGH_PERF`，标示为 `"WebDavPlayer:StreamingWifiLock"`）。
- [x] 当状态进入 `Playing` 或 `Buffering` 时，自动 `acquire()` Wi-Fi 锁与 CPU 唤醒锁，防止锁屏流式数据断流。
- [x] 当状态进入 `Paused`、`Idle`、`Ended` 或引擎 `release` 时，立即 `release()` Wi-Fi 锁与 CPU 唤醒锁，严格杜绝无谓的待机电池消耗。
- [x] 编写单元与集成测试，验证播放、缓冲、暂停、停止不同阶段双硬件锁的获取与释放生命周期。

## Answer

1. 抽象并实现了 `StreamingHardwareLockManager`（`DefaultStreamingHardwareLockManager`），统一管理系统 `PowerManager.WakeLock`（`PARTIAL_WAKE_LOCK`）与 `WifiManager.WifiLock`（`WIFI_MODE_FULL_HIGH_PERF`，标签 `"WebDavPlayer:StreamingWifiLock"`）；
2. 在 `Media3AudioPlayerEngine` 创建 ExoPlayer 时显式注入 `.setWakeMode(C.WAKE_MODE_NETWORK)`；
3. 将 `StreamingHardwareLockManager` 装配入 `DefaultWebDavMediaSourceAdapter`、`Media3AudioPlayerEngine` 与 `PlaybackSessionHost`；
4. 当进入 `Playing` / `Buffering` 状态时，自动获取双硬件锁；在 `Paused`、`Idle`、`Ended`、`stop()` 或 `release()` 时立即释放硬件锁，杜绝电量泄漏；
5. 在 `AndroidManifest.xml` 中声明 `WAKE_LOCK` 权限；
6. 编写了针对 `StreamingHardwareLockManagerTest` 的单元测试，并在 `PlaybackSessionHostTest` 中集成了完整的生命周期加锁/释锁断言验证。
