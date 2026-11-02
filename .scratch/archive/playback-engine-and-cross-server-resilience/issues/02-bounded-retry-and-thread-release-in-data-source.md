# 02: Bounded Retry and Thread Release in Streaming Data Source

**What to build:** Deterministic failure handling and prompt background thread release when a WebDAV server becomes unreachable or network drops completely. The resilient WebDAV data source enforces a 5-attempt retry threshold with exponential backoff (totaling approximately 19 seconds) instead of retrying indefinitely. If the outage persists across all 5 retries, the data source ceases looping, releases ExoPlayer's loader thread immediately, and rethrows the underlying I/O exception. When a user skips to the next track or selects a different song, the player starts loading immediately without freezing or suffering from thread starvation.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] `ResilientWebDavDataSource` uses `DEFAULT_MAX_RETRIES = 5`
- [x] After 5 consecutive retry attempts during `open()` or `read()`, the reconnect loop terminates and rethrows the underlying `IOException`
- [x] Closing the data source promptly interrupts any pending sleep or backoff wait
- [x] Loader thread is released without hanging when network connections are severed
- [x] Unit tests in `ResilientWebDavDataSourceTest` verify the 5-attempt retry cap, backoff progression, and exception rethrow

## Answer
1. 将 `ResilientWebDavDataSource.DEFAULT_MAX_RETRIES` 从 `Int.MAX_VALUE` 调整为确定的 `5`。
2. 5 次重试退避调度（1s, 2s, 4s, 6s, 6s，总计 19 秒）耗尽后，立即设置 `isReconnecting = false` 并抛出底层 `IOException`，配合上层 `PlaybackFaultTolerance` 弱网看门狗及时触发错误状态与恢复策略。
3. 在 `open()` 与 `read()` 期间通过 `activeThread` 记录当前执行线程，当外部调用 `close()` 时若有工作线程处于退避休眠或阻塞状态，主动触发 `interrupt()` 并抛出 `InterruptedIOException`，确保 ExoPlayer 内部 Loader 线程与 NAS 离线时的阻塞调用即时释放。
4. 在 `ResilientWebDavDataSourceTest` 中新增 `defaultMaxRetries_isFive`、`persistentFailureDuringOpen_terminatesAfterFiveRetries_andRethrows`、`persistentFailureDuringRead_terminatesAfterFiveRetries_andRethrows` 与 `close_whileSleepingInBackoff_promptlyInterruptsThreadAndThrows` 单元测试，全部验证通过。
