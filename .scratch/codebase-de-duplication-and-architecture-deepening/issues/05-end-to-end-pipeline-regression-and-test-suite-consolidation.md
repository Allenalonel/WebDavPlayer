# 05: End-to-End Pipeline Regression and Test Suite Consolidation

**What to build:** Comprehensive regression verification across all deepened modules and cleaned seams, consolidating the unit test suite and confirming zero dead code or broken contracts.

**Blocked by:** 01: Collapse Streaming Adapter and Unify MediaItem Assembly, 02: Deepen Domain Models for Paths and Progress, 03: Internalize Audio Quality Evaluation into AudioTrack, 04: Unify Session Navigation Seam and Deduplicate Metadata Dispatch

**Status:** ready-for-agent

- [ ] All unit and integration test suites are updated to reflect the removal of shallow helper objects and transitional factories.
- [ ] End-to-end playback, browsing, session persistence, and metadata pipelines pass all tests deterministically (`./gradlew testDebugUnitTest`).
- [ ] Zero deprecated module usages remain across production and test codebases.
- [ ] Codebase conforms fully to domain terms in `CONTEXT.md` and architecture principles in `codebase-design`.
