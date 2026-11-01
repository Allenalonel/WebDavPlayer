# 04: End-to-End Playback Pipeline and Performance Verification

**What to build:** Comprehensive end-to-end integration and performance verification across the deepened WebDAV media source adapter, two-track reactive session state, and unified playback session host. This slice validates the complete lifecycle of browsing, queuing, streaming over fluctuating network connections, background playback persistence, and cold-start resumption. It ensures zero regressions across all existing test suites, verifies that main-thread Looper frame drops are eliminated, and confirms strict adherence to ADR-0002 (ephemeral in-memory streaming buffer with zero persistent local audio disk storage).

**Blocked by:** 01: Deep WebDAV MediaSource Adapter and Resilient Streaming, 02: Two-Track Reactive Playback Session State Optimization, 03: Unified Playback Session Host and Resilient Background Service

**Status:** completed

## Acceptance criteria

- [x] End-to-end integration test suite executes the complete user journey: connecting to a WebDAV server, loading directory contents, enqueuing tracks, streaming audio, and verifying playback progress.
- [x] Simulated network latency and transient chunk request failures trigger automatic retries within the media source adapter without disrupting the listener's audio playback.
- [x] Background service execution and foreground notification updates operate continuously without receiving App Idle termination warnings from the Android system.
- [x] UI rendering performance is verified to ensure main-thread frame skipping during cold start and track transitions is fully resolved.
- [x] Ephemeral in-memory streaming buffer constraints established by ADR-0002 are strictly maintained, with zero audio chunk bytes persisting to local disk storage.
- [x] All existing repository, DAO, parser, engine, and UI unit test suites pass completely with 100% success rate.
