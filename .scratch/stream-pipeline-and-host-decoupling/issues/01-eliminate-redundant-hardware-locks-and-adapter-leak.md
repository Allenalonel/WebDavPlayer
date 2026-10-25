# 01: 硬件锁归属内化至流式管道并消除适配器冗余传递

**What to build:** 消除硬件休眠锁（Dual Streaming Hardware Locks）在媒体源适配器与服务宿主之间的多头传递与重复操作。流式播放管道在播放与缓冲时内部自主加锁，在暂停与结束时自主释放锁，避免外部模块冗余感知底层锁细节。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] `DefaultWebDavMediaSourceAdapter` 构造函数彻底移除未被调用的 `hardwareLockManager` 参数与字段
- [x] `Media3AudioPlayerEngine` 作为流式管道唯一持有并驱动 `StreamingHardwareLockManager` 的生命周期（Playing/Buffering 加锁，Paused/Idle/Error/Ended/release 释放锁）
- [x] `PlaybackSessionHost` 内部移除多余的重复加锁与解锁操作，不再跨接缝强转读取引擎的锁实例
- [x] 既有单元测试与硬件锁治理测试编译通过且行为无回归

## Comments

### Resolution
- 从 `DefaultWebDavMediaSourceAdapter` 构造函数及类成员中彻底移除了未被使用的 `hardwareLockManager` 参数与属性，消除跨层锁传递泄漏。
- 在 `Media3AudioPlayerEngine` 中收敛了 `StreamingHardwareLockManager` 的唯一持有权与完整生命周期驱动：
  - 构造参数默认直接实例化 `DefaultStreamingHardwareLockManager(context)`，不再从 `mediaSourceAdapter` 向下强转读取；
  - 在 `Playing` 与 `Buffering`（`playWhenReady == true`）状态自动 `acquireLocks()`；
  - 在 `Paused`、`Idle`、`Error`（含 `onPlayerError` 异常回调即时释放）、`Ended`、`stop()` 时调用 `releaseLocks()`，在 `release()` 时调用 `release()`。
- 从 `PlaybackSessionHost` 中移除了 `hardwareLockManager` 构造参数与属性、对 `(playerEngine as? Media3AudioPlayerEngine)?.hardwareLockManager` 的跨接缝强转，以及 `handlePlaybackStateChanged`、`detachService`、`stop`、`release` 中的全部重复加锁与释锁调用。
- 在 `Media3AudioPlayerEngineTest` 中新增并覆盖了完整的硬件休眠锁全生命周期单元测试（`hardwareLocks_fullLifecycle_acquiresOnPlayingAndBuffering_releasesOnPausedErrorEndedIdleStopAndRelease`），并清理了 `PlaybackSessionHostTest` 与 `EndToEndSystemFocusAndRouteRegressionTest` 中跨越宿主接缝断言硬件锁的冗余代码，全量单元测试通过。
