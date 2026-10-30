# 03: Extract PlaybackFaultTolerance

**What to build:** Extract the stream buffering watchdog (15-second weak network flag and 30-second pause), Premature EOF guard, HTTP 404/auth failure categorization, server unavailable handling, and unsupported Range replay dialogue state out of `MusicPlayerAppSessionImpl` into an internal `PlaybackFaultTolerance` collaborator. Fault handling becomes isolated, robust, and unit-tested directly.

**Blocked by:** 02: Extract PlaybackQueueManager

**Status:** ready-for-agent

- [ ] Create internal `PlaybackFaultTolerance` in `domain.session` package
- [ ] Move buffering watchdog job scheduling, weak network state transition, and pause timeouts into the fault tolerance collaborator
- [ ] Move HTTP 404 error detection, authentication failure checks, and server unreachable classification into the collaborator
- [ ] Move unsupported HTTP Range replay dialogue confirmations (`confirmUnsupportedRangeReplay`, `dismissUnsupportedRange`) into the collaborator
- [ ] Add dedicated `PlaybackFaultToleranceTest` covering watchdog delays, error classification, and Range replay states
- [ ] Update `MusicPlayerAppSessionImpl` to delegate error handling and watchdog monitoring to `PlaybackFaultTolerance`
- [ ] Verify full test suite passes cleanly
