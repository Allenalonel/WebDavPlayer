# 05: End-to-End Verification and Regression Tests

**What to build:** Comprehensive end-to-end regression tests validating the full playback pipeline with large embedded artwork, folder cover fallbacks, Enhanced LRC files with inline timestamps, and bilingual translation display across cold start, queue mutation, and track transition.

**Blocked by:** 04-ui-bilingual-lyrics-view-and-artwork-rendering.md

**Status:** completed

- [x] Add end-to-end tests in `EndToEndPlaybackPipelineIntegrationTest` verifying:
  - High-res cover art (>500KB) extracted and rendered without truncation.
  - Folder-level `cover.jpg` loaded when audio file has no embedded artwork.
  - Enhanced LRC with multiple inline word timestamps verified to contain exact unique lines per verse.
  - Bilingual LRC loaded and verified with structured translations.
- [x] Run full test suite via `./gradlew.bat testDebugUnitTest` and confirm 100% green pass.
