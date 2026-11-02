# Spec: System-Level Network Connectivity Awareness, Transparent Auto-Resumption, and Offline Playlist Self-Healing

Status: implemented

## Problem Statement

As an audiophile listening to music streamed over WebDAV from a private NAS, real-world network connectivity is volatile:

1. **Lack of Automatic Playback Resumption After Network Outages**:
   When commuting through subways, tunnels, or elevators, streaming buffers inevitably drain, and the player's weak-network watchdog transitions to a suspended pause state (`isWeakNetwork = true`, `canManualRetry = true`) to conserve device power and prevent infinite retries. However, when the listener exits the dead zone and mobile data or Wi-Fi reconnects with full internet capability, the player remains completely oblivious. The playback state remains indefinitely paused, forcing the listener to take out their phone, unlock the screen, and manually tap the "Retry" button on the notification or player sheet to resume listening.

2. **Ghost Resumption Risks if Auto-Play is Naively Implemented**:
   If network reconnection naively triggers playback unconditionally, serious user experience failures occur: if a listener intentionally paused music in a quiet office or if playback paused because headphones were unplugged (Becoming Noisy protection), regaining Wi-Fi would cause the phone to suddenly blare music out loud in public.

3. **Stale Inactive Playlist Tracks After Temporary Server Disconnections**:
   In cross-server playlists, tracks are marked inactive with a dimmed visual state (`isInactive = true`, `InactiveReason.SERVER_UNREACHABLE`) whenever a remote server cannot be reached during network downtime. Once internet connectivity is restored, these tracks stay permanently greyed out until the listener manually re-adds them, manually navigates the directory, or individually clicks every failed track to force a connection test.

## Solution

1. **System-Level Reactive Network Connectivity Observer**:
   - Establish a native Android `ConnectivityManager.NetworkCallback` observer that listens to real-time physical network capability transitions (`NET_CAPABILITY_INTERNET`, `NET_CAPABILITY_VALIDATED`) and transports (`WIFI`, `CELLULAR`).
   - Expose a clean reactive domain flow `networkStatus: StateFlow<NetworkStatus>` to the application architecture.
   - Introduce an in-memory 500ms debouncing window to filter high-frequency network flapping caused by entering or leaving weak signal zones.

2. **Ghost-Proof Transparent Auto-Resumption Pipeline**:
   - Wire the network status stream directly into the core playback session orchestrator and fault tolerance subsystem.
   - When network connectivity transitions from disconnected to connected:
     - Check the strict resumption precondition: the player must currently be in a suspended weak-network state (`sessionState.isWeakNetwork && sessionState.canManualRetry && sessionState.hasTrack`).
     - If the user explicitly initiated the pause, or if playback paused due to Becoming Noisy audio route changes, suppress auto-resumption completely to guarantee zero accidental sound output.
     - If suspended by a network outage, automatically trigger retry playback, smoothly reconnecting the streaming data source, resuming progress from the exact millisecond offset, and dismissing the weak network retry banner.

3. **Seamless Cellular Handover**:
   - Maintain continuous streaming without aborting playback when moving between Wi-Fi and mobile data networks.
   - Transparently race network addresses and preserve playback buffers so handovers do not disrupt the acoustic experience.

4. **Background Offline Server Reachability Self-Healing**:
   - Upon network restoration, the session asynchronously triggers playlist reachability self-healing.
   - The playlist repository identifies unique servers associated with tracks currently flagged as `SERVER_UNREACHABLE` and dispatches lightweight connection probes (`PROPFIND Depth: 0`).
   - Servers that respond successfully have their associated playlist tracks batch-restored to active status, automatically brightening dimmed tracks without user intervention.

## User Stories

1. As a listener entering an elevator where Wi-Fi drops, I want playback to pause cleanly, and when I exit the elevator and 5G reconnects, I want playback to automatically resume from my last position without touching my phone.
2. As a listener commuting on a subway that passes through tunnels, I want music to pause when signal is lost and automatically start playing again once connectivity returns, so that my listening experience remains uninterrupted.
3. As a listener who manually tapped "Pause" before entering a meeting, I want music NOT to resume automatically when my phone connects to office Wi-Fi, so that my phone never plays music out loud unexpectedly.
4. As a listener wearing Bluetooth earbuds that disconnected (triggering safe pause), I want reconnecting to Wi-Fi NOT to resume playback through the phone speakers, so that my acoustic privacy is strictly respected.
5. As a listener whose playback auto-resumed after a tunnel, I want the "Weak Network Retry" banner on the player sheet to automatically disappear, so that the user interface stays clean.
6. As a listener with cross-server playlists whose home NAS was unreachable while away from home, I want returning home and connecting to Wi-Fi to automatically restore dimmed tracks to normal, so that I can play them without manual refreshes.
7. As a listener on a train switching rapidly between Wi-Fi and cellular towers, I want network state debouncing to prevent rapid-fire play/pause churn, so that playback remains stable.
8. As a listener walking out of my home where Wi-Fi fades out and cellular data takes over, I want streaming to seamlessly transition across networks without stopping music, so that my audio playback is continuous.
9. As a listener whose playback was paused due to network loss for 10 minutes, I want the player's 15-minute foreground service grace period to keep the notification alive so that auto-resumption works seamlessly from the background.
10. As a listener whose server is still truly offline after network reconnection, I want auto-resumption to fail gracefully back to the retry banner after its bounded retries, so that the app does not enter an infinite reconnect loop.
11. As a user checking playlist details after reconnecting, I want to see previously dimmed tracks automatically re-enabled with full metadata, so that I know my storage servers are accessible again.
12. As a listener on a metered mobile network, I want network state indicators to accurately reflect connection status, so that data usage visibility is maintained.
13. As an audiophile streaming lossless FLAC over remote WebDAV, I want auto-resumption after network recovery to maintain bit-perfect decoding from the exact paused timestamp, so that audio quality is uncompromised.
14. As a listener playing CUE virtual tracks during network loss, I want network auto-resumption to calculate and restore the exact relative track position, so that virtual track progress is fully preserved.

