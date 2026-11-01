# 01: Extract SessionPersistenceCoordinator

**What to build:** Extract the session serialization, cold restore, periodic flushing, and directory memory responsibilities out of `MusicPlayerAppSessionImpl` into an internal `SessionPersistenceCoordinator` collaborator. Session restoration (both ordinary tracks and CUE virtual tracks) and flush-to-disk logic will be cleanly encapsulated and verified by dedicated unit tests, while `MusicPlayerAppSessionImpl` delegates all persistence calls to this coordinator.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] Create internal `SessionPersistenceCoordinator` in `domain.session` package
- [x] Move `restoreSession`, `restoreVirtualTrackSession`, `restoreOrdinarySession`, and directory path tracking into the coordinator
- [x] Move `flushSession`, `flushSessionWithCleanExit`, `startPeriodicFlush`, `stopPeriodicFlush`, and `flushSessionAsync` into the coordinator
- [x] Add comprehensive `SessionPersistenceCoordinatorTest` covering cold restore, CUE virtual track reconstruction, and periodic flushing
- [x] Update `MusicPlayerAppSessionImpl` to delegate persistence operations to the coordinator
- [x] Ensure all existing `MusicPlayerAppSessionTest` tests remain 100% green

## Answer

Extracted `SessionPersistenceCoordinator` as an internal collaborator in `domain.session`:
1. Encapsulated `PlaybackSessionStore` persistence, cold-restore state reconstruction (both for ordinary audio queues and CUE virtual track sheet timelines), and per-server last visited directory mappings (`serverLastDirectories`).
2. Implemented active-state periodic flushing (`startPeriodicFlush` / `stopPeriodicFlush`) on a 1-second cadence when the underlying engine is playing.
3. Created `SessionPersistenceCoordinatorTest` with 7 comprehensive unit test cases covering ordinary restore, cached metadata enrichment, deleted server resilience, CUE virtual timeline reconstruction, session snapshot serialization, periodic flush throttling, and directory memory.
4. Refactored `MusicPlayerAppSessionImpl` to delegate all persistence and directory tracking operations to `SessionPersistenceCoordinator`, reducing `MusicPlayerAppSessionImpl.kt` from 2,149 to 1,860 lines while preserving 100% green test passes across all suites.
