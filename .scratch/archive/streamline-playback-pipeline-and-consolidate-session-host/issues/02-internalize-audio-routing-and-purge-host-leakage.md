# 02: Internalize Audio Routing and Purge Host Leakage

**What to build:** When the user unplugs headphones, reconnects Bluetooth audio, or switches output sinks via the Media Output Switcher, `Media3AudioPlayerEngine` detects device routing shifts and flushes/resynchronizes the AudioSink buffers internally. `PlaybackSessionHost` completely removes the test-only `audioRoutingObserver` and `attachAudioRouting` downcast accessors, eliminating engine downcasts for audio routing.

**Blocked by:** 01: Consolidate Hardware Locks in Session Host

**Status:** resolved

- [x] `Media3AudioPlayerEngine` handles `AudioRoutingObserver` events and audio pipeline flush (`flushAudioPipelines()`) completely internally.
- [x] `PlaybackSessionHost` removes `val audioRoutingObserver`, `fun onAudioRoutingChanged()`, and `@VisibleForTesting fun attachAudioRouting()` downcasts.
- [x] Unit and regression tests for audio routing observation, device connection/disconnection, and sink flush target `Media3AudioPlayerEngine` directly.
- [x] All audio routing and sink synchronization tests in `AudioRoutingObserverTest` and `EndToEndSystemFocusAndRouteRegressionTest` pass cleanly.

## Comments

### Resolution
- Internalized `onAudioRoutingChanged()` and `flushAudioPipelines()` within `Media3AudioPlayerEngine`, removing `fun onAudioRoutingChanged()` from `AudioPlayerEngine`, `PlaybackSessionHost`, and `FakeAudioPlayerEngine`.
- Verified `PlaybackSessionHost` and `AudioPlayerEngine` expose zero audio routing leakage (`audioRoutingObserver`, `onAudioRoutingChanged`, `attachAudioRouting`, `detachAudioRouting`).
- Added direct unit test coverage on `Media3AudioPlayerEngine` for physical device connection/disconnection (`onAudioDevicesAdded` / `onAudioDevicesRemoved`) and dynamic `AudioRouting` changes flushing `activeAudioSink` internally.
- Updated `EndToEndSystemFocusAndRouteRegressionTest` and `PlaybackSessionHostTest` to target `Media3AudioPlayerEngine` directly for routing resynchronization and sink flush.
