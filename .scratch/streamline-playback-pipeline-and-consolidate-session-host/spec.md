# Spec: Streamline Playback Pipeline and Consolidate Session Host

Status: completed

## Problem Statement

When streaming WebDAV audio files, playback stability in background and lock-screen scenarios relies on delicate coordination among the foreground service, system media session, hardware wake locks, and the underlying ExoPlayer engine. 

In the current implementation, `PlaybackSessionHost` acts as an `AudioPlayerEngine` adapter, but repeatedly breaches its own interface seam via downcasts (`as? Media3AudioPlayerEngine`) to access internals like hardware locks, routing observers, and skip callbacks. Furthermore, dual streaming hardware locks (CPU WakeLock and Wi-Fi Lock) are monitored and arbitrated redundantly in both the host and the player engine, and `MediaSession` ownership is located inside the player engine despite the host managing service and notification dispatch. This coupling produces state races, testing fragility, and maintenance friction.

## Solution

Consolidate all system-facing hosting responsibilities—including `MediaSession` ownership, foreground service elevation, deferred demotion, and `Dual Streaming Hardware Locks`—exclusively within `PlaybackSessionHost`. 

Strip all downcast breaches from `PlaybackSessionHost`, remove reverse callbacks and redundant lock handling from `Media3AudioPlayerEngine`, and internalize dynamic `Audio Routing Observer` routines within the engine. `AudioPlayerEngine` becomes a pure, unidirectional `Streaming Playback Pipeline` interface.

## User Stories

1. As a listener streaming remote WebDAV music with my screen locked, I want CPU and Wi-Fi hardware locks acquired seamlessly during buffering and playback, so that streaming never stutters due to device sleep or network throttling.
2. As a listener pausing remote playback, I want CPU and Wi-Fi hardware locks released immediately by the session host, so that my device saves battery while remaining protected under the 15-minute deferred demotion grace period.
3. As a listener resuming playback from a paused notification, I want hardware locks re-acquired reliably without state contention between the host and player engine.
4. As a user controlling playback via Bluetooth headset buttons, lock screen controls, or system notifications, I want `MediaSession` commands routed directly through the unified session host without reverse callback gymnastics.
5. As an audiophile switching audio outputs (e.g., unplugging wired headphones or disconnecting Bluetooth speakers), I want the player engine to automatically detect routing changes and flush audio buffers internally, so that playback never experiences silent phantom progress.
6. As a maintainer configuring media sources, I want `WebDavMediaSourceAdapter` to be free of unused hardware lock manager constructor arguments, so that media source creation remains focused on authentication and streaming data sources.
7. As a test author verifying foreground service behavior, I want to assert on hardware lock states and service elevation at the `PlaybackSessionHost` seam without the underlying engine simultaneously mutating the same locks.
8. As a test author verifying native playback and format resampling, I want to exercise `Media3AudioPlayerEngine` directly through the `AudioPlayerEngine` interface without instantiating or mocking system `MediaSession` or service callback wiring.
9. As an Android developer navigating the codebase, I want `PlaybackSessionHost` to contain zero downcasts to `Media3AudioPlayerEngine`, so that alternative player engine implementations can be substituted without runtime type errors.
10. As a listener experiencing transient network errors or playback completion, I want the session host to cleanly release locks and demote foreground status deterministically in one consolidated location.

## Implementation Decisions

1. **Consolidate Hardware Locks Exclusively in Session Host**:
   - `StreamingHardwareLockManager` is managed strictly by the session host in response to playback state changes (`Playing`, `Buffering`, `Paused`, `Error`, `Idle`, `Ended`).
   - All lock acquisition, release, and teardown logic is removed from `Media3AudioPlayerEngine`.
   - The unused `hardwareLockManager` parameter in `DefaultWebDavMediaSourceAdapter` is removed.

2. **Consolidate MediaSession and Session Player in Session Host**:
   - `MediaSession` and its `sessionPlayer` (`ForwardingPlayer`) are built and owned by `PlaybackSessionHost`, linking directly with `WebDavMediaSessionCallback`.
   - `Media3AudioPlayerEngine` no longer creates or exposes `mediaSession`, nor does it retain `sessionCallback`, `onSkipToNext`, or `onSkipToPrevious` variables.
   - Non-disruptive metadata updates from `updateTrackMetadata` flow cleanly through the engine to the active ExoPlayer item and are reflected in the host's `MediaSession`.

3. **Internalize Dynamic Audio Routing and Flush in Player Engine**:
   - `AudioRoutingObserver` and associated AudioSink flush mechanisms remain encapsulated as private internal capabilities of `Media3AudioPlayerEngine`.
   - The test-only `attachAudioRouting` and `audioRoutingObserver` properties on `PlaybackSessionHost` are deleted.
   - Routing resilience tests target `Media3AudioPlayerEngine` directly.

4. **Zero-Downcast Seam Guarantee**:
   - Every `(playerEngine as? Media3AudioPlayerEngine)` expression in `PlaybackSessionHost` is eliminated.
   - `PlaybackSessionHost` interacts with `AudioPlayerEngine` solely through its public interface.

## Testing Decisions

- **Host Testing Surface**: `PlaybackSessionHostTest` and `WebDavMediaServiceTest` verify foreground elevation, deferred demotion, hardware lock states, and media button intent dispatch through the host seam.
- **Engine Testing Surface**: `Media3AudioPlayerEngineTest` verifies audio decoding, canonical resampling, non-disruptive metadata updates, and internal routing change handling without coupling to host locks or services.
- **Integration Regressions**: `EndToEndSystemFocusAndRouteRegressionTest` and `EndToEndPlaybackPipelineIntegrationTest` verify end-to-end alignment across the unified pipeline.

## Out of Scope

- Modifying the public methods of `AudioPlayerEngine` (`loadTrack`, `prerollTrack`, `updateTrackMetadata`, etc.).
- Modifying CUE virtual track resolution or `VirtualTimelineEngine` (addressed under Candidate 2 & Candidate 3).
- Altering Android notification layouts or UI theme assets.

## Further Notes

- Aligns directly with ADR-0016, ADR-0017, ADR-0018, and ADR-0019.
