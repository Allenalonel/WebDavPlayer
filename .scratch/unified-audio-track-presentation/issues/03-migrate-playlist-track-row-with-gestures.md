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

**Status:** claimed

- [ ] Wrap `AudioTrackRow` inside `SwipeToDismissBox`
- [ ] Strictly verify 50% physical threshold and haptic vibration feedback
- [ ] Connect drag-and-drop handle into `leadingContent` slot with vertical drag gestures
- [ ] Wire overflow actions (`下一首播放`, `从歌单删除`, `详细信息`)
- [ ] Verify line count reduction from 440 to ≤160 lines
- [ ] Run `PlaylistDetailViewModelTest` and verify all swipe/reorder gestures remain 100% functional
