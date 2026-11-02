# 02: Bounded Retry and Thread Release in Streaming Data Source

**What to build:** Deterministic failure handling and prompt background thread release when a WebDAV server becomes unreachable or network drops completely. The resilient WebDAV data source enforces a 5-attempt retry threshold with exponential backoff (totaling approximately 19 seconds) instead of retrying indefinitely. If the outage persists across all 5 retries, the data source ceases looping, releases ExoPlayer's loader thread immediately, and rethrows the underlying I/O exception. When a user skips to the next track or selects a different song, the player starts loading immediately without freezing or suffering from thread starvation.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `ResilientWebDavDataSource` uses `DEFAULT_MAX_RETRIES = 5`
- [ ] After 5 consecutive retry attempts during `open()` or `read()`, the reconnect loop terminates and rethrows the underlying `IOException`
- [ ] Closing the data source promptly interrupts any pending sleep or backoff wait
- [ ] Loader thread is released without hanging when network connections are severed
- [ ] Unit tests in `ResilientWebDavDataSourceTest` verify the 5-attempt retry cap, backoff progression, and exception rethrow
