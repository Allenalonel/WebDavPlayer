# Spec: Physical Duration Calibration, Fault-Tolerant Reconnection, and Uninterrupted Queue Progression

Status: resolved

## Problem Statement

When listening to audio tracks via WebDAV, users experience an issue where the player repeatedly loops the current track instead of advancing to the next track in the playback queue.

This degradation stems from two interdependent failure modes:

1. **Clock Desynchronization and Duration Guard False Truncation**: When an audio track completes its natural playback, the engine notifies state listeners before updating the cached position and duration. Furthermore, static metadata tags (such as MP3 ID3v2 TLEN or VBR estimation) frequently overestimate playable audio duration by seconds compared to actual audio frames. When natural playback finishes, the Duration Guard mistakenly flags the delta between the stale/metadata duration and playback position as a premature network cut-off. Because the guard lacked an upper limit and physical EOF validation, it repeatedly seeks to the end position and loops the same track indefinitely.
2. **Ambiguous Stream Disconnection vs. Incompatible Range Handling**: During mobile network fluctuations or long-lived streams, connections can drop mid-song. If an intermediary proxy or server fails to respect HTTP Range requests (returning `200 OK` rather than `206 Partial Content`), feeding full data from byte zero into an active decoder causes position resets and audio glitches, leading to perceived restarts of the current track.

Users require an experience matching industry-standard players (such as Symfonium and Salt Player): accurate physical duration-driven track transitions, robust in-place reconnection on transient network drops, and explicit in-place suspension with a clear retry prompt when reconnection is exhausted or Range is unsupported—never silently skipping tracks or looping endlessly.

## Solution

From the user's perspective:

1. **Seamless Queue Advancement**: When a song plays to the end, it reliably and automatically advances to the next track in the queue across all audio formats (MP3, FLAC, WAV, WMA). Songs with slight metadata duration discrepancies will no longer trigger false network truncation alarms.
2. **In-Place Network Resilience**: If network jitter occurs during streaming, playback pauses in place and automatically retries reconnecting via HTTP Range requests without skipping forward or restarting from the beginning.
3. **Suspended State with Persistent Retry Prompt**: If network connectivity remains unavailable after automatic retry attempts, the player preserves the exact playback position, stays paused, and displays a prominent persistent retry banner and button across both the mini player and full player views ("网络连接受阻，点击重试").
4. **Unsupported Range Explicit Confirmation**: If a server or proxy does not support HTTP Range requests and returns full content upon reconnection, the player stops playback at the disconnection point and prompts the user with a confirmation dialog rather than unexpectedly restarting the track from the beginning.

## User Stories

1. As a listener, I want my music player to automatically advance to the next song when the current song finishes, so that my listening session continues without manual intervention.
2. As a listener playing MP3 files with VBR encoding or inaccurate TLEN tags, I want the player to recognize when the audio frames have naturally completed, so that the player does not get stuck repeating the last few seconds of the song.
3. As an audiobook listener, I want the player to never skip ahead to the next track when my network drops mid-chapter, so that I never lose my place or miss content.
4. As a listener on a weak or fluctuating Wi-Fi/cellular connection, I want the player to attempt transparent in-place reconnection, so that brief network stutters resume smoothly without restarting from the beginning.
5. As a listener experiencing an extended network outage, I want the player to pause in place and show a clear "网络连接受阻，点击重试" prompt in both the mini player and full screen player, so that I know why playback halted and can resume with a single tap once signal returns.
6. As a listener who taps "点击重试" on the network error prompt, I want the player to resume streaming from the exact millisecond where it paused, so that my listening experience remains continuous.
7. As a listener using a WebDAV server that does not support HTTP Range headers (returning HTTP 200 on Range requests), I want the player to pause and present a confirmation dialog asking whether to replay the track from the beginning, so that I am never surprised by unexpected restarts.
8. As a listener controlling playback from Bluetooth headphones or notification controls, I want skip next and skip previous to always function cleanly without being blocked by lingering reconnection routines.
9. As a listener navigating between directories while a song is playing, I want metadata enrichment to use the physical decoder duration once available, so that timeline progress bars reflect true audio length.
10. As a listener switching between Single Loop, List Loop, and Shuffle modes, I want natural track completion to obey the selected playback mode without Duration Guard misinterpreting mode loops as network failures.

## Implementation Decisions

