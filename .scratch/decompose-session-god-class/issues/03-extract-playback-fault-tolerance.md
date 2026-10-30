# 03: Extract PlaybackFaultTolerance

**What to build:** Extract the stream buffering watchdog (15-second weak network flag and 30-second pause), Premature EOF guard, HTTP 404/auth failure categorization, server unavailable handling, and unsupported Range replay dialogue state out of `MusicPlayerAppSessionImpl` into an internal `PlaybackFaultTolerance` collaborator. Fault handling becomes isolated, robust, and unit-tested directly.

**Blocked by:** 02: Extract PlaybackQueueManager

**Status:** resolved

- [x] Create internal `PlaybackFaultTolerance` in `domain.session` package
- [x] Move buffering watchdog job scheduling, weak network state transition, and pause timeouts into the fault tolerance collaborator
- [x] Move HTTP 404 error detection, authentication failure checks, and server unreachable classification into the collaborator
- [x] Move unsupported HTTP Range replay dialogue confirmations (`confirmUnsupportedRangeReplay`, `dismissUnsupportedRange`) into the collaborator
- [x] Add dedicated `PlaybackFaultToleranceTest` covering watchdog delays, error classification, and Range replay states
- [x] Update `MusicPlayerAppSessionImpl` to delegate error handling and watchdog monitoring to `PlaybackFaultTolerance`
- [x] Verify full test suite passes cleanly

## Answer

Extracted `PlaybackFaultTolerance` as an internal collaborator in `domain.session`:
1. Encapsulated stream buffering watchdog coroutine lifecycle (15-second weak network transition and 30-second pause with manual retry authorization), healthy network timing, and active playback watchdog clearing.
2. Encapsulated HTTP 404 file-missing detection, HTTP 401/403 credential failure detection, network connection exception classification (unreachable servers), and unsupported HTTP Range exception recognition.
3. Encapsulated failure progression: inactive reason playlist marking (`FILE_NOT_FOUND`, `SERVER_CREDENTIAL_INVALID`, `SERVER_UNREACHABLE`), delegating to `PlaybackQueueManager.recordFailedTrack` for skipping without infinite loops.
4. Encapsulated unsupported HTTP Range replay confirmations and dismissals, as well as premature EOF arbitration (5000ms physical tolerance and 95% progress threshold).
5. Created `PlaybackFaultToleranceTest` with 7 dedicated unit test cases covering buffering watchdog delays, playing resets, comprehensive error classification, 404 skipping and cycle exhaustion, unsupported Range dialog actions, and premature EOF arbitration.
6. Refactored `MusicPlayerAppSessionImpl` to delegate all fault handling, watchdog monitoring, and Range confirmations to `PlaybackFaultTolerance`, keeping test suites 100% green.
