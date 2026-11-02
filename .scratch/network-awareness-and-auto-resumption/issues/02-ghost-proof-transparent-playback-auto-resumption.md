# 02: Ghost-Proof Transparent Playback Auto-Resumption

**What to build:** Automatic playback recovery when internet connectivity is restored after a network outage, with strict guards against accidental audio blaring (ghost resumption). When network state transitions from disconnected to connected, the session orchestrator checks if playback was suspended due to a network fault (`isWeakNetwork == true && canManualRetry == true`). If so, it automatically retries playback from the exact millisecond offset, reconnects the streaming data source, and dismisses the retry banner. If playback was paused by explicit user action or headphone disconnection, it strictly remains paused.

**Blocked by:** 01 (System Network Connectivity Reactive Observer with Debounce)

**Status:** ready-for-agent

- [ ] `PlaybackFaultTolerance` incorporates `onNetworkRestored(onAutoResume: () -> Unit)`
- [ ] Auto-resumption triggers only when strict preconditions are met (`isWeakNetwork && canManualRetry && hasTrack`)
- [ ] Successful auto-resumption clears `isWeakNetwork`, `canManualRetry`, and `isReconnecting`, smoothly dismissing UI retry banners
- [ ] User-initiated pauses and Becoming Noisy headphone disconnection pauses strictly remain paused upon network return
- [ ] Unit tests in `PlaybackFaultToleranceTest` and `MusicPlayerAppSessionTest` assert positive auto-resumption and ghost resumption suppression