### 1. Clock Update Strict Precedence & Synchronous Probe

- In the audio player engine module, `Player.Listener` callbacks for playback state changes (`onPlaybackStateChanged`, `onPositionDiscontinuity`, `onIsPlayingChanged`) must synchronously refresh position, duration, and buffered position before emitting state notifications to subscribers.
- Prioritize decoder-measured physical duration (`player.duration`) over static tag-parsed metadata once playback has begun and physical duration is non-zero.

### 2. Multi-Tier Natural Completion Arbitration in Session Layer

- Revise the completion handler in the session management module:
  - **Tier 1 (Physical Match)**: If the player engine reports genuine end-of-stream (`STATE_ENDED`) and the current position is within physical tolerance of physical duration (or the decoder flags end of input stream without an underlying network exception), confirm natural completion and trigger queue progression.
  - **Tier 2 (Completion Progress Threshold)**: If static metadata duration exceeds physical playback position due to container padding, trailing silence, or encoder delay, but playback progress has reached or exceeded 95% of total duration without network I/O errors, treat as legitimate completion and proceed to the next track.
  - **Tier 3 (Premature EOF Reconnection & Bounded Retry)**: If playback terminates prematurely before the 95% threshold and physical duration was not reached, classify as genuine network interruption. Retain playback position, enter buffering state, and attempt in-place resumption.

### 3. Extended Reconnection Expiry to Suspended UI State

- Maintain in-place reconnection in the WebDAV data source and session watchdog.
- When background network recovery attempts reach the quiet threshold (e.g., 30s) without recovery, transition session state to `isWeakNetwork = true` and `canManualRetry = true`.
- Mini player and full player UI components surface the persistent banner and retry button ("网络连接受阻，点击重试").
- Tapping retry triggers an in-place seek and resume without queue progression or index alteration.

### 4. Non-206 HTTP Response Interception and User Confirmation Flow

- In the custom WebDAV HTTP data source, when performing a ranged reconnection (`Range: bytes=offset-` with `offset > 0`), check the HTTP response status code.
- If the server returns `HTTP 200 OK` instead of `HTTP 206 Partial Content`, reject consuming the data stream to prevent audio frame corruption and position reset.
- Propagate an explicit signal (`UnsupportedRangeReconnectionException` or session event) up to the session and UI.
- The UI displays an alert dialog explaining that the remote server does not support partial range streaming and asks the user whether to restart playback of the current track from the beginning.

## Testing Decisions

- **Testing Principles**: Tests will evaluate external observable behavior through public interfaces (`AudioPlayerEngine`, `MusicPlayerAppSession`, and UI states) rather than internal private variables.
- **Modules Tested**:
  - `MusicPlayerAppSessionTest`: Verify that natural `STATE_ENDED` transitions advance queue indices when playback is at 95%+ completion even if metadata duration is inflated.
  - `MusicPlayerAppSessionTest`: Verify that genuine premature EOF (< 95%) halts queue progression, preserves current index, seeks to current position, and transitions to buffering / manual retry without advancing.
  - `Media3AudioPlayerEngineTest`: Verify that `onPlaybackStateChanged(STATE_ENDED)` updates position and duration before emitting state changes.
  - `ResilientWebDavDataSourceTest`: Verify that receiving `200 OK` on a ranged reconnect request aborts data streaming and emits an incompatible Range error rather than delivering byte-zero data as ranged content.
- **Prior Art**:
  - `app/src/test/java/com/webdav/player/data/player/WmaPlaybackDiagnosisTest.kt`
  - `app/src/test/java/com/webdav/player/domain/session/MusicPlayerAppSessionTest.kt`
  - `app/src/test/java/com/webdav/player/data/player/ResilientWebDavDataSourceTest.kt`

## Out of Scope

- Implementing persistent on-disk audio caching (strictly prohibited by ADR-0002).
- Modifying ID3 tag parsing libraries or writing tag editing features.
- Dynamic audio transcoding on the client or server side.
- Changes to CUE sheet parsing or virtual track offset calculations (already handled by `VirtualTimelineEngine`).

## Further Notes

- Aligns directly with ADR-0002 (pure streaming zero-disk cache), ADR-0015 (session-managed queue progression), and ADR-0016 (stream rendering pipeline).
- Fixes user-reported infinite loop on single tracks without compromising the anti-skip protections for audiobooks and continuous listening.
