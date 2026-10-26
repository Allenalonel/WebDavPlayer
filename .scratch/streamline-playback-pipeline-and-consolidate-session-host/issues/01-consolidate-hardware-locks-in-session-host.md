# 01: Consolidate Hardware Locks in Session Host

**What to build:** The user enjoys uninterrupted background and lock-screen streaming without dual lock contention or battery drain. `StreamingHardwareLockManager` (CPU WakeLock and Wi-Fi Lock) is exclusively held and managed by `PlaybackSessionHost` across all playback state transitions, while `Media3AudioPlayerEngine` and `DefaultWebDavMediaSourceAdapter` are relieved of duplicate lock monitoring and unused lock constructor parameters.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] All lock management logic (`hardwareLockManager.acquireLocks()`, `releaseLocks()`, `release()`) is removed from `Media3AudioPlayerEngine`.
- [x] `DefaultWebDavMediaSourceAdapter` constructor and properties remove the unused `hardwareLockManager` parameter.
- [x] `PlaybackSessionHost` reliably acquires wake and Wi-Fi locks during `Playing` and active `Buffering`, and releases them on `Paused`, `Error`, `Idle`, `Ended`, and service detachment.
- [x] `PlaybackSessionHost` constructor initializes its own default `StreamingHardwareLockManager` without downcasting `(playerEngine as? Media3AudioPlayerEngine)?.hardwareLockManager`.
- [x] Existing unit and regression tests in `PlaybackSessionHostTest` and `Media3AudioPlayerEngineTest` are updated to reflect the single-owner lock contract and pass with 100% success.

## Comments

### Resolution
- Consolidated `StreamingHardwareLockManager` ownership and lifecycle management exclusively inside `PlaybackSessionHost`, initializing `DefaultStreamingHardwareLockManager(context)` directly across constructors without downcasting `playerEngine`.
- Configured `PlaybackSessionHost` to acquire wake and Wi-Fi locks on `Playing` and active `Buffering` (`playWhenReady == true`) and release them on inactive `Buffering`, `Paused`, `Error`, `Idle`, `Ended`, `detachService`, `stop`, and `release`.
- Removed `hardwareLockManager` constructor parameter and all lock acquisition/release calls from `Media3AudioPlayerEngine` (`DefaultWebDavMediaSourceAdapter` is also free of any `hardwareLockManager` parameter).
- Updated `PlaybackSessionHostTest`, `Media3AudioPlayerEngineTest`, and `EndToEndSystemFocusAndRouteRegressionTest` to verify the single-owner lock contract at the `PlaybackSessionHost` seam.
