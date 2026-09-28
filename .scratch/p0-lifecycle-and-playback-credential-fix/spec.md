# Spec: P0 Lifecycle and Playback Credential Fix

Status: ready-for-agent

## Problem Statement

When users stream audio from WebDAV servers, they encounter two severe, crash-level failure modes that break core playback stability:

1. **Fatal Android Service Lifecycle Crash**: On Android 8.0+ devices, starting playback from an idle state or resuming from cold start frequently triggers an uncatchable operating system crash (`RemoteServiceException: Context.startForegroundService() did not then call Service.startForeground()`). This happens because the media service start is initiated asynchronously, and foreground notification elevation is delayed until the audio engine reaches an active playback state. When network latency, SSL handshakes, or initial buffering exceed 5 seconds, the operating system forcibly terminates the application process.
2. **Streaming Interruption Due to Credential Loss (HTTP 401 & SSL Failures)**: When streaming from WebDAV servers requiring HTTP Basic authentication or utilizing self-signed SSL certificates, audio playback starts for less than a second and then abruptly aborts with an authentication or SSL handshake error. This occurs because the background metadata enrichment pipeline calls track update logic on the playback engine, which replaces the media item inside the player. The player engine's default media source factory lacks the server's authentication credentials and custom SSL configuration, causing the rebuilt stream to issue an unauthenticated request that is immediately rejected by the remote server.
3. **Audio Re-buffering and Playback Glitches During Metadata Resolution**: Replacing the active media item in the player engine while a track is currently streaming forces ExoPlayer to tear down and reconstruct its media pipeline, causing audible glitches, buffering latency, and playback stutter.

## Solution

A targeted, robust architecture fix that eliminates the lifecycle race condition and guarantees credential retention across the entire playback pipeline:

1. **Zero-Delay First-Frame Service Elevation**: The media service unconditionally and synchronously elevates to a foreground service on the very first frame of its creation lifecycle, posting an initial notification before any asynchronous playback preparation occurs. This satisfies Android's 5-second foreground service contract in zero milliseconds, completely immunizing the application against `RemoteServiceException` regardless of network latency or server response times.
2. **Dynamic Credentialed Media Source Factory**: The audio playback engine's global media source factory is backed by a dynamic, server-aware data source factory linked to the WebDAV media source adapter. Whenever the player engine constructs or reconstructs a media source for any media item, the request automatically inherits the active server's Basic Authentication headers, timeouts, and self-signed SSL socket factory.
3. **Non-Disruptive In-Flight Metadata Enrichment**: Track metadata updates for the actively playing track update the application session state and external media session without executing a destructive media item replacement on the active player timeline, eliminating rebuffering glitches and safeguarding ongoing audio playback.

## User Stories

1. As a listener on a high-latency cellular network, I want playback initiation to be completely immune to foreground service timeouts, so that the app never crashes while waiting for the remote WebDAV server to respond.
2. As a listener on Android 8.0 or newer, I want the playback service to synchronously fulfill its foreground service contract on startup, so that the operating system never terminates the player process.
3. As a listener tapping play while the player is currently idle or buffering, I want a system notification to appear immediately, so that I have clear visual feedback that playback is preparing.
4. As a listener streaming from a private WebDAV server requiring authentication, I want playback to continue uninterrupted when track metadata finishes loading in the background, so that I am never cut off by an unexpected HTTP 401 error.
5. As a listener streaming from a NAS with a self-signed SSL certificate, I want background metadata enrichment to preserve custom SSL trust configurations, so that streaming is never interrupted by SSL validation errors.
6. As an audiophile streaming lossless FLAC or WAV tracks, I want metadata enrichment to occur without audio hiccups, so that my music playback remains continuous and smooth.
7. As a listener whose network fluctuates, I want player-level stream retries to always carry valid server credentials, so that transient connection recoveries succeed seamlessly.
8. As a listener switching tracks rapidly, I want service foreground elevation and notification updates to remain consistent, so that the media service is never left in an unattached or invalid state.
9. As a listener pausing and resuming audio, I want transitions between foreground and background service states to obey Android system contracts strictly, so that the service is neither prematurely killed nor leaked.
10. As a listener controlling playback from the system lock screen, I want lock screen playback controls to remain reliable and synchronized even while background metadata parsing is active.
11. As a listener playing long albums, I want successive track transitions to seamlessly retain the active server's streaming credentials without manual re-authentication.
12. As a listener with slow WebDAV DNS resolution, I want the app to wait patiently for the remote server without timing out at the operating system service level.
13. As an engineer maintaining the playback subsystem, I want the media service lifecycle to be strictly deterministic and decoupled from remote network latency.
14. As an engineer testing the playback engine, I want media source creation to be centralized behind an authenticated adapter seam, so that credentials cannot be dropped by internal player mechanisms.
15. As an engineer diagnosing streaming failures, I want confidence that background metadata updates will never corrupt an active media session or drop network headers.

