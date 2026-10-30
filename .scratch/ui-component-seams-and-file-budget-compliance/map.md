# Map: UI Component Seams and File Budget Compliance

## Overview
Decomposing monolithic Compose screen files into focused component seams under their respective `components/` subdirectories to ensure 100% of non-asset executable files in the codebase comply with the 600-line budget in `AGENTS.md`.

## Issues Graph
- [ ] **01-decompose-playlist-detail-screen** (Unblocked & ready)
- [x] **02-decompose-server-list-screen** (Completed)
- [ ] **03-decompose-full-player-view** (Unblocked & ready)

## Decisions So Far
- **Decision 1 (Pure Structural Refactor)**: Zero visual or behavioral changes; existing composable signatures, states, test tags, and gestures are preserved verbatim.
- **Decision 2 (Component Placement)**:
  - `ui/playlist/components/` gains `PlaylistTrackRow.kt`, `PlaylistTrackDetailDialog.kt`, `PlaylistHeaderArea.kt`.
  - `ui/server/components/` gains `ServerCardItem.kt`.
  - `ui/player/components/` gains `PlayerVinylArtwork.kt`, `PlayerTransportControls.kt`, `FullPlayerRetryBanner.kt`.
- **Decision 3 (Scope & Budget)**:
  - `PlaylistDetailScreen.kt` reduces from 1,009 to < 400 lines.
  - `ServerListScreen.kt` reduces from 623 to < 200 lines.
  - `FullPlayerView.kt` reduces from 867 to < 450 lines.
  - Brings 100% of Kotlin source files under the 600-line cohesion limit.
