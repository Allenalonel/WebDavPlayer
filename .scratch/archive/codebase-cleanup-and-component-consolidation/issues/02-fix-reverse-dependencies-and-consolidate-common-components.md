# 02: Fix Reverse Dependencies and Consolidate Common Micro-Modules

**What to build:**
Resolve package dependency inversion and consolidate fragmented micro-components in `ui/common/`:
1. **Fix Layer Inversion (`ui.common` → `ui.browser`)**:
   - `ui/common/EqualizerTrackIndicator.kt` currently imports `com.webdav.player.ui.browser.components.EqualizerStateHelper` and `EqualizerWaveState`.
   - Move `EqualizerWaveState` and `EqualizerStateHelper` directly into `ui/common/EqualizerTrackIndicator.kt` (or `ui/common/EqualizerState.kt`).
   - Remove `ui/browser/components/EqualizerStateHelper.kt`.
   - Update usages across `ui/browser/` and `ui/playlist/` to import cleanly from `com.webdav.player.ui.common`.
2. **Consolidate Micro-Components into `AudioTrackRow.kt`**:
   - Merge 4 tiny satellite files into `ui/common/AudioTrackRow.kt`:
     - `TrackThumbnailLeading.kt` (74 lines)
     - `AudioQualityBadgePill.kt` (32 lines)
     - `TrackTagPill.kt` (31 lines)
     - `AudioTrackRowDefaults.kt` (90 lines)
   - Establish `AudioTrackRow.kt` as a self-contained deep module (~340 lines, far below 600-line budget limit) providing high locality for audio track layout, styling tokens, and sub-components.
   - Delete the 4 separate micro-module files.
3. **Update Test Call Sites**:
   - Update imports in unit tests (`AudioTrackRowTest`, `TrackThumbnailLeadingTest`, `AudioQualityBadgePillTest`) to reference `ui.common.AudioTrackRow` directly.

**Blocked by:** None

**Status:** resolved

- [x] Move `EqualizerStateHelper` & `EqualizerWaveState` to `ui/common/`
- [x] Delete `ui/browser/components/EqualizerStateHelper.kt` and update caller imports
- [x] Merge `TrackThumbnailLeading`, `AudioQualityBadgePill`, `TrackTagPill`, and defaults into `AudioTrackRow.kt`
- [x] Delete the 4 satellite micro-files in `ui/common/`
- [x] Verify `AudioTrackRow.kt` line count is ≤380 lines (strictly complying with 600-line limit)
- [x] Run test suite to verify 100% pass rate

## Answer
- Relocated `EqualizerWaveState` and `EqualizerStateHelper` to `com.webdav.player.ui.common` (inside `EqualizerTrackIndicator.kt`), resolving the architectural reverse dependency smell where `ui/common` was importing from `ui/browser`.
- Removed `ui/browser/components/EqualizerStateHelper.kt` and updated callers in `DirectoryBrowserUiState.kt`, `CueAlbumCard.kt`, `EqualizerStateHelperTest.kt`.
- Consolidated 4 micro-module files (`TrackThumbnailLeading.kt`, `AudioQualityBadgePill.kt`, `TrackTagPill.kt`, `AudioTrackRowDefaults.kt`) into `AudioTrackRow.kt`. The consolidated file stands at 379 lines (well under the 600-line ceiling and ≤ 380 lines budget).
- Removed the 4 satellite micro-files in `ui/common/`.
- Updated `TrackTagPillTest.kt` and `AudioTrackRowTest.kt` for the consolidated structure and verified 100% test pass rate across the test suite.
