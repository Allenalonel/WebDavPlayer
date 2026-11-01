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

**Status:** open

- [ ] Move `EqualizerStateHelper` & `EqualizerWaveState` to `ui/common/`
- [ ] Delete `ui/browser/components/EqualizerStateHelper.kt` and update caller imports
- [ ] Merge `TrackThumbnailLeading`, `AudioQualityBadgePill`, `TrackTagPill`, and defaults into `AudioTrackRow.kt`
- [ ] Delete the 4 satellite micro-files in `ui/common/`
- [ ] Verify `AudioTrackRow.kt` line count is ≤380 lines (strictly complying with 600-line limit)
- [ ] Run test suite to verify 100% pass rate
