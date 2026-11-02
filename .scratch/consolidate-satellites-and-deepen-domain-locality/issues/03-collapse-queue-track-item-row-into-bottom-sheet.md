# 03: Collapse Single-Caller QueueTrackItemRow into PlaybackQueueBottomSheet

**What to build:** Elimination of shallow single-caller file navigation inside the playback queue presentation layer. `QueueTrackItemRow.kt` and its private subtitle formatting and swipe dismissal helpers are folded directly into `PlaybackQueueBottomSheet.kt`, establishing a unified ~360-line queue sheet module. External callers interact exclusively with the `PlaybackQueueBottomSheet` seam, reducing the repository's exposed interface surface area.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `QueueTrackItemRow.kt` is deleted
- [ ] `QueueTrackItemRow`, `resolveQueueTrackSubtitle`, `handleQueueDismiss`, and `QueueLeadingContent` are integrated into `PlaybackQueueBottomSheet.kt` as private/internal collaborators
- [ ] `PlaybackQueueBottomSheet` public signature and presentation behavior remain 100% identical
- [ ] `PlaybackQueueBottomSheetTest` passes with zero regressions, verifying subtitle formatting and swipe dismissal behavior
