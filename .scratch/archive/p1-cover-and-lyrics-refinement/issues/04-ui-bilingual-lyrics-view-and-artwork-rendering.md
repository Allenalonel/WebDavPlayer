# 04: UI Bilingual Lyrics View and Artwork Rendering

**What to build:** Update `LyricsView` and `LyricLineItem` to render structured bilingual lyrics (original main text in primary styling, translation in secondary subtitle styling), and polish `FullPlayerView` / `MiniPlayer` artwork display so that newly loaded or updated covers transition smoothly without flicker.

**Blocked by:** 02-queue-metadata-propagation-and-cache-healing.md, 03-industrial-lrc-normalizer-and-bilingual-model.md

**Status:** completed

- [x] Update `LyricLineItem` in `LyricsView.kt` to display `line.translation` below `line.text` with subtle contrast and smaller font size when present.
- [x] Ensure tap-to-seek and smooth auto-scrolling work seamlessly with bilingual items.
- [x] Verify that `CoverThumbnailImage` in `MiniPlayer`, `FullPlayerView`, and `AudioTrackItemRow` reacts instantly when `coverThumbnailPath` updates from null to a valid local path.
- [x] Add Compose unit / screenshot / presentation tests for `LyricsView` with bilingual content.
- [x] Ensure `FullPlayerPresentationTest` and `LyricSeekInteractionTest` pass.
