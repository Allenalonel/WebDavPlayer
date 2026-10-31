# Map: UI Component Seams and File Budget Compliance

## Overview
Decomposing monolithic Compose screen files into focused component seams under their respective `components/` subdirectories to ensure 100% of non-asset executable files in the codebase comply with the 600-line budget in `AGENTS.md`.

## Issues Graph
- [x] **01-decompose-playlist-detail-screen** (Completed)
- [x] **02-decompose-server-list-screen** (Completed)
- [x] **03-decompose-full-player-view** (Completed)

## Decisions So Far
- **Decision 1 (Pure Structural Refactor)**: Zero visual or behavioral changes; existing composable signatures, states, test tags, and gestures are preserved verbatim.
- **Decision 2 (Component Placement)**:
  - `ui/playlist/components/` gains `PlaylistTrackRow.kt`, `PlaylistTrackDetailDialog.kt`, `PlaylistHeaderArea.kt`.
  - `ui/server/components/` gains `ServerCardItem.kt`.
  - `ui/player/components/` gains `PlayerVinylArtwork.kt`, `PlayerTransportControls.kt`, `FullPlayerRetryBanner.kt`.
- **Decision 3 (Scope & Budget Compliance Achieved)**:
  - `PlaylistDetailScreen.kt` reduced from 1,009 to 354 lines (< 400 target). See [01-decompose-playlist-detail-screen.md](issues/01-decompose-playlist-detail-screen.md).
  - `ServerListScreen.kt` reduced from 623 to 170 lines (< 200 target). See [02-decompose-server-list-screen.md](issues/02-decompose-server-list-screen.md).
  - `FullPlayerView.kt` reduced from 867 to 403 lines (< 450 target). See [03-decompose-full-player-view.md](issues/03-decompose-full-player-view.md).
  - All non-asset executable source files strictly adhere to the 600-line budget in `AGENTS.md`.
- **Decision 4 (Review & Quality Remediations)**: All extracted components marked `internal`, provided with standard `@Preview` composables, and verified across all 956 unit tests.
- **Decision 5 (Fluid Scrub Bar & Final Seam Polish)**: Replaced native M3 Slider limitation with custom `PlayerProgressSlider.kt` (229 lines) supporting seamless free drag across the entire track hitbox; reduced `PlayerTransportControls.kt` to 338 lines; removed all intermediate forwarding stubs in `FullPlayerView.kt` and `PlayerVinylArtwork.kt`. All files <= 500 lines.
