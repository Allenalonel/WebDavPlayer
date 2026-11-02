# Spec: Playback Engine Deepening, Cross-Server Credential Synchronization, and Foreground Service Resilience

Status: implemented

## Problem Statement

Listeners playing audio from multiple remote WebDAV servers and custom cross-server playlists face three critical playback and stability barriers:

1. **Authentication Failures During Natural Gapless Transitions Across Servers**:
   When listening to a custom playlist containing tracks from different WebDAV servers (e.g. Server A and Server B), the player successfully prerolls and starts playing track 2 from Server B. However, the application session state and underlying audio engine fail to update their active server reference to Server B upon natural gapless track transition. If a transient network glitch occurs, if the listener manually seeks on the scrub bar, or if premature EOF arbitration triggers, the underlying streaming data source requests bytes from Server B while mistakenly presenting Server A's authentication credentials. The remote server immediately rejects the request with HTTP 401 Unauthorized, causing the track to fail and be erroneously marked inactive.

2. **UI Freezes and Thread Starvation from Infinite Data Source Retries**:
   When a user's home NAS goes offline (due to sleep, power outage, or IP reassignment) or when network connectivity drops completely, the resilient streaming data source attempts infinite retries (`Int.MAX_VALUE`). Even though the higher-level playback watchdog pauses playback and presents a retry banner after 15-30 seconds, ExoPlayer's dedicated background loader thread remains trapped in a blocking sleep-and-reconnect loop. If the listener skips to another track or attempts to interact with the player, the old loader thread cannot be promptly released, freezing playback progression and delaying new audio playback by 5 to 10 seconds.

3. **Background Process Kill on Modern Android (API 34) Due to Service Elevation Timing**:
   Under modern Android execution limits, invoking `startForegroundService` from the background is strictly forbidden unless within specific temporary exemption windows (such as incoming system media button broadcasts). When playback is initiated from external Bluetooth buttons, lock-screen controls, or deferred resumption while the activity is hidden, failures to elevate the foreground service can leave the audio engine playing without active foreground service protection or a persistent notification. As a consequence, Android's Low Memory Killer (LMK) quickly terminates the background process as an unauthorized battery-draining app.

## Solution

1. **Atomic Session and Engine Credential Synchronization on Gapless Progression**:
   - Explicitly extend the audio player engine contract to support active server updates.
   - When the timeline coordinator observes a natural track transition emitted by the engine, it inspects the newly active track's server identity.
   - If the server has changed, the coordinator asynchronously resolves the new server context from the server repository, atomically updates the active server in the session state flow, and synchronizes the audio engine's active server reference.
   - All subsequent HTTP Range reconnections, scrubbing operations, and watchdog checks automatically use the correct server credentials.

2. **Bounded Retry Threshold with Fast Thread Release**:
   - Enforce a deterministic retry threshold (5 consecutive attempts with exponential backoff: 1s, 2s, 4s, 6s, 6s) in the streaming data source.
   - If an outage persists across all 5 retry attempts, terminate the reconnect loop and immediately rethrow the underlying I/O exception.
   - Promptly release ExoPlayer's loader thread and transition playback state to error, enabling higher-level fault tolerance to handle server reachability or advance the queue without thread starvation.

3. **Synchronous Foreground Service Elevation Within System Exemption Windows**:
   - Ensure media service commands initiated via media button broadcasts, notification intents, or session callbacks invoke foreground service elevation synchronously on the main thread within the system's temporary exemption window.
   - In the session host, harden background start exception handling so that any transient service start rejections maintain recoverable player state and gracefully reconcile with the foreground service whenever the application window regains focus.

## User Stories

1. As a listener playing a cross-server playlist, I want the player to seamlessly transition from a track on Server A to a track on Server B, so that playback continues without interruption.
2. As a listener playing a track from Server B that naturally progressed from Server A, I want to seek backward or forward on the progress bar, so that position changes succeed without triggering false HTTP 401 authentication errors.
3. As a listener streaming a cross-server track during fluctuating Wi-Fi, I want mid-stream range reconnections to send the correct server credentials, so that playback resumes smoothly instead of crashing.
4. As a listener whose cross-server track experiences network interruption near its end, I want duration calibration and premature EOF checks to query the correct server, so that valid tracks are not mistakenly flagged as inactive.
5. As a listener streaming a cross-server track that finishes, I want adaptive preroll for the subsequent track (Server C) to evaluate reachability against the true active server, so that subsequent pre-buffering functions reliably.
6. As a listener whose NAS unexpectedly disconnects, I want the player to stop retrying after 5 attempts, so that the background thread does not loop indefinitely and drain battery.
7. As a listener whose current track fails due to a disconnected server, I want to tap "Next Track", so that the player immediately begins loading the next track without a 10-second thread hang.
8. As a listener on a train entering a tunnel, I want the player to fail cleanly and present a retry banner, so that I can manually resume once connectivity returns.
9. As a listener using Bluetooth headphones while walking with my phone locked, I want pressing the play button to resume playback without the app being killed by Android, so that background listening is dependable.
10. As a listener resuming playback from the system lock-screen notification, I want the foreground service and media notification to remain active, so that playback controls stay accessible.
11. As an Android 14 user, I want the media service to respect platform background execution boundaries, so that the app complies with OS power management without crashes.
12. As a listener whose app was demoted after the 15-minute idle grace period, I want pressing play on my car stereo to cleanly elevate the foreground service and resume streaming.
13. As a user reviewing playlist entries after a server hiccup, I want tracks not to be falsely marked with "Invalid Credentials" when the server was simply transitioning, so that my playlists remain clean.
14. As an audiophile playing high-resolution gapless albums across different storage nodes, I want acoustic continuity without stutter or credential churn at boundary transitions.

