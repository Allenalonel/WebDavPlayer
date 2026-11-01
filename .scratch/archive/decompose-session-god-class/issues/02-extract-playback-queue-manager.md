# 02: Extract PlaybackQueueManager

**What to build:** Extract the playback queue state transitions, index calculations, playback mode cycling, shuffle order generation, ad-hoc insertions (Play Next / Play Next and Advance), and dead-track cycle prevention out of `MusicPlayerAppSessionImpl` into a pure, internal `PlaybackQueueManager` collaborator. All queue progression logic becomes independently testable without player engines or persistence stores.

**Blocked by:** 01: Extract SessionPersistenceCoordinator

**Status:** resolved

- [x] Create internal `PlaybackQueueManager` in `domain.session` package
- [x] Move `calculateNextQueueIndex`, `calculatePreviousQueueIndex`, and shuffle order generation into the queue manager
- [x] Move ad-hoc queue insertion (`playNext`, `playNextAndAdvance`), queue track removal (`removeQueueTrack`), and `setPlaybackMode` / `cyclePlaybackMode` into the queue manager
- [x] Move `failedTrackIdsInCurrentCycle` and inactive track skipping protection into the queue manager
- [x] Add dedicated `PlaybackQueueManagerTest` covering all playback modes (list loop, single loop, shuffle), ad-hoc insertions, and skip cycles
- [x] Update `MusicPlayerAppSessionImpl` to delegate queue management to `PlaybackQueueManager`
- [x] Verify full test suite passes cleanly

## Answer

Extracted `PlaybackQueueManager` as an internal collaborator in `domain.session`:
1. Encapsulated `PlaybackQueue` state transitions, next/previous index calculations under all playback modes (`LIST_LOOP`, `SINGLE_LOOP`, `SHUFFLE`), lazy shuffle order generation, and ad-hoc insertions (`playNext`, `playNextAndAdvance`) and removals (`removeQueueTrack`).
2. Encapsulated `failedTrackIdsInCurrentCycle` and dead-track cycle prevention: when tracks fail, SINGLE_LOOP automatically advances to prevent infinite spin, and when all queue tracks have failed, it safely halts skipping.
3. Created `PlaybackQueueManagerTest` with 11 targeted unit tests verifying queue index math, SINGLE_LOOP error advances, cycle exhaustion protection, shuffle ordering, ad-hoc insertions, and track removals without player engine dependencies.
4. Refactored `MusicPlayerAppSessionImpl` to delegate all queue manipulations to `PlaybackQueueManager`, keeping test suites 100% green.
