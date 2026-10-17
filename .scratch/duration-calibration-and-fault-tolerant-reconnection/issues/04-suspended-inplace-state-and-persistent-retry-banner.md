# 04: Suspended In-Place State and Persistent Retry Banner

**What to build:**
When network connectivity is disrupted and automatic backoff reconnection attempts exceed the quiet window (e.g., 30s), playback transitions into an in-place suspended state. Both the mini player and full player views display a prominent, persistent retry banner with an action button ("网络连接受阻，点击重试"). Tapping retry resumes streaming from the exact millisecond where it paused without changing queue index or skipping tracks.

**Blocked by:** 02 Multi-Tier Completion Arbitration and Uninterrupted Queue Progression

**Status:** ready-for-agent

- [ ] Watchdog flags `canManualRetry = true` and `isWeakNetwork = true` after quiet window exhaustion without altering queue state.
- [ ] MiniPlayer and FullPlayerView render a persistent, accessible banner with a "点击重试" action.
- [ ] Invoking retry triggers in-place resumption from the preserved playback timestamp.
- [ ] UI integration tests verify banner visibility and retry click handling across both player views.
