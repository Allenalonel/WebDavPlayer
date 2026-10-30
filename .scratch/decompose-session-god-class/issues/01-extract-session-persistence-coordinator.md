# 01: Extract SessionPersistenceCoordinator

**What to build:** Extract the session serialization, cold restore, periodic flushing, and directory memory responsibilities out of `MusicPlayerAppSessionImpl` into an internal `SessionPersistenceCoordinator` collaborator. Session restoration (both ordinary tracks and CUE virtual tracks) and flush-to-disk logic will be cleanly encapsulated and verified by dedicated unit tests, while `MusicPlayerAppSessionImpl` delegates all persistence calls to this coordinator.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Create internal `SessionPersistenceCoordinator` in `domain.session` package
- [ ] Move `restoreSession`, `restoreVirtualTrackSession`, `restoreOrdinarySession`, and directory path tracking into the coordinator
- [ ] Move `flushSession`, `flushSessionWithCleanExit`, `startPeriodicFlush`, `stopPeriodicFlush`, and `flushSessionAsync` into the coordinator
- [ ] Add comprehensive `SessionPersistenceCoordinatorTest` covering cold restore, CUE virtual track reconstruction, and periodic flushing
- [ ] Update `MusicPlayerAppSessionImpl` to delegate persistence operations to the coordinator
- [ ] Ensure all existing `MusicPlayerAppSessionTest` tests remain 100% green
