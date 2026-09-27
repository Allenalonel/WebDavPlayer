Status: ready-for-agent

# Spec: WebDAV Playback Pipeline Deepening and Two-Track Session Optimization

## Problem Statement

When users stream audio tracks from their remote WebDAV Server, they experience playback friction across three interconnected layers of the system:

1. **Brittle Streaming Pipeline & Error Cascades**: Streaming audio files (such as high-bitrate or WMA files) over fluctuating network connections suffers from fragmented network configurations and shallow adapters. The player engine leaks raw OkHttp network details, extractor composition, and retry policies. When a network connection encounters a temporary timeout or packet loss, the error penetrates directly through the layers to trigger an unrecoverable source error, abruptly killing playback for the user.
2. **Foreground Media Service Disconnect & System App Idle Termination**: The background playback service and the core audio playback engine suffer from bidirectional coupling leaks and cross-singleton downcasting. The playback engine triggers the Android foreground service via static utility calls, while the service reaches backwards into the application singleton to downcast the engine. When an error stops playback or when the player is idle in the background, the service fails to synchronize its foreground lifecycle with the operating system, causing Android's ActivityManager to forcibly kill the service with an "App Idle" termination.
3. **Main-Thread Scheduling Congestion & UI Frame Skipping**: Every sub-second tick of playback position updates is currently packed into a single monolithic session state object alongside the entire playback queue, server configuration, lyrics, and metadata. Copying and publishing this heavy state on the Android main thread causes severe Looper congestion (delays exceeding 350ms) and UI frame drops (skipping 40+ frames) during cold start and track transitions.

## Solution

A cohesive architectural deepening across the playback pipeline, background service host, and reactive session state:

1. **Deep WebDAV Media Source Adapter**: Fold the shallow data source factory, standalone load error policies, and extractor registrations into a unified, deep media source adapter. The adapter presents a minimal interface that accepts the active server and audio tracks, returning a self-contained media source that encapsulates long-timeout streaming connections, automatic Basic authentication header injection, exponential backoff retries on transient network failures, and format-specific extractor dispatch.
2. **Unified Playback Session Host**: Redesign the background media service into a self-contained playback session host following modern Android Media3 service architecture. The host encapsulates the engine lifecycle, audio focus negotiation, media notification sync, and foreground service elevation. Callers interact through a clean audio player engine interface, while the service autonomously ensures compliant foreground execution, preventing system App Idle killings.
3. **Two-Track Reactive State Model**: Decouple high-frequency millisecond progress updates from low-frequency structural session state. Structural state (active server, playback queue, current track, playback mode) updates calmly on significant domain events, while lightweight playback progress (current position, duration, buffering percentage) streams through a dedicated high-frequency flow computed off the main thread. This completely eliminates main-thread Looper delays and UI recomposition churn.

## User Stories

1. As a listener, I want my music to continue streaming seamlessly without interruption when the WebDAV server takes several seconds to respond to chunk requests, so that my listening experience is smooth.
2. As a listener streaming large or high-bitrate audio tracks over mobile networks, I want transient socket timeouts to automatically back off and retry in the background, so that temporary network latency does not abort my playback session.
3. As a listener playing WMA or specialized audio formats, I want the player to automatically attach the correct extractor without requiring manual decoder configuration, so that all supported tracks play transparently.
4. As a listener, I want the player to inject WebDAV authentication credentials securely into all underlying media stream chunk requests without leaking authorization parameters to upper-level UI components, so that my private audio library plays without authentication errors.
5. As a listener whose playback encounters a non-recoverable server error (such as HTTP 404 or 401), I want the playback engine to fail fast with a clear error description rather than retrying indefinitely, so that I immediately understand why the track cannot be played.
6. As a listener listening to music in the background or with the screen off, I want the playback service to maintain an active foreground notification and keep the audio process alive, so that the Android operating system does not kill the music due to perceived app idleness.
7. As a listener who pauses playback or experiences a temporary pause due to network buffering, I want the background playback service to remain gracefully active without crashing or being discarded by system background restrictions, so that I can resume listening whenever I am ready.
8. As a listener using headphone controls or car Bluetooth buttons, I want playback commands (play, pause, skip next, skip previous) to execute directly against the active media session without relying on fragile global application downcasts, so that remote control always works reliably.
9. As a listener experiencing an incoming phone call or navigation prompt, I want the playback session host to manage audio focus and duck or pause audio appropriately, restoring full volume when the interruption ends.
10. As a listener browsing a large directory while an audio track is playing, I want the directory list and navigation bar to render at full 60/120 FPS without stutter, so that browsing my music library feels responsive.
11. As a listener observing the docked mini-player, I want track title and album art to remain completely stable without unnecessary UI recomposition every few milliseconds, so that device battery life and rendering performance are preserved.
12. As a listener viewing the full player sheet, I want the playback progress bar and synchronized lyrics to update smoothly with millisecond accuracy, so that the visual feedback matches the audio.
13. As a listener resuming the application from a cold restart, I want the initial screen to appear instantly without 350ms main-thread Looper freezes, so that the app opens promptly.
14. As an engineer writing unit and integration tests for audio playback, I want to verify streaming resilience and retry behavior through a single media source interface, so that tests run fast and deterministically without spinning up full hardware audio tracks.
15. As an engineer maintaining the codebase, I want all foreground service lifecycle and notification logic confined strictly inside the playback host module, so that UI view models and repository layers remain clean and decoupled from Android framework quirks.

