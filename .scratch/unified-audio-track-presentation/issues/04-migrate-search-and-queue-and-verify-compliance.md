# 04: Migrate Search & Queue Rows and Verify Compliance

**What to build:**
Complete the migration across the remaining two track lists and execute the full verification loop:
1. **Migrate `SearchResultsViews.kt` (`SearchAudioTrackItem`)**:
   - Refactor `SearchAudioTrackItem` to delegate presentation to `AudioTrackRow`.
   - Forward click to play, ad-hoc queue insertion (`Play Next`), and `AddToPlaylist`.
   - Shrink file size from 456 lines to ≤180 lines.
2. **Migrate `PlaybackQueueBottomSheet.kt` (`QueueTrackItemRow`)**:
   - Refactor `QueueTrackItemRow` to delegate presentation to `AudioTrackRow`.
   - Display track position index prefix in `leadingContent` slot alongside cover thumbnail.
   - Wire swipe-to-remove from queue and active track equalizer state.
   - Shrink file size from 380 lines to ≤190 lines.
3. **Purge Redundant Utilities**:
   - Remove duplicate duration formatters, equalizer bar drawing code, and unused style helpers.
4. **Full Verification Loop & Budget Compliance**:
   - Verify all 4 migrated files and `AudioTrackRow.kt` are strictly under 200 lines (far below the 600-line ceiling).
   - Execute the entire unit test suite (`./gradlew testDebugUnitTest`) to ensure 100% green tests.

**Blocked by:** 02, 03

**Status:** claimed

- [ ] Refactor `SearchAudioTrackItem` in `SearchResultsViews.kt` to use `AudioTrackRow`
- [ ] Refactor `QueueTrackItemRow` in `PlaybackQueueBottomSheet.kt` to use `AudioTrackRow`
- [ ] Remove duplicate equalizer indicator helpers and redundant local row styling
- [ ] Verify line counts for all affected files stay strictly under 600 lines
- [ ] Run full test suite (`./gradlew testDebugUnitTest`) and ensure 100% pass rate
