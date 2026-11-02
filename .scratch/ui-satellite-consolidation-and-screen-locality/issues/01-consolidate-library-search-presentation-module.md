# 01: Consolidate Library Search Presentation Module

**What to build:** Elimination of artificial file fragmentation in library search presentation. The search history section, directory match section, and track match item are completely folded into `SearchResultsViews.kt`, establishing a cohesive ~480-line module. External callers interact exclusively with the single clean `SearchResultsContent` composable interface, eliminating cross-file bouncing during search styling or empty-state maintenance while deleting 3 shallow satellite files.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] `SearchHistorySection.kt`, `SearchDirectoriesSection.kt`, and `SearchTrackItem.kt` are deleted
- [x] History chips rendering, directory match rows, and track match items are integrated into `SearchResultsViews.kt` as internal collaborators
- [x] `SearchResultsContent` composable signature and external presentation behavior remain 100% identical
- [x] Internal subtitle formatting helper `resolveSearchTrackSubtitle` is preserved in the package
- [x] `SearchTrackItemTest` passes with zero regressions, verifying subtitle formatting for standard and CUE virtual tracks
