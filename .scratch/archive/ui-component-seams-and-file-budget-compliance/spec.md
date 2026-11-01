# Specification: UI Component Seams and File Budget Compliance

## Problem Statement

Following the complete deepening and decomposition of the domain and data layers (ADR-0021 through ADR-0024), all core repositories and session managers strictly comply with the 600-line single-file architectural budget. However, in the presentation layer, several Compose screen files have grown into unwieldy, monolithic files:

1. **`PlaylistDetailScreen.kt` (1,009 lines)**: Encloses the screen scaffold along with a 363-line double-action swipe row (`PlaylistTrackRow`), a 75-line track detail dialog (`PlaylistTrackDetailDialog`), a 94-line header (`PlaylistHeaderArea`), and drag-and-drop state helpers.
2. **`ServerListScreen.kt` (623 lines)**: Exceeds the 600-line threshold because 70% of the file (430 lines) consists of an inlined private `ServerCardItem` composable handling SSL trust indicators, connection testing, and action menus.
3. **`FullPlayerView.kt` (867 lines)**: Conflates full-screen sheet drag physics with vinyl record rotation animations (`ArtworkPage`), lyrics scrolling, format pills, transport sliders, and retry banners.

In contrast, the media library browser cleanly delegates presentation responsibilities to dedicated component files under `ui/browser/components/` (`AudioTrackItemRow`, `CueAlbumCard`, `DirectoryBreadcrumbStrip`, `SearchResultsViews`, `FileInfoDialog`).

## Solution

Standardize the presentation tier by decomposing the three monolithic screen files into focused, reusable component seams under their respective `components/` subdirectories:

1. **Decompose `PlaylistDetailScreen`**:
   - Extract `PlaylistTrackRow` (and swipe helpers) to `ui/playlist/components/PlaylistTrackRow.kt`.
   - Extract `PlaylistTrackDetailDialog` (and formatting helpers) to `ui/playlist/components/PlaylistTrackDetailDialog.kt`.
   - Extract `PlaylistHeaderArea` to `ui/playlist/components/PlaylistHeaderArea.kt`.
   - `PlaylistDetailScreen.kt` drops from 1,009 lines to under 400 lines.
2. **Decompose `ServerListScreen`**:
   - Extract `ServerCardItem` to `ui/server/components/ServerCardItem.kt`.
   - `ServerListScreen.kt` drops from 623 lines to under 200 lines.
3. **Decompose `FullPlayerView`**:
   - Extract vinyl disc cover rotation & ambient glow into `ui/player/components/PlayerVinylArtwork.kt`.
   - Extract playback mode, progress slider, and transport controls into `ui/player/components/PlayerTransportControls.kt`.
   - `FullPlayerView.kt` drops from 867 lines to under 450 lines.

All non-asset executable files across the entire codebase will then strictly satisfy the `<= 600` line budget stipulated in `AGENTS.md`.

## User Stories

1. As a music listener, I want playlist browsing, server management, and full-screen playback to retain their exact existing visual appearance, animations, and touch gestures, so that my experience remains completely seamless.
2. As an Android developer and maintainer, I want individual UI components (such as track rows, server cards, and detail dialogs) to live in dedicated files, so that components are easy to locate, understand, and preview in Compose tooling without scrolling through 1,000-line files.
3. As an Android developer and maintainer, I want all non-asset files in the repository to strictly respect the 600-line budget, so that AI navigation and human review operate within optimal cognitive limits.

## Implementation Decisions

1. **Zero Behavioral or Visual Alteration**: All composable signatures, parameters, state bindings, gestures, and test tags remain 100% identical. This is a pure structural extraction refactor.
2. **Package Layout Alignment**:
   - `ui/playlist/components/` receives `PlaylistTrackRow.kt`, `PlaylistTrackDetailDialog.kt`, `PlaylistHeaderArea.kt` (alongside existing `AddToPlaylistDialog.kt`).
   - `ui/server/components/` receives `ServerCardItem.kt` (alongside existing dialogs).
   - `ui/player/components/` receives `PlayerVinylArtwork.kt` and `PlayerTransportControls.kt`.
3. **Preserve Internal Visibility**: Components that are only consumed within their respective UI feature package are marked `internal` or given package-private visibility, avoiding unintended public exposure.

## Testing Decisions

- **Zero Regression**: Run the full unit and UI test suite (`./gradlew testDebugUnitTest`) after each ticket to verify that extraction does not break existing ViewModel or screen tests.
- **Compose Previews**: Ensure all extracted composables retain or introduce standard `@Preview` annotations for rapid visual inspection in Android Studio.

## Out of Scope

- Changing ViewModel state structures or business logic.
- Modifying domain entities, DAOs, or repositories.
- Altering user-facing styling or Material Design 3 theme tokens.
