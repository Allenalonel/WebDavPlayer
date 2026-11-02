# 01: System Network Connectivity Reactive Observer with Debounce

**What to build:** An application-wide reactive network observation subsystem utilizing Android's native `ConnectivityManager.NetworkCallback`. It monitors real-time physical network capability transitions (`NET_CAPABILITY_INTERNET`, `NET_CAPABILITY_VALIDATED`) and transport types (`TRANSPORT_WIFI`, `TRANSPORT_CELLULAR`), exposing an immutable domain `StateFlow<NetworkStatus>` stream. A built-in 500ms debouncing window ensures transient signal flickering (e.g. entering elevators or switching cell towers) is filtered out before propagating to higher-level domain listeners.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] `NetworkStatus` immutable domain model encapsulates connection state, Wi-Fi, cellular, and metered attributes
- [x] `NetworkConnectivityObserver` interface defined in the domain layer
- [x] `DefaultNetworkConnectivityObserver` registers native `ConnectivityManager.NetworkCallback` with proper cleanup
- [x] 500ms coroutine debounce filters rapid transitional network state flapping
- [x] `AppContainer` and `WebDavApplication` manage singleton observer lifecycle
- [x] `FakeNetworkConnectivityObserver` test stand-in provided for deterministic testing
- [x] Unit tests in `NetworkConnectivityObserverTest` verify transitions across Wi-Fi, cellular, disconnected, and debounced states

## Comments
- Implemented `NetworkStatus` immutable model and `NetworkConnectivityObserver` interface in domain.
- Implemented `DefaultNetworkConnectivityObserver` with `ConnectivityManager.NetworkCallback`, safe unregistering, and 500ms debounce window.
- Integrated singleton lifecycle into `AppContainer` and `WebDavApplication`.
- Implemented `FakeNetworkConnectivityObserver` and exhaustive tests in `NetworkConnectivityObserverTest`. All 9 tests passed.
