# Specification: Decompose Session God Class into Focused Internal Seams

## Problem Statement

As WebDavPlayer grew through multiple iterative feature additions (CUE sheet virtual tracks, cross-server playlists, resilient battery optimization banners, gapless pre-roll buffering, and system intent routing), the core domain session implementation grew unchecked into a 2,149-line God class.

While ADR-0018 successfully consolidated external intent routing into a unified channel, the absence of internal seams within the session layer caused the implementation to absorb nine orthogonal concerns into a single file. Understanding, debugging, and modifying session behavior requires navigating thousands of lines of intertwined logic spanning DataStore persistence, queue progression, shuffle algorithms, watchdog timers, and HTTP error recovery.

## Solution

Decompose the monolithic playback session implementation behind its existing, stable domain interface into three package-private (`internal`) collaborators:
1. A session persistence coordinator handling state serialization, periodic flushing, cold restoration, and CUE session reconstruction.
2. A playback queue manager handling queue state machine transitions, index calculations, list/single loop rules, shuffle order generation, ad-hoc insertions, and inactive track skip cycles.
3. A playback fault tolerance guard handling buffering watchdog delays, EOF detection, 404/auth failure recovery, and unsupported range replay arbitration.

The primary session implementation retains its role as a lean orchestrator (< 700 lines) delegating to these collaborators while keeping the external intent interface 100% unchanged.

## User Stories

1. As a music listener, I want my playback session (active server, current track, progress, and queue) to restore seamlessly after a cold start or system kill, so that I can resume listening without interruption.
2. As a music listener, I want CUE virtual track playback sessions to accurately reconstruct their relative timeline offsets upon cold restoration, so that playing an individual track within a disc image resumes at the exact second.
3. As a music listener, I want list loop, single loop, and shuffle modes to advance tracks predictably according to my chosen mode, so that queue progression behaves naturally.
4. As a music listener, I want ad-hoc insertions (Play Next and Play Next and Advance) to insert tracks after the active song without wiping my existing queue or disrupting the overarching directory/playlist structure, so that queue flexibility is preserved.
5. As a music listener, I want inactive tracks or tracks that return HTTP 404/auth errors during playback to be smoothly and silently skipped without infinite loop lockups, so that my listening experience does not crash or freeze.
6. As a music listener experiencing network congestion, I want playback to indicate weak network after 15 seconds of buffering and automatically pause after 30 seconds, so that battery and mobile data are protected during connection outages.
7. As an Android developer and maintainer, I want the playback queue transitions and shuffle logic to be isolated in a pure Kotlin state machine, so that queue algorithms can be tested rapidly and deterministically without mocking Android media engines or DataStore.
8. As an Android developer and maintainer, I want session serialization and cold restore logic to be isolated in a dedicated persistence coordinator, so that storage schema migrations and session persistence rules can evolve without touching audio playback code.
9. As an Android developer and maintainer, I want external callers (Compose UI, media notification actions, Bluetooth media button callbacks, and Android foreground service lifecycle) to experience zero API changes, so that existing integration contracts remain 100% stable.

## Implementation Decisions

1. **Preserve External Seam**: The public domain interface `MusicPlayerAppSession` remains completely unchanged. All public methods, properties, StateFlow emissions, and intent contracts are preserved verbatim.
2. **Package-Private Collaborators**: Three new collaborators are created inside the `domain.session` package with `internal` visibility:
   - `SessionPersistenceCoordinator`: Encapsulates DataStore operations, periodic flushing coroutines, last visited directory memory, and cold session reconstruction.
   - `PlaybackQueueManager`: Encapsulates immutable queue transitions, next/previous index calculations, list loop/single loop/shuffle ordering, ad-hoc insertions, track removal, and failed-track cycle guardrails.
   - `PlaybackFaultTolerance`: Encapsulates buffering watchdog coroutine jobs, weak network state tracking, HTTP 404/credential failure categorization, and unsupported HTTP Range dialogue confirmations.
3. **Lean Orchestrator Composition**: The session implementation maintains references to the three internal collaborators alongside the underlying player engine and virtual timeline engine, delegating specific domain tasks and combining their outputs into the unified `PlayerSessionState` and `PlaybackProgress` flows.
4. **Step-by-Step Expand-Contract Migration**: Refactoring proceeds in discrete vertical slices:
   - Slice 1: Extract and integrate `SessionPersistenceCoordinator` with dedicated unit tests.
   - Slice 2: Extract and integrate `PlaybackQueueManager` with dedicated unit tests.
   - Slice 3: Extract and integrate `PlaybackFaultTolerance` with dedicated unit tests.
   - Slice 4: Full integration verification and file budget compliance (< 700 lines).

## Testing Decisions

- **Full Regression Safety**: All existing 3,000+ lines in `MusicPlayerAppSessionTest.kt` must continue to pass cleanly without any regression, serving as the end-to-end acceptance net.
- **Dedicated Internal Seam Tests**: New targeted unit tests will be introduced for each internal collaborator:
   - `SessionPersistenceCoordinatorTest`: Verifies normal session save/restore, CUE virtual track reconstruction, directory path memory, and clean exit serialization.
   - `PlaybackQueueManagerTest`: Verifies index calculation under all playback modes, shuffle permutation stability, ad-hoc queue insertions, and inactive skip cycle prevention.
   - `PlaybackFaultToleranceTest`: Verifies 15-second weak network state triggering, 30-second watchdog pause dispatch, and 404 error auto-skip flags.

## Out of Scope

- Changing the underlying Media3 ExoPlayer engine or audio rendering pipeline (governed by ADR-0016 and ADR-0019).
- Modifying UI components (MiniPlayer, FullPlayerSheet, PlaylistDetailScreen).
- Modifying `DirectoryRepository` or `LibrarySearchRepository` (reserved for subsequent refactoring candidates).

## Further Notes

- Governed by ADR-0018 (Intent Routing) and ADR-0023 (Internal Seams and Session Decomposition).
- Strict adherence to the 600-line cohesion budget established in the architectural guidelines.