## Implementation Decisions

- **Domain Model & Interface Seams**:
  - Define `data class NetworkStatus(val isConnected: Boolean, val isWifi: Boolean = false, val isCellular: Boolean = false, val isMetered: Boolean = false)` in the domain model.
  - Define domain interface `NetworkConnectivityObserver` exposing `val networkStatus: StateFlow<NetworkStatus>`.
  - Provide a test stand-in (`FakeNetworkConnectivityObserver`) for deterministic integration and unit testing.

- **Platform Network Callback Implementation**:
  - Implement `DefaultNetworkConnectivityObserver` utilizing Android's `ConnectivityManager.registerDefaultNetworkCallback` (supported natively on Min SDK 29+).
  - Register callbacks querying `NetworkCapabilities.NET_CAPABILITY_INTERNET`, `NetworkCapabilities.NET_CAPABILITY_VALIDATED`, and transport flags (`TRANSPORT_WIFI`, `TRANSPORT_CELLULAR`).
  - Incorporate a 500ms debounce in coroutine flow mapping to filter out transitional network state flips.
  - Life-cycle manage the observer as an application-scoped singleton hosted in `AppContainer`.

- **Auto-Resumption Orchestration in `PlaybackFaultTolerance`**:
  - Extend `PlaybackFaultTolerance` with `fun onNetworkRestored(onAutoResume: () -> Unit)`.
  - Check the resumption condition: `sessionState.isWeakNetwork && sessionState.canManualRetry && sessionState.hasTrack`.
  - If true, invoke `onAutoResume`, reset fault flags (`isWeakNetwork = false`, `canManualRetry = false`, `isReconnecting = false`), and command the audio engine to resume playback.
  - If false (such as when paused by user tap or `BecomingNoisyReceiver`), take no action.

- **Offline Playlist Reachability Self-Healing in `PlaylistRepository`**:
  - Extend `PlaylistRepository` with `suspend fun triggerReachabilitySelfHealing()`.
  - In `PlaylistRepositoryImpl`, query `playlistDao` for distinct server IDs of tracks where `isInactive = true` and `inactiveReason = InactiveReason.SERVER_UNREACHABLE`.
  - Concurrently execute `webDavClient.testConnection(server)` for each unreachable server.
  - For servers that return success, invoke `playlistDao.clearServerUnreachableStatus(serverId)` to batch-restore affected tracks back to active.

## Testing Decisions

- **Test Public Interfaces and Observable State**:
  - Tests will not inspect internal broadcast receivers or private callbacks; tests will feed deterministic `NetworkStatus` transitions and assert on emitted `PlayerSessionState` and repository states.
  - **NetworkConnectivityObserver Unit Tests**:
    - Test `DefaultNetworkConnectivityObserver` with Robolectric or mock `ConnectivityManager`.
    - Verify that transitions between connected, Wi-Fi, cellular, and disconnected correctly map to `NetworkStatus` objects.
  - **Auto-Resumption State Machine Tests**:
    - In `MusicPlayerAppSessionTest` and `PlaybackFaultToleranceTest`:
      - Simulate a stream entering weak-network suspended pause (`isWeakNetwork = true`, `canManualRetry = true`). Emit `networkStatus(isConnected = true)` and assert that playback automatically resumes and flags are cleared.
      - Simulate a user-initiated pause (`isWeakNetwork = false`). Emit `networkStatus(isConnected = true)` and assert that playback remains strictly paused.
      - Simulate an audio becoming noisy pause. Emit `networkStatus(isConnected = true)` and assert that playback remains strictly paused.
  - **Playlist Reachability Recovery Tests**:
    - In `PlaylistRepositoryTest`, seed tracks marked inactive with `SERVER_UNREACHABLE`.
    - Mock `WebDavClient` to return success on `testConnection`.
    - Invoke `triggerReachabilitySelfHealing()` and assert that all matching tracks transition back to active (`isInactive = false`, `inactiveReason = null`).
  - **Full Pipeline Regression**:
    - Run the entire test suite (`./gradlew testDebugUnitTest`) to verify zero regressions.
    - Run `./gradlew verifyReleasePackaging` to ensure binary budget compliance.

## Out of Scope

- Metered network hard download quotas or cellular data limit dialogues.
- P2P Wi-Fi Direct or local Bluetooth offline transfer.
- Network speed benchmarking or bitrate throttling.

## Further Notes

- The 500ms debounce buffer is critical for mobile radio dormancy transitions, ensuring that temporary cellular state handshakes do not fire duplicate resumption events.
