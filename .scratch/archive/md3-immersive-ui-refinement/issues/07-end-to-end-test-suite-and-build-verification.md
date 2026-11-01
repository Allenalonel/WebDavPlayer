# 07: End-to-End Test Suite and Build Verification

**What to build:** Consolidate and execute unit and UI state tests covering audio quality specification extraction, equalizer state predicates, and component layout contracts. Execute full Gradle compilation and verify that all refactored screens, adaptive icons, and insets configurations function harmoniously without regressions.

**Blocked by:** 01, 02, 03, 04, 05, 06

**Status:** resolved

- [x] Unit tests verify audio quality specification string generation (format, sample rate, bit depth, bitrate) across all supported codecs.
- [x] Equalizer indicator state logic is tested for active, paused, and idle transitions.
- [x] Full project compiles cleanly via `./gradlew compileDebugSources` and `./gradlew test` with zero build or resource errors.
- [x] No regression in background audio playback, notification controls, or WebDAV directory caching.
