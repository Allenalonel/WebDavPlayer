# 01: 15 分钟延迟前台服务降级状态机 (Deferred Foreground Demotion)

**What to build:** 当用户暂停播放时，播放器不立即注销前台服务和通知栏，而是保持高优先级前台身份并启动 15 分钟倒计时。若在 15 分钟内恢复播放，系统零延迟瞬间继续播放；若暂停超过 15 分钟未操作，前台服务优雅降级以节约电量。

**Blocked by:** None (can start immediately)

**Status:** completed

- [x] 播放器进入暂停（`PlaybackState.Paused`）状态时，前台服务标记（`isForegroundActive`）保持为 `true`，系统通知栏维持前台属性并刷新为暂停按键与最新进度。
- [x] 暂停时自动启动 15 分钟（`15 * 60 * 1000L`）延迟降级协程计时器；若在计时器运行期间接收到 `Playing` 或 `Buffering` 状态，立即取消该计时器并维持前台活跃。
- [x] 延迟降级计时器达到 15 分钟且未恢复播放时，优雅调用 `service.stopForeground(STOP_FOREGROUND_DETACH)`，将通知转为普通通知并释放前台身份以节约系统电量。
- [x] 当播放状态转为 `Idle` 或 `Ended` 且无后续曲目时，即刻取消延迟降级计时器并彻底移除前台通知。
- [x] 在 `PlaybackSessionHostTest` 中通过虚拟时钟调度器，完整覆盖暂停 14 分钟保持前台、15 分钟触发降级、中途恢复取消计时的单元测试。

## Answer

在 `PlaybackSessionHost` 中引入 `demotionJob` 与 `DEFAULT_DEMOTION_TIMEOUT_MS = 15 * 60 * 1000L`：
1. 当播放器进入 `PlaybackState.Paused` 时，前台服务保持活跃（`isForegroundActive = true`），仅在通知栏更新暂停与最新进度，并启动 15 分钟延迟降级作业；
2. 倒计时期间收到 `Playing` / `Buffering` 立即取消降级作业；
3. 超时未恢复则优雅调用 `service.stopForeground(STOP_FOREGROUND_DETACH)` 释放前台身份；
4. `Idle` / `Ended` / `stop` / `release` 时立即取消计时器并移除通知；
5. 在 `PlaybackSessionHostTest` 中通过 `StandardTestDispatcher` 与 `advanceTimeBy` 完整覆盖 14 分钟保持、15 分钟降级、恢复播放取消与主动停止的测试。