## Implementation Decisions

- **Engine Active Server Mutability**:
  - Deepen the `AudioPlayerEngine` domain interface with a dedicated method `setActiveServer(server: WebDavServer)` to update the streaming credential context dynamically.
  - Implement this in `Media3AudioPlayerEngine` by updating its atomic reference `activeServerRef` on the application looper.
  - This ensures that any subsequent data source instantiations through `delegatingDataSourceFactory` immediately inherit the correct server credentials.

- **Timeline Coordinator Gapless Progression Synchronization**:
  - In `PlaybackTimelineCoordinator`, enhance `startTrackTransitionsObservation` to compare the server ID of the next queue track with the currently active server.
  - When a server transition is detected, resolve the corresponding `WebDavServer` from `ServerRepository` asynchronously and dispatch an atomic update to `sessionStateFlow` and `playerEngine.setActiveServer`.
  - Ensure this update occurs before subsequent preroll triggers or progress evaluations can fire.

- **Bounded Reconnection Policy in `ResilientWebDavDataSource`**:
  - Replace `DEFAULT_MAX_RETRIES = Int.MAX_VALUE` with `DEFAULT_MAX_RETRIES = 5`.
  - When `retryCount >= maxRetryAttempts`, cease looping, set `isSourceOpen = false`, and rethrow the captured exception.
  - This guarantees that an unrecoverable network failure escapes the blocking `open()` / `read()` loops after approximately 19 seconds of total backoff, allowing ExoPlayer to invoke `onPlayerError`.

- **Foreground Service Elevation Synchronicity and Recovery**:
  - In `WebDavMediaService.onStartCommand`, execute foreground elevation immediately upon receiving play intents within the broadcast dispatch window.
  - In `PlaybackSessionHost.elevateForeground`, handle any platform `ForegroundServiceStartNotAllowedException` gracefully without poisoning the session state, and register a reconciliation check in `prewarmService()` to re-elevate whenever `MainActivity.onStart()` is triggered.

## Testing Decisions

- **Focus on Observable External Contracts**:
  - Tests will exercise the public behavior of the player session, engine transitions, and data source, asserting on emitted states, transitions, and network interactions.
  - **Cross-Server Transition Credential Verification**:
    - Extend `MusicPlayerAppSessionTest` and `PlaybackTimelineCoordinatorTest` with a two-track queue containing tracks from Server 1 and Server 2.
    - Simulate a natural track transition from track 0 to track 1 and assert that `sessionState.activeServer` and `playerEngine.activeServer` are atomically updated to Server 2.
  - **Bounded Retry and Thread Release Verification**:
    - Exercise `ResilientWebDavDataSourceTest` with persistent network disconnect exceptions.
    - Assert that after exactly 5 retry attempts, the data source throws an `IOException` rather than sleeping indefinitely.
    - Assert that total elapsed backoff matches the 5-attempt schedule and that closing the source promptly unblocks any pending thread.
  - **Foreground Service Elevation Lifecycle Verification**:
    - In `PlaybackSessionHostTest` and `WebDavMediaServiceTest`, verify that transitioning to playing state triggers foreground elevation safely, and verify that transient start failures do not corrupt playback state or abort subsequent playback commands.
  - **Full Regression & Packaging Verification**:
    - Run the entire automated test suite (`./gradlew testDebugUnitTest`) to ensure zero regressions across all 30 existing test fixtures.
    - Run `./gradlew verifyReleasePackaging` to ensure the release APK remains under the 7.5 MB size budget and the single classes.dex budget (<= 4.0 MB).

## Out of Scope

- Introducing new third-party dependency injection frameworks or service locator libraries.
- Modifying the underlying SQLite database schema (no changes to Room tables or migrations needed).
- Changing the 15-minute deferred demotion grace period duration (ADR-0017 invariants remain strictly preserved).

## Further Notes

- In `ResilientWebDavDataSource`, the 5-attempt backoff schedule (1000ms, 2000ms, 4000ms, 6000ms, 6000ms) totals 19,000ms, which aligns cleanly with the 15-second weak network watchdog threshold in `PlaybackFaultTolerance`, creating an orchestrated defense in depth.
