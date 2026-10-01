# 05: End-to-End Cache Invalidation and Refresh Regression Verification

**What to build:**
Validate the complete user journey from application cache clearance to total visual and functional recovery: wiping thumbnail files from disk while preserving Room database records, launching the app, browsing directories, pulling down to refresh, and resuming playback, confirming that all cover art self-heals, system media controls display proper artwork, and zero `FileNotFoundException` errors occur.

**Blocked by:** 03: Harmonized Directory Refresh Lifecycle and Job Stability, 04: Dead Artwork URI Protection for Media Playback Session

**Status:** completed

- [x] Integration test simulates app cache clearance and verifies automatic progressive thumbnail self-healing during directory browsing.
- [x] Integration test verifies that pull-to-refresh correctly re-probes remote folder artwork and updates UI rows.
- [x] Integration test verifies that resuming playback with cleared cache does not emit file not found exceptions to system media listeners.
- [x] Full project test suite (`./gradlew testDebugUnitTest`) passes with 100% success rate.
- [x] Release APK (`./gradlew assembleRelease`) builds cleanly with zero lint or packaging errors.
