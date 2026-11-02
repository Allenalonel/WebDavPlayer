# 01: System Network Connectivity Reactive Observer with Debounce

**What to build:** An application-wide reactive network observation subsystem utilizing Android's native `ConnectivityManager.NetworkCallback`. It monitors real-time physical network capability transitions (`NET_CAPABILITY_INTERNET`, `NET_CAPABILITY_VALIDATED`) and transport types (`TRANSPORT_WIFI`, `TRANSPORT_CELLULAR`), exposing an immutable domain `StateFlow<NetworkStatus>` stream. A built-in 500ms debouncing window ensures transient signal flickering (e.g. entering elevators or switching cell towers) is filtered out before propagating to higher-level domain listeners.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `NetworkStatus` immutable domain model encapsulates connection state, Wi-Fi, cellular, and metered attributes
- [ ] `NetworkConnectivityObserver` interface defined in the domain layer
- [ ] `DefaultNetworkConnectivityObserver` registers native `ConnectivityManager.NetworkCallback` with proper cleanup
- [ ] 500ms coroutine debounce filters rapid transitional network state flapping
- [ ] `AppContainer` and `WebDavApplication` manage singleton observer lifecycle
- [ ] `FakeNetworkConnectivityObserver` test stand-in provided for deterministic testing
- [ ] Unit tests in `NetworkConnectivityObserverTest` verify transitions across Wi-Fi, cellular, disconnected, and debounced states