## Implementation Decisions

1. **Deep WebDAV Media Source Module**:
   - Create a unified Media Source module that serves as the single seam between the WebDAV network transport and the ExoPlayer playback engine.
   - The interface accepts an active WebDAV server and target audio tracks, and outputs a configured Media3 MediaSource.
   - Internally encapsulates:
     - Streaming OkHttpClient with extended read and write timeouts (30s+), dedicated connection pooling, and connection retry enabled.
     - HTTP Basic Authentication header injection for every chunk and range request.
     - Exponential backoff error handling policy providing minimum 3 retries for transient IO and socket timeouts, while immediately passing through permanent HTTP errors (401, 403, 404).
     - Automated format detection and extractor binding for standard extractors as well as custom format extractors (including ASF/WMA).
   - Deprecate and absorb the responsibilities of the previous thin data source factory.

2. **Unified Playback Session Host Module**:
   - Refactor the MediaSessionService implementation into a self-contained Playback Session Host.
   - The host maintains internal ownership of the playback engine instance and binds it directly to the Media3 MediaSession.
   - Outwardly exposes the standard audio player engine interface for domain and UI callers to control playback without any knowledge of Service intents, notification channels, or foreground service elevation requirements.
   - Eliminates all static service startup methods and reverse application downcasting.
   - Automatically synchronizes playback states (playing, paused, buffering, idle, error) with the Android foreground service status to protect against operating system App Idle shutdowns.
   - Manages audio focus transitions internally, handling ducking and transient losses without leaking audio focus events across unrelated components.

3. **Two-Track Reactive State Model**:
   - Re-architect the application session state into two distinct reactive streams:
     - **Structural Session State (`sessionState: StateFlow<PlayerSessionState>`)**: Emits only on coarse domain events (track transition, queue modification, active server change, playback mode toggle, error condition). Millisecond playback position is removed from this model.
     - **High-Frequency Playback Progress (`playbackProgress: StateFlow<PlaybackProgress>`)**: Emits lightweight progress data (current position in milliseconds, track duration in milliseconds, buffered position in milliseconds).
   - Compute and throttle progress ticks on a background coroutine dispatcher rather than Dispatchers.Main, pushing updates only to listening UI components that explicitly require high-frequency rendering (progress slider, lyric highlight).
   - UI components such as directory lists, breadcrumb strips, and mini-player summary labels subscribe exclusively to the low-frequency structural state, eliminating recomposition thrashing.

4. **Strict Architectural Seam Discipline**:
   - Adhere strictly to the three identified seams: the Media Source Seam, the Playback Session Host Seam, and the Two-Track State Seam.
   - Preserve `ADR-0002`: no audio stream chunk or full file will be saved to persistent local disk storage; streaming buffer remains ephemeral in memory.

## Testing Decisions

1. **What Makes a Good Test**:
   - Tests must exercise external behavior through public module interfaces at the defined seams, never asserting on private internal fields or implementation details.
   - Media source tests must simulate realistic network conditions (such as mock timeouts, HTTP 206 partial responses, and bad credentials) through MockWebServer and assert on successful retry recovery or proper terminal error delivery.
   - State model tests must verify that high-frequency progress emissions do NOT trigger emissions on the structural session state Flow, ensuring isolation.
   - Playback session host tests must verify correct foreground service state transitions and notification updates in response to engine playback state changes under Robolectric.

2. **Modules to Test**:
   - Deep Media Source module: verified with MockWebServer for socket timeout resilience, retry delays, authentication headers, and extractor selection.
   - Two-Track Session module: verified with coroutine test dispatchers for state isolation, throttle compliance, and cold-start restoration.
   - Playback Session Host: verified under Robolectric for service lifecycle, media session callbacks, and audio focus transitions.

3. **Prior Art in Codebase**:
   - `app/src/test/java/com/webdav/player/data/player/WebDavStreamingPlaybackTest.kt` (MockWebServer socket timeout and retry policy verification).
   - `app/src/test/java/com/webdav/player/data/player/Media3AudioPlayerEngineTest.kt` (Robolectric-based playback engine state verification).
   - `app/src/test/java/com/webdav/player/domain/session/MusicPlayerAppSessionTest.kt` (Flow-based session state assertion).

## Out of Scope

- Implementing persistent local audio disk caching or offline downloading (explicitly prohibited by `ADR-0002`).
- Redesigning UI layout, Material Design 3 color palettes, or typography styles.
- Modifying Room database entities or schema migrations for server or metadata storage.
- Adding third-party streaming protocols (e.g., Subsonic, Jellyfin, SMB); this spec focuses strictly on WebDAV HTTP/HTTPS streaming.

## Further Notes

- The deepening of these modules resolves the exact root causes observed in real-device telemetry (SocketTimeoutException on stream open, Service killed by ActivityManager on app idle, and 354ms Looper main delays).
- Breaking down the implementation into discrete sub-issues should proceed in the dependency order: (1) Media Source Deepening, (2) Two-Track State Model, (3) Playback Session Host Unification.
