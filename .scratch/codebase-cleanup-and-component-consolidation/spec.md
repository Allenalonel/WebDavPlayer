# Spec: Codebase Cleanup and Component Consolidation

## Summary
Execute final codebase cleanup and micro-module consolidation addressing Candidates 1, 2, and 3 from the Round 4 architecture audit:
1. **Candidate 1 (Dead Code & Zombie Forwarding Purge)**: Delete 6 zero-production-usage files left over from earlier refactorings (4 obsolete ViewModelFactory forwarding shells, 1 deprecated CUE static cache, and 1 backward-compatibility alias file). Align reflection tests to real component interfaces.
2. **Candidate 2 (Reverse Dependency Inversion & Micro-Module Consolidation)**:
   - Invert the package dependency smell where `ui/common/` components reverse-import `ui/browser/components/EqualizerStateHelper`.
   - Consolidate 4 micro-module files in `ui/common/` (`TrackTagPill.kt`, `AudioQualityBadgePill.kt`, `TrackThumbnailLeading.kt`, `AudioTrackRowDefaults.kt`) into `AudioTrackRow.kt`, establishing a cohesive deep module (~350 lines, strictly under 600 lines) with high locality.
3. **Candidate 3 (Align MiniPlayer to PlaybackActions)**:
   - Align `MiniPlayer.kt` to consume the unified `PlaybackActions` contract (matching `FullPlayerView.kt`).
   - Eliminate the 100% redundant pass-through wrapper `DockedMiniPlayer`.
   - Reduce `MiniPlayer` parameter list from 7 down to 4.

---

## Detailed Architectural Seams

### 1. Zombie File Removals (Candidate 1)
- **Files to Delete**:
  - `ui/browser/DirectoryBrowserViewModelFactory.kt` (only forwards to `AppViewModelFactory`)
  - `ui/server/ServerManagementViewModelFactory.kt` (only forwards to `AppViewModelFactory`)
  - `ui/playlist/PlaylistListViewModelFactory.kt` (only forwards to `AppViewModelFactory`)
  - `ui/playlist/PlaylistDetailViewModelFactory.kt` (only forwards to `AppViewModelFactory`)
  - `data/cue/CueTextCache.kt` (deprecated in ADR-0021, 0 usages)
  - `ui/browser/components/AudioTrackItemRowCompat.kt` (105 lines of unused aliases: `FileItemRow`, `AudioBadge`, `LyricBadge`, `OtherBadge`)
- **Test Alignment**:
  - Update `AudioTrackItemRowTest.kt` to remove reflection assertions testing deprecated compatibility aliases.

### 2. Dependency Inversion & Consolidation in `ui/common/` (Candidate 2)
- **Package Hierarchy Invariant**:
  - `ui/common` represents foundational UI primitives and must **never import** screen-specific packages (`ui/browser`, `ui/playlist`, `ui/player`).
- **Dependency Inversion**:
  - Move `EqualizerWaveState` and `EqualizerStateHelper` into `ui/common/EqualizerTrackIndicator.kt`.
  - Update `ui/browser/components/` and `ui/browser/DirectoryBrowserScreen.kt` to import them from `ui/common/`.
- **Micro-Module Consolidation into `AudioTrackRow.kt`**:
  - Merge the following shallow files into `AudioTrackRow.kt`:
    - `TrackThumbnailLeading.kt` (74 lines)
    - `AudioQualityBadgePill.kt` (32 lines)
    - `TrackTagPill.kt` (31 lines)
    - `AudioTrackRowDefaults.kt` (90 lines)
  - Consolidated `AudioTrackRow.kt` will be ~340 lines, providing high locality where maintainers can read and edit track presentation in a single cohesive deep module.

### 3. MiniPlayer Alignment (Candidate 3)
- **Signature Refactoring**:
  ```kotlin
  @Composable
  fun MiniPlayer(
      sessionState: PlayerSessionState,
      playbackActions: PlaybackActions,
      modifier: Modifier = Modifier,
      playbackProgress: PlaybackProgress = PlaybackProgress.ZERO,
      onMiniPlayerClick: () -> Unit = {},
  )
  ```
- **Wrapper Elimination**:
  - Delete `fun DockedMiniPlayer(...)`.
  - In `MainActivity.kt`, invoke `MiniPlayer` directly:
    ```kotlin
    MiniPlayer(
        sessionState = playerSessionState,
        playbackProgress = playbackProgress,
        playbackActions = playbackActions,
        onMiniPlayerClick = { coordinator.expandFullPlayer() },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    )
    ```

---

## Goals & Acceptance Criteria
1. **Zero Zombie Forwarders**: 6 obsolete files removed.
2. **Zero Reverse Imports**: `ui/common/` contains zero imports from `ui/browser/`.
3. **High Locality**: Common audio track presentation consolidated into `AudioTrackRow.kt` (≤380 lines, well below 600-line ceiling).
4. **Unified Player Actions**: Both `MiniPlayer` and `FullPlayerView` consume `PlaybackActions`.
5. **100% Test Pass Rate**: Full unit test suite (`./gradlew testDebugUnitTest`) passes with zero failures.
