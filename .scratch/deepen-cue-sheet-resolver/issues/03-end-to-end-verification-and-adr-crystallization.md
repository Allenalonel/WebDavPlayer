# 03: End-to-End Verification and ADR Crystallization

**What to build:** Run full regression test suite across all modules, ensure zero compiler warnings or broken tests, verify release APK packaging budget (`<= 7.5 MB`), and record architectural decision as ADR-0021.

**Blocked by:** 02 (Migrate Callers and Eliminate WebDavClient Leak)

**Status:** resolved

- [x] Execute `./gradlew testDebugUnitTest` and verify 100% test pass rate across the entire repository.
- [x] Confirm `DirectoryBrowserViewModelTest`, `MusicPlayerAppSessionTest`, and `LibrarySearchRepositoryTest` pass cleanly with no flaky static cache interactions.
- [x] Record `docs/adr/0021-deepen-cue-sheet-resolver-and-purge-network-leakage.md` capturing the consolidation of CUE handling, bounded LRU caching, and the removal of `WebDavClient` from UI/Session layers.
- [x] Verify release build and packaging size budget under 7.5 MB.
- [x] Update `spec.md` and ticket statuses to `resolved`.

## Comments

Full test suite executed with 100% pass rate. ADR-0021 recorded in `docs/adr/0021-deepen-cue-sheet-resolver-and-purge-network-leakage.md`. Release APK built successfully at 6.67 MB, well within the 7.5 MB budget. All tickets resolved.

## Answer

Verified 100% test pass rate across the full repository (`./gradlew testDebugUnitTest`), verified release APK build at 6.67 MB (`<= 7.5 MB` budget), recorded ADR-0021 in `docs/adr/0021-deepen-cue-sheet-resolver-and-purge-network-leakage.md`, and validated live device execution.
