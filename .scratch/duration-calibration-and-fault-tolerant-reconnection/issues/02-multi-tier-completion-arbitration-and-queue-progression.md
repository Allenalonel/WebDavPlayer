# 02: Multi-Tier Completion Arbitration and Uninterrupted Queue Progression

**What to build:**
Refactor the session layer's `handlePlaybackEnded()` to accurately differentiate legitimate end-of-stream from premature network cut-offs. Natural completion is confirmed if physical duration is matched or if playback progress has reached 95%+ of total duration, advancing the queue smoothly without falling into false Duration Guard loops. Genuine mid-track disconnections retain the track index and seek position to resume in place.

**Blocked by:** 01 Engine Clock Synchronization Precedence and Physical Duration Probe

**Status:** claimed

- [ ] Legitimate track completion advances the queue to the next track across List Loop and Shuffle modes when playback progress >= 95% of duration, even if static ID3 metadata is slightly inflated.
- [ ] Genuine premature EOF (< 95% of duration) prevents advancing the queue, preserves the current track index, seeks to the cut-off position, and triggers in-place buffering/resumption.
- [ ] Single Loop mode correctly replays the track from 0L upon legitimate completion without false Duration Guard alerts.
- [ ] Comprehensive unit tests in `MusicPlayerAppSessionTest` verify 95%+ tail release, physical match advancement, and premature cut-off retention.