## Implementation Decisions

1. **Immediate Lifecycle Fulfillment for Media Service**:
   - The media service synchronously invokes foreground promotion within its initial creation lifecycle callback, utilizing the session host's notification provider to post a valid notification immediately.
   - The service establishes foreground status at time zero before delegating to any asynchronous player state observers.
   - Decouple foreground service contract fulfillment from remote player state transitions (`Playing` vs `Buffering` vs `Idle`), ensuring the 5-second operating system deadline is never dependent on remote server responsiveness.
   - Maintain foreground state tracking inside the session host so that subsequent playback state events cleanly modulate notification content and ongoing flags.

2. **Global Dynamic Authentication for Media Source Generation**:
   - Configure the audio playback engine's global media source factory with a dynamic delegating data source factory.
   - This delegating factory dynamically queries the active WebDAV server context from the playback engine and provisions data sources equipped with the server's Basic Authentication credentials, connection pooling, and SSL trust manager.
   - All internal player operations that derive a media source from a media item (including timeline refreshes, retries, and item substitutions) automatically produce authenticated requests, eliminating naked HTTP requests.

3. **Non-Disruptive In-Flight Track Update Strategy**:
   - Differentiate between active playing tracks and queued or idle tracks during metadata enrichment:
     - For inactive or queued tracks: The media item in the playlist is safely updated.
     - For the currently active track: If the track is actively playing, the engine avoids invoking destructive media item replacement on the player timeline. Instead, metadata updates are propagated reactively via the application session state and updated on the media session, preserving active buffer continuity.
   - Presentation layer components (docked mini-player, full player sheet) bind to the reactive session state, guaranteeing instant UI updates without requiring timeline reconstruction in the decoder.

## Testing Decisions

- **Good Test Criteria**: Tests must verify observable behavior across high-level architectural seams rather than asserting on internal private method calls or volatile implementation details.
- **Seam 1: Media Service Lifecycle Seam (`PlaybackSessionHost` / `WebDavMediaService`)**:
  - Verify that when the media service is created, foreground elevation occurs synchronously without waiting for playback state transitions.
  - Verify that when the service attaches to the host, the foreground active flag is correctly established and notifications reflect the current session state.
- **Seam 2: Audio Player Engine Authenticated Streaming Seam (`AudioPlayerEngine` / `WebDavMediaSourceAdapter`)**:
  - Verify that calling track updates on the playback engine while streaming against an authenticated mock server does not trigger HTTP 401 errors.
  - Verify that media items reconstructed by the player engine's media source factory retain the active server's authentication headers and custom SSL configurations.
  - Verify that updating metadata for the currently playing track does not disrupt active playback state or reset current playback position.
- **Prior Art**:
  - `PlaybackSessionHostTest.kt`: Unit tests verifying host-service attachment and notification elevation.
  - `WebDavMediaServiceTest.kt`: Unit tests verifying service lifecycle and intent handling.
  - `Media3AudioPlayerEngineTest.kt`: Unit tests verifying track playback, position reporting, and queue manipulation.
  - `WebDavStreamingPlaybackTest.kt`: Integration tests verifying authenticated HTTP Range streaming against MockWebServer.

## Out of Scope

- Modifying the 128KB HTTP Range chunk size or Room database caching behavior for album cover art (designated as a separate P1 issue).
- Refactoring the LRC parser to eliminate inline word-level timestamp duplicates (designated as a separate P1 issue).
- Adding MP4 container box parsing for M4A/AAC metadata (P2 issue).
- Registering an audio becoming noisy broadcast receiver for headphone disconnects (P2 issue).

## Further Notes

- Resolving these two P0 issues establishes the fundamental operational baseline of the application: uninterrupted authenticated streaming and zero-crash service lifecycle compliance on Android 8.0+.
- All domain vocabulary strictly matches `CONTEXT.md` (Active Server, Audio Track, Streaming Buffer, Playback Session State).
