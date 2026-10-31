# 02: Migrate Browser AudioTrackItemRow

**What to build:**
Refactor `ui/browser/components/AudioTrackItemRow.kt` (currently 585 lines) to delegate its core layout, metadata formatting, equalizer pulse, and overflow menu to `AudioTrackRow`.
The directory browser's row component will focus strictly on directory-specific bindings:
1. **RemoteFile & Metadata Resolution**: Map `RemoteFile` and `TrackMetadata?` to `AudioTrackRow` title, subtitle, duration, and badge.
2. **File Info Dialog Integration**: Host `FileInfoDialog` state (`showInfoDialog`) triggered via the overflow menu's "详细信息" item.
3. **Action Routing**: Forward `onPlayNext`, `onAddToPlaylist`, and `onClick` cleanly.
4. **Non-Audio File Fallback**: Provide concise fallback rendering for non-audio remote files.
5. **Budget Reduction**: Shrink `AudioTrackItemRow.kt` from 585 lines to ≤130 lines, well below the 600-line ceiling.

**Blocked by:** 01

**Status:** open

- [ ] Refactor `AudioTrackItemRow` to delegate to `AudioTrackRow`
- [ ] Connect `RemoteFile` badge resolution and metadata formatting
- [ ] Wire overflow menu actions (`下一首播放`, `添加到歌单`, `详细信息`)
- [ ] Preserve `FileInfoDialog` invocation and non-audio item handling
- [ ] Verify line count reduction from 585 to ≤130 lines
- [ ] Verify DirectoryBrowserScreen previews and Robolectric unit tests pass cleanly
