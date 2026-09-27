# 02: Deepen Domain Models for Paths and Progress

**What to build:** Self-contained domain entities for directory paths and playback progress, eliminating shallow UI utility helpers and duplicate private sanitization logic in repositories.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Shallow utility object `BreadcrumbNavigationHelper` is removed.
- [ ] Path normalization, parent path extraction, ancestor hierarchy verification, and breadcrumb list calculation are consolidated directly into `RemoteDirectory` (or a dedicated domain value object).
- [ ] `DirectoryRepositoryImpl` reuses the unified domain path normalization, eliminating private duplicate `normalizePath` logic.
- [ ] Shallow utility object `PlayerTimeFormatter` is removed.
- [ ] `PlaybackProgress` provides intrinsic formatted string accessors (`formattedCurrentPosition`, `formattedDuration`) adhering to `mm:ss` and `hh:mm:ss` rules.
- [ ] UI components (`MiniPlayer`, `FullPlayerSheet`, `PlaybackQueueBottomSheet`, `DirectoryBreadcrumbStrip`) and tests migrate directly to consuming enriched domain models.
