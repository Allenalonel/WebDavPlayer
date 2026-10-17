# 03: Non-206 HTTP Response Interception and Confirmation Flow

**What to build:**
Detect and intercept unexpected `HTTP 200 OK` responses during ranged reconnection (`Range: bytes=offset-` with `offset > 0`). Prevent full-stream byte-zero data from corrupting active decoders or resetting playback to the beginning. The session layer pauses in place and presents an explicit confirmation dialog asking whether to replay the track from the beginning.

**Blocked by:** 01 Engine Clock Synchronization Precedence and Physical Duration Probe

**Status:** resolved

- [x] `ResilientWebDavDataSource` inspects HTTP response codes during `openRangeSource()` and throws `UnsupportedRangeReconnectionException` if status is `200 OK` on a ranged offset request.
- [x] Session layer catches the unsupported Range condition, retains playback position without advancing queue, and surfaces `isUnsupportedRange = true` in session state.
- [x] UI prompts the user with a confirmation dialog: "当前服务器不支持断点续传，是否从头重新播放此曲？"
- [x] Confirming replays the track from 0L; dismissing keeps playback paused at the cut-off position.
- [x] Unit tests in `ResilientWebDavDataSourceTest` verify 200 response interception and exception propagation.

## Answer

1. Added `UnsupportedRangeReconnectionException` thrown when HTTP response is 200 during ranged offset requests (`currentOffset > 0L`). Added to `isNonRetryable`.
2. Emitted cause in `PlaybackState.Error`. In `MusicPlayerAppSessionImpl`, intercepted unsupported range errors, paused playback, preserved position and queue index, and set `isUnsupportedRange = true`.
3. Implemented `confirmUnsupportedRangeReplay()` (seekTo 0L and replay) and `dismissUnsupportedRange()` (remain paused).
4. Added confirmation dialog in `FullPlayerView` prompting the user with "当前服务器不支持断点续传，是否从头重新播放此曲？" and handling confirm/dismiss.
5. Unit tests added and passing in `ResilientWebDavDataSourceTest`, `MusicPlayerAppSessionTest`, and `FullPlayerPresentationTest`. Commit `1cc09db`.
