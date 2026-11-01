# 01: Deep WebDAV MediaSource Adapter and Resilient Streaming

**What to build:** A self-contained, deep media source adapter that encapsulates all WebDAV streaming transport concerns behind a single minimal interface. Upper-level audio player components pass only the target active WebDAV Server and Audio Tracks, receiving a fully configured MediaSource. The adapter internally manages long-timeout streaming connections (30s+), automatic HTTP Basic Authentication header injection for chunk requests, exponential backoff retries for transient socket and network timeouts, and format-specific extractor selection (including specialized ASF/WMA and FLAC extractors). Transient network latency or slow server responses no longer terminate playback with unrecoverable source errors.

**Blocked by:** None (can start immediately)

**Status:** completed

## Acceptance criteria

- [x] A unified media source adapter accepts an active WebDAV Server and Audio Tracks and produces a ready-to-play MediaSource without exposing raw OkHttp client instances or manual extractor lists to the caller.
- [x] Network read and write timeouts for streaming connections are configured to at least 30 seconds to prevent premature socket timeout exceptions during audio chunk buffering.
- [x] HTTP Basic Authentication credentials from the WebDAV Server are automatically and securely attached to all media chunk requests without leaking into higher-level UI states.
- [x] Transient network failures (such as socket read timeouts, broken pipes, and connection resets) automatically trigger exponential backoff retries with at least 3 attempts before escalating to an error.
- [x] Non-recoverable HTTP errors (such as HTTP 401 Unauthorized, 403 Forbidden, and 404 Not Found) bypass retry attempts and fail fast with meaningful error diagnostics.
- [x] Audio format extractors (including custom ASF extractors for WMA playback) are automatically resolved and bound within the adapter based on the track's audio format.
- [x] MockWebServer-based automated tests verify streaming chunk retrieval, socket timeout retry recovery, and extractor dispatch through the adapter interface.
