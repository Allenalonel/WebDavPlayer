# Spec: Unified Audio Track Presentation Seam

## Summary
Extract a high-leverage, deep Composable component `AudioTrackRow` in `ui/common/AudioTrackRow.kt` to unify track presentation and user interaction across the four primary screens:
1. **Directory Browser**: `ui/browser/components/AudioTrackItemRow.kt` (585 lines)
2. **Playlist Detail**: `ui/playlist/components/PlaylistTrackRow.kt` (440 lines)
3. **Library Search**: `ui/browser/components/SearchResultsViews.kt` (456 lines)
4. **Playback Queue**: `ui/player/PlaybackQueueBottomSheet.kt` (380 lines)

Currently, each of these four files independently implements track metadata layout, artist/album subtitle formatting, equalizer pulse animations, audio quality badge rendering, cover thumbnail rendering, and overflow action menus. This duplication accounts for ~1,800 lines of UI boilerplate and causes subtle gesture, timing, and formatting drifts across screens.

By establishing a unified, slot-based `AudioTrackRow` presentation seam, all four screens will delegate core rendering to this deep module, reducing each screen-specific row file by 60%~75% and strictly complying with the repository's 600-line ceiling.

---

## Architectural Seam & Slot Contract

The `AudioTrackRow` follows the Compose Slot API pattern:

```kotlin
@Composable
fun AudioTrackRow(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    durationText: String? = null,
    coverThumbnailPath: String? = null,
    audioQualityBadge: AudioQualityBadge? = null,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit = {},
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    actionsMenu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
)
```

### Slot Allocations:
- **Default `leadingContent`**: Renders `CoverThumbnailImage` with rounded corners. When `isActive = true`, seamlessly overlays `EqualizerTrackIndicator` (animated triple-bar pulse when `isPlaying`, static paused state otherwise).
  - *Customization*: Can be prepended or replaced with track index (Playback Queue) or drag handles (Playlist Custom Reorder Mode).
- **Body (`title` & `subtitle`)**: Standard MD3 typography with `basicMarquee` on active track, null-safe subtitle joining (`artist · album · size`), and audio quality badge capsule (`FLAC`, `Hi-Res`, `MP3 320k`).
- **Default `trailingContent`**: Duration timestamp and overflow menu icon (`⋮`).
- **`actionsMenu`**: Composable lambda populating the standard `DropdownMenu` items (`下一首播放`, `添加到歌单`, `详细信息/属性`, `从歌单移除`).
- **Outer Gesture Extensibility**: Designed to be cleanly wrapped by `SwipeToDismissBox` without polluting the inner track presentation with gesture drag state.

---

## Goals & Invariants

1. **Zero Visual or Functional Drift**:
   - Retain exact Material You dynamic theme tokens, typography, and spacing.
   - Retain fluid marquee animations for active track titles.
   - Retain equalizer pulse wave states (`PLAYING`, `PAUSED`, `IDLE`).
2. **Zero Gesture Regression**:
   - Retain `Threshold-Gated Double-Action Swipe` in `PlaylistTrackRow` (strictly 50% physical width threshold, haptic vibration feedback, cancel zone under 50%).
   - Retain `Custom Playlist Reorder Mode` drag-and-drop vertical handles and z-index elevation.
3. **Massive Code Reduction & File Budget Compliance**:
   - `AudioTrackItemRow.kt`: 585 lines → ≤130 lines.
   - `PlaylistTrackRow.kt`: 440 lines → ≤160 lines.
   - `SearchResultsViews.kt`: 456 lines → ≤180 lines.
   - `PlaybackQueueBottomSheet.kt`: 380 lines → ≤190 lines.
   - All files strictly adhere to the `AGENTS.md` 600-line limit.
4. **100% Verification Loop**:
   - All Robolectric unit tests and Compose preview components pass cleanly.
