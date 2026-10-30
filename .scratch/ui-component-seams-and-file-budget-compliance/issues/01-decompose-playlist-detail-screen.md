# 01: Decompose PlaylistDetailScreen into Component Seams

**What to build:** Extract the inline composables and helpers from `PlaylistDetailScreen.kt` into dedicated component files under `ui/playlist/components/`:

1. `PlaylistTrackRow.kt`: Double-action swipeable row with 50% threshold, custom reorder drag handle, inactive styling, and track overflow dropdown menu (~360 lines).
2. `PlaylistTrackDetailDialog.kt`: Track metadata detail window and size formatting helpers (~90 lines).
3. `PlaylistHeaderArea.kt`: Header area with title, track count, "Play All", and "Shuffle Play" pill action buttons (~100 lines).

`PlaylistDetailScreen.kt` will focus cleanly on top-level scaffold, top app bar, drag-and-drop state host, and lazy column layout, shrinking from 1,009 lines to under 400 lines.

**Blocked by:** None (can start immediately)

**Status:** completed

- [x] Create `ui/playlist/components/PlaylistTrackRow.kt` and move `PlaylistTrackRow` and related row animations
- [x] Create `ui/playlist/components/PlaylistTrackDetailDialog.kt` and move `PlaylistTrackDetailDialog`, `PlaylistInfoRow`, and `formatTrackFileSize`
- [x] Create `ui/playlist/components/PlaylistHeaderArea.kt` and move `PlaylistHeaderArea`
- [x] Update imports in `PlaylistDetailScreen.kt` and verify clean compilation
- [x] Verify `PlaylistDetailScreen.kt` line count is under 450 lines (strictly complying with 600-line budget)
- [x] Verify all playlist tests pass cleanly (`./gradlew testDebugUnitTest`)
