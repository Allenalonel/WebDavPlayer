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

**Status:** resolved

## Answer
Migrated search results and playback queue presentation to `AudioTrackRow`:
- `SearchResultsViews.kt` refactored and shrunk from 467 lines to 140 lines (≤180 lines budget). Extracted `SearchTrackItem.kt`, `SearchDirectoriesSection.kt`, and `SearchHistorySection.kt`.
- `PlaybackQueueBottomSheet.kt` refactored and shrunk from 401 lines to 186 lines (≤190 lines budget). Extracted `QueueTrackItemRow.kt`.
- Extracted `AudioTrackTrailing.kt` to make trailing actions and badges cleanly modular.
- All 5 primary files are strictly < 200 lines (and all files well under the 600 line ceiling).
- Full unit test suite (`./gradlew testDebugUnitTest`) passed 100% with zero failures.
- Merge commit: `d8681ce`

- [x] Refactor `SearchAudioTrackItem` in `SearchResultsViews.kt` to use `AudioTrackRow`
- [x] Refactor `QueueTrackItemRow` in `PlaybackQueueBottomSheet.kt` to use `AudioTrackRow`
- [x] Remove duplicate equalizer indicator helpers and redundant local row styling
- [x] Verify line counts for all affected files stay strictly under 600 lines
- [x] Run full test suite (`./gradlew testDebugUnitTest`) and ensure 100% pass rate
