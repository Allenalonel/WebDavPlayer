# 03: Decompose FullPlayerView Page and Control Composables

**What to build:** Extract the page-specific and control composables from `FullPlayerView.kt` (867 lines) into dedicated component files under a new `ui/player/components/` package:

1. `ui/player/components/PlayerVinylArtwork.kt`: Encapsulates the vinyl record rotating cover art, ambient background glow gradient, and rotating animations (`ArtworkPage`, ~140 lines).
2. `ui/player/components/PlayerTransportControls.kt`: Encapsulates the progress slider, elapsed/remaining time labels, playback mode button (`PlaybackModeButton`), play/pause/skip buttons, and format specification badges (~200 lines).
3. `ui/player/components/FullPlayerRetryBanner.kt`: Encapsulates weak network manual retry and error banner presentation (~50 lines).

`FullPlayerView.kt` will retain sheet dismiss gesture physics, edge-to-edge system insets handling, and horizontal pager navigation, shrinking from 867 lines to under 450 lines.

**Blocked by:** None (can run independently or after 01, 02)

**Status:** resolved

- [x] Create `ui/player/components/PlayerVinylArtwork.kt` and move `ArtworkPage` and vinyl disc rotation animations
- [x] Create `ui/player/components/PlayerTransportControls.kt` and move `PlaybackModeButton`, transport controls, and slider logic
- [x] Create `ui/player/components/FullPlayerRetryBanner.kt` and move `FullPlayerRetryBanner` and `isFullPlayerRetryBannerVisible`
- [x] Update imports in `FullPlayerView.kt` and ensure clean compilation
- [x] Verify `FullPlayerView.kt` line count is under 450 lines (strictly complying with 600-line budget)
- [x] Verify all player and end-to-end tests pass cleanly (`./gradlew testDebugUnitTest`)

## Answer

Extracted `PlayerVinylArtwork.kt` (205 lines), `PlayerTransportControls.kt` (505 lines), and `FullPlayerRetryBanner.kt` (124 lines) under `ui/player/components/`. All extracted components are marked `internal` and include `@Preview` annotations. `FullPlayerView.kt` was reduced from 867 lines to 403 lines (under 450 lines target). All playback and player unit tests pass cleanly.
