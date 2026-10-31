# 01: Create Shared AudioTrackRow Component

**What to build:**
Create the foundational shared `AudioTrackRow` composable under `ui/common/AudioTrackRow.kt`.
This component serves as the unified presentation seam for audio track items across the entire app, encapsulating:
1. **Title & Subtitle Typography**: Material Theme titleMedium with fluid `basicMarquee` on active track, and null-safe formatted subtitle (`artist · album · size`).
2. **Dynamic Cover Thumbnail**: Integrated with `CoverThumbnailImage` (rounded corners, format fallback icon, in-memory cache).
3. **Adaptive Equalizer Pulse Indicator**: Integrated with `EqualizerTrackIndicator` overlaying cover thumbnail when active (animated 3-bar jump when playing, static when paused).
4. **Audio Quality Badge**: Integrated display of `AudioQualityBadge` (lossless, Hi-Res, bitrate tag).
5. **Slot API Architecture**:
   - `leadingContent`: Slot allowing custom prefix (drag handle, track number) before or replacing the thumbnail.
   - `trailingContent`: Slot allowing custom duration/badge/action customization.
   - `actionsMenu`: Composable lambda for overflow dropdown menu items (`下一首播放`, `添加到歌单`, `详细信息`).

**Blocked by:** None

**Status:** completed

- [x] Create `ui/common/AudioTrackRow.kt` with clean slot-based API
- [x] Implement cover thumbnail with equalizer pulse overlay for active & playing states
- [x] Implement title marquee scrolling and formatted subtitle layout
- [x] Implement audio quality badge and duration rendering
- [x] Implement overflow dropdown menu with standard action callbacks
- [x] Provide comprehensive `@Preview` variants (idle, active playing, active paused, without cover)
- [x] Verify clean compilation and formatting
