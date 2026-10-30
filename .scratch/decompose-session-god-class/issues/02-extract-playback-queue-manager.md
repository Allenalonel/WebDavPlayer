# 02: Extract PlaybackQueueManager

**What to build:** Extract the playback queue state transitions, index calculations, playback mode cycling, shuffle order generation, ad-hoc insertions (Play Next / Play Next and Advance), and dead-track cycle prevention out of `MusicPlayerAppSessionImpl` into a pure, internal `PlaybackQueueManager` collaborator. All queue progression logic becomes independently testable without player engines or persistence stores.

**Blocked by:** 01: Extract SessionPersistenceCoordinator

**Status:** ready-for-agent

- [ ] Create internal `PlaybackQueueManager` in `domain.session` package
- [ ] Move `calculateNextQueueIndex`, `calculatePreviousQueueIndex`, and shuffle order generation into the queue manager
- [ ] Move ad-hoc queue insertion (`playNext`, `playNextAndAdvance`), queue track removal (`removeQueueTrack`), and `setPlaybackMode` / `cyclePlaybackMode` into the queue manager
- [ ] Move `failedTrackIdsInCurrentCycle` and inactive track skipping protection into the queue manager
- [ ] Add dedicated `PlaybackQueueManagerTest` covering all playback modes (list loop, single loop, shuffle), ad-hoc insertions, and skip cycles
- [ ] Update `MusicPlayerAppSessionImpl` to delegate queue management to `PlaybackQueueManager`
- [ ] Verify full test suite passes cleanly
