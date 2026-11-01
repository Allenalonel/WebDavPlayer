# 04: Suspended In-Place State and Persistent Retry Banner

**What to build:**
When network connectivity is disrupted and automatic backoff reconnection attempts exceed the quiet window (e.g., 30s), playback transitions into an in-place suspended state. Both the mini player and full player views display a prominent, persistent retry banner with an action button ("网络连接受阻，点击重试"). Tapping retry resumes streaming from the exact millisecond where it paused without changing queue index or skipping tracks.

**Blocked by:** 02 Multi-Tier Completion Arbitration and Uninterrupted Queue Progression

**Status:** resolved

- [x] Watchdog flags `canManualRetry = true` and `isWeakNetwork = true` after quiet window exhaustion without altering queue state.
- [x] MiniPlayer and FullPlayerView render a persistent, accessible banner with a "点击重试" action.
- [x] Invoking retry triggers in-place resumption from the preserved playback timestamp.
- [x] UI integration tests verify banner visibility and retry click handling across both player views.

## Answer

Implemented in `MusicPlayerAppSessionImpl`, `MiniPlayer`, and `FullPlayerView`:
1. The buffering watchdog marks `canManualRetry = true` and `isWeakNetwork = true` when quiet window buffering exceeds 30s, preserving playback position and queue index.
2. In both `MiniPlayer` and `FullPlayerView`, rendered persistent, accessible banners showing "网络连接受阻，点击重试" with clickable retry triggers.
3. Invoking `retryPlayback()` resumes streaming in-place from the preserved timestamp.
4. Comprehensive UI and session tests added to `MiniPlayerPresentationTest`, `FullPlayerPresentationTest`, and `MusicPlayerAppSessionTest`. Commit `194afad`.
