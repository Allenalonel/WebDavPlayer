# 03: Migrate PlaylistTrackRow with Gesture Preservation

**What to build:**
Refactor `ui/playlist/components/PlaylistTrackRow.kt` (currently 440 lines) to delegate track presentation to `AudioTrackRow` while strictly preserving its sophisticated gesture and reordering systems:
1. **Threshold-Gated Double-Action Swipe Preservation**:
   - Wrap `AudioTrackRow` inside `SwipeToDismissBox`.
   - Strictly preserve the **50% physical width threshold** (`targetThreshold = 0.5f`).
   - Retain haptic feedback on crossing the 50% trigger threshold.
   - Retain two-way actions: right-swipe to delete (`onRemove`), left-swipe to add to next (`onPlayNext`).
2. **Custom Playlist Reorder Mode Preservation**:
   - Utilize `AudioTrackRow`'s `leadingContent` slot to render the vertical drag handle `[ ≡ ]` when `showDragHandle = true`.
   - Preserve `pointerInput` vertical drag gesture detection (`detectVerticalDragGestures`) linked to `DragDropState`.
   - Retain elevated shadow and z-index (`zIndex = 1f`, elevation = 6.dp) while dragging.
3. **Menu & Details Routing**:
   - Forward track detail dialog (`onShowDetails`), delete (`onRemove`), and play next (`onPlayNext`).
4. **Budget Reduction**:
   - Shrink `PlaylistTrackRow.kt` from 440 lines to ≤160 lines, eliminating duplicated metadata formatting, duration, and cover code.

**Blocked by:** 01

**Status:** resolved

## Answer
Migrated `ui/playlist/components/PlaylistTrackRow.kt` to delegate to `AudioTrackRow`:
- Shrunk `PlaylistTrackRow.kt` from 440 lines to 150 lines (≤160 lines budget).
- Extracted `PlaylistTrackRowCollaborators.kt` with `PlaylistTrackSwipeHelper`, `PlaylistTrackRowDefaults`, `PlaylistSwipeBackground`, `PlaylistTrackLeadingContent`, and `PlaylistTrackActionsMenu`.
- Preserved strict 50% physical width threshold (`positionalThreshold = { it * 0.5f }`) with haptic feedback and two-way actions (right swipe delete, left swipe play next).
- Preserved custom playlist reorder mode with vertical drag handle and drag elevation (6.dp).
- Added comprehensive unit tests in `PlaylistTrackRowTest.kt` verifying thresholds, dismissal actions, drag elevation, and line budget.
- Merge commit: `f68a867`

- [x] Wrap `AudioTrackRow` inside `SwipeToDismissBox`
- [x] Strictly verify 50% physical threshold and haptic vibration feedback
- [x] Connect drag-and-drop handle into `leadingContent` slot with vertical drag gestures
- [x] Wire overflow actions (`下一首播放`, `从歌单删除`, `详细信息`)
- [x] Verify line count reduction from 440 to ≤160 lines
- [x] Run `PlaylistDetailViewModelTest` and verify all swipe/reorder gestures remain 100% functional
