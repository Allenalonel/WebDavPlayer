# 01: Consolidate PlaylistTrackRow and Eliminate Satellite Collaborators

**What to build:** Elimination of artificial file fragmentation in playlist item presentation. The swipe threshold algorithms, dismissal state handlers, background renderers, and dimension constants from the satellite collaborator file are completely folded into `PlaylistTrackRow.kt`, establishing a single cohesive ~430-line module. External callers only interact with the clean `PlaylistTrackRow` composable interface, eliminating cross-file navigation and purging the codebase's only remaining `*Collaborators.kt` file.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `PlaylistTrackRowCollaborators.kt` is deleted
- [ ] Row dimension constants (`PlaylistTrackRowDefaults`), swipe dismissal helpers (`PlaylistTrackSwipeHelper`), leading views (`PlaylistTrackLeading`), and dismiss background views (`SwipeBackgroundContent`) are integrated into `PlaylistTrackRow.kt`
- [ ] `PlaylistTrackRow` composable signature and external presentation behavior remain 100% identical
- [ ] `PlaylistTrackRowTest` passes with zero regressions, verifying drag elevation, 50% swipe threshold, and dismiss actions
