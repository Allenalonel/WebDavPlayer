# 02: Ghost-Proof Transparent Playback Auto-Resumption

**What to build:** Automatic playback recovery when internet connectivity is restored after a network outage, with strict guards against accidental audio blaring (ghost resumption). When network state transitions from disconnected to connected, the session orchestrator checks if playback was suspended due to a network fault (`isWeakNetwork == true && canManualRetry == true`). If so, it automatically retries playback from the exact millisecond offset, reconnects the streaming data source, and dismisses the retry banner. If playback was paused by explicit user action or headphone disconnection, it strictly remains paused.

**Blocked by:** 01 (System Network Connectivity Reactive Observer with Debounce)

**Status:** resolved

- [x] `PlaybackFaultTolerance` incorporates `onNetworkRestored(onAutoResume: () -> Unit)`
- [x] Auto-resumption triggers only when strict preconditions are met (`isWeakNetwork && canManualRetry && hasTrack`)
- [x] Successful auto-resumption clears `isWeakNetwork`, `canManualRetry`, and `isReconnecting`, smoothly dismissing UI retry banners
- [x] User-initiated pauses and Becoming Noisy headphone disconnection pauses strictly remain paused upon network return
- [x] Unit tests in `PlaybackFaultToleranceTest` and `MusicPlayerAppSessionTest` assert positive auto-resumption and ghost resumption suppression

## Comments
- Implemented `onNetworkRestored(onAutoResume: () -> Unit)` in `PlaybackFaultTolerance` guarded by `isWeakNetwork && canManualRetry && hasTrack`.
- Bound `NetworkConnectivityObserver` in `MusicPlayerAppSessionImpl` with `drop(1)` transition filtering to trigger auto-resumption from exact playback position.
- Added explicit state clearance in `pauseInternal()` guaranteeing zero ghost resumption on user pauses and Becoming Noisy events.
- Unit tests added in `PlaybackFaultToleranceTest` (4 new tests) and `MusicPlayerAppSessionTest` (3 new tests), all passing.
