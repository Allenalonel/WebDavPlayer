Status: completed

# Spec: Material Design 3 Immersive UI and Audio Experience Refinement

## Problem Statement

Users navigating the WebDavPlayer application experience visual friction, layout overlap, and immersion breaks across the primary navigation, media browsing, and player sheets:

1. **Non-Immersive System Bars & Inconsistent Header Tones**: The status bar and system gesture navigation bar display solid system background colors rather than blending with application surfaces. The window does not extend edge-to-edge, breaking modern Android immersion expectations. Crucially, the status bar displays a disconnected color tone separate from the application's top app bar, creating an unsightly horizontal seam at the top of the viewport.
2. **Redundant Path Presentation in Directory Browser**: The directory browser top app bar displays the complete directory path as a subtitle directly above the directory breadcrumb strip. Because the breadcrumb strip already presents the full hierarchical path with interactive ancestor chips, repeating the path text in the top bar creates redundant visual clutter and wastes vertical screen real estate.
3. **Docked Mini-Player Button Collision & Touch Target Overlap**: The docked mini-player's play/pause and skip-next icon buttons are placed within a 40dp visual height and separated by only 4dp of horizontal padding. Because Compose Material 3 icon buttons enforce a 48dp minimum interactive component constraint, their ripple effects and touch targets collide and overlap, degrading touch ergonomics and causing visual overlap on certain screen densities.
4. **Misaligned Server Cards & Crowded Actions**: Server management cards employ an arbitrary, hardcoded 52dp start offset to indent secondary metadata tags under the leading server icon. This offset breaks on non-standard screen densities and custom font scaling. Furthermore, cards pack three individual action buttons alongside status feedback into a narrow horizontal row, crowding layout boundaries and causing awkward wrapping. Heavy 1.5dp static borders also violate Material 3 tonal elevation principles.
5. **Cluttered Full Player Sheet Header**: The full player sheet header includes an obsolete top-left collapse icon button and redundant "Now Playing / Lyrics" ("正在播放/歌词") text. Since the sheet already features a standard top drag handle, global vertical swipe-down dismiss gestures, and system back interception, these header elements represent visual noise that unnecessarily compresses album artwork and distorts atmospheric cover gradients.
6. **Lack of Distinct Application Identity**: The application manifest references Android's fallback green robot icon (`@android:drawable/sym_def_app_icon`). The application lacks a dedicated vector adaptive launcher icon and fails to support Android 13+ Material You dynamic themed icon coloring.
7. **Absence of In-List Active Playback Indication & Text Truncation**: When users return from the player sheet to browse a directory, there is no dynamic visual indicator showing which audio track is currently playing. Concurrently, long song and album titles are truncated aggressively with ellipses, detracting from the refined feeling of an audiophile music library.

## Solution

A comprehensive Material Design 3 refinement that establishes edge-to-edge system immersion, resolves component touch and layout collisions, purges redundant header text, and elevates audio library presentation through validated prototype patterns:

1. **Immersive Edge-to-Edge Chrome**:
   - Activate Android edge-to-edge window decor across the entire activity lifecycle.
   - Unify the top status bar background seamlessly with the top app bar container tone (`surfaceContainer`), ensuring a continuous, unbroken header surface.
   - Extend the primary navigation bar and full player sheet background gradients through the bottom gesture navigation bar, providing proper inset padding so content remains safely within interactive viewports.
2. **Streamlined Single-Source Directory Breadcrumbs**:
   - Remove the redundant directory path subtitle from the directory browser top app bar, leaving a clean, bold current folder or server title.
   - Retain the interactive Material 3 breadcrumb strip as the sole authoritative presentation of navigation hierarchy.
3. **Conflict-Free Floating Capsule Mini-Player**:
   - Reshape the docked mini-player into a floating Material 3 capsule (`RoundedCornerShape(20.dp)`) with distinct tonal elevation.
   - Establish an 8dp physical separation between the play/pause button and the skip-next button, constraining touch bounds and utilizing a filled-tonal container for the primary playback toggle to completely eliminate touch collisions and ripple overlap.
   - Integrate running marquee text for overflowing track titles.
4. **Tonal Grid Server Cards with Streamlined Actions**:
   - Replace hardcoded start padding with natural, flexible column alignment where metadata tags naturally align with the title block.
   - Replace heavy static borders with Material 3 tonal containers (`surfaceContainerHigh` for the active server and `surfaceContainerLow` for idle servers).
   - Consolidate trailing action buttons into a primary connection test trigger and an overflow more menu, preventing horizontal button compression.
5. **Pure Minimalist Full Player Sheet (Variant C - Audiophile Specification)**:
   - Eliminate the top app bar, collapse arrow, and title text from the full player sheet, leaving exclusively a refined central drag handle.
   - Maximize album artwork scale and atmospheric mesh gradient breathing room.
   - Incorporate an audiophile specification badge capsule directly below the track title displaying Hi-Res gold styling, audio format, bit depth, sampling rate, and bitrate (e.g., `FLAC · 96kHz / 24-bit · 2450 kbps`).
   - Implement dual-layer seek slider tracks displaying real-time playback position overlaid on top of a translucent WebDAV streaming buffer cache track.
6. **Dedicated Adaptive App Icon with Material You Themed Support**:
   - Construct a custom vector adaptive icon set embodying a minimalist vinyl record and streaming cloud motif.
   - Deliver background, foreground, and Android 13+ monochrome icon layers that dynamically adapt to the user's system wallpaper color scheme.
7. **Active Equalizer Track Indicator & Running Marquee**:
   - Display a dynamic three-bar jumping equalizer wave animation over the cover thumbnail of the currently playing track in directory browsing lists, freezing when paused and hiding when idle.
   - Apply smooth marquee scrolling across directory items, mini-player headers, and full player sheets for long track names.

## User Stories

1. As a music listener, I want the status bar to seamlessly match the color of the application header, so that my screen feels unified and free of harsh dividing seams.
2. As a music listener, I want the application background to bleed gracefully behind the bottom gesture bar, so that the player makes full use of modern edge-to-edge mobile displays.
3. As a music listener browsing audio directories, I want the top bar to show only the current folder name without duplicating the path shown in the breadcrumbs, so that the screen is clean and easy to scan.
4. As a music listener navigating deep folders, I want to rely on the interactive breadcrumb chips to see where I am and jump to ancestor folders, so that navigation is predictable and single-sourced.
5. As a music listener using one hand, I want to tap play/pause or next on the docked mini-player without mis-hitting the adjacent button, so that playback controls are accurate and frustration-free.
6. As a music listener, I want the docked mini-player to look like a modern floating pill with smooth corners, so that it feels integrated with Material Design 3.
7. As a music listener with long song titles, I want the mini-player title to scroll smoothly rather than being chopped off with ellipses, so that I can read the complete track name.
8. As a music listener managing multiple WebDAV connections, I want server cards to have clean alignments without awkward indents, so that server details and connection states are visually harmonious.
9. As a music listener on a small phone, I want server card action buttons to fit comfortably on the screen, so that buttons never wrap or push status text out of view.
10. As a music listener, I want active servers to be highlighted with elegant tonal containers rather than harsh thick lines, so that the UI adheres to modern Material 3 aesthetics.
11. As a music listener expanding the full player, I want maximum screen space dedicated to large album artwork and background gradients without cluttered top buttons, so that music playback feels immersive.
12. As a music listener, I want to dismiss the full player sheet by pulling down on the top drag handle or swiping down anywhere on the cover, so that gesture dismissal feels natural.
13. As an audiophile streaming lossless FLAC or Hi-Res tracks over WebDAV, I want to see detailed audio specifications (format, sample rate, bit depth, bitrate) prominently in the full player, so that I can appreciate the audio quality of my private collection.
14. As a music listener with slow or fluctuating network connections, I want to see how much of the track has been buffered in the seek bar, so that I know whether playback will continue smoothly without stuttering.
15. As a music listener browsing a large directory, I want the currently playing track to display an animated jumping equalizer icon, so that I can instantly identify which song is playing.
16. As a music listener pausing playback, I want the equalizer icon in the list to freeze in place, so that visual feedback matches audio state.
17. As a smartphone user, I want the application to have its own distinctive vinyl and cloud launcher icon on my home screen, so that the app looks professional and recognizable.
18. As an Android 13+ user with themed icons enabled, I want WebDavPlayer's launcher icon to tint to match my wallpaper palette, so that my desktop maintains visual cohesion.
19. As a developer maintaining the codebase, I want window insets to be handled consistently across Scaffold, TopAppBar, and navigation components, so that future screen additions do not introduce status bar clipping bugs.
20. As a developer writing tests, I want the new audio quality formatting, marquee support, and active equalizer states to be verified across stable seams without brittle private state inspection.

## Implementation Decisions

### 1. Immersive Edge-to-Edge System Chrome
- **Activity Level Integration**: Call `enableEdgeToEdge()` inside `MainActivity.onCreate()` prior to `setContent`. Configure system bar styles so icons transition to light or dark contrast automatically based on the active Material 3 theme.
- **TopAppBar Insets Alignment**: Standardize top app bars across `DirectoryBrowserScreen` and `ServerListScreen` to use `surfaceContainer` as their `containerColor`. Allow `TopAppBarDefaults.windowInsets` to naturally consume status bar insets, ensuring the container background paints seamlessly behind the status bar icons while action icons and titles are positioned below the status bar.
- **Scaffold & NavigationBar Insets Handling**: Preserve the bottom navigation bar's container background so it extends into the gesture bar inset area (`WindowInsets.navigationBars`), maintaining a clean, solid backdrop behind the Android gesture bar pill.
- **Full Player Sheet Window Bleed**:
  - The root surface of `FullPlayerView` consumes `Modifier.fillMaxSize()` with atmospheric mesh gradients extending from top edge to bottom edge.
  - Apply `Modifier.statusBarsPadding()` and `Modifier.navigationBarsPadding()` to the inner content column to ensure interactive controls, drag handles, and bottom buttons remain inside safe interactive bounds.

### 2. Directory Browser Top Bar Simplification
- **Subtitle Elimination**: Remove the `uiState.currentPath` text block from the top app bar in `DirectoryBrowserScreen`.
- **Title Behavior**: Display solely the current directory name (or server name when at root `/`).
- **Hierarchy Ownership**: All path inspection, navigation backtracks, and ancestor folder jumps remain exclusively owned by `DirectoryBreadcrumbStrip`.

### 3. Docked Mini-Player Capsule & Collision Elimination
- **Container Geometry**: Apply `MaterialTheme.shapes.extraLarge` / `RoundedCornerShape(20.dp)` to the outer surface with `surfaceContainerHigh` color and tonal elevation.
- **Button Sizing & Heatmap Clearance**:
  - The play/pause toggle uses a `FilledTonalIconButton` of size 42dp with an explicit primary container accent.
  - The skip-next control uses an `IconButton` of size 38dp.
  - Enforce an 8dp horizontal spacer between the two buttons and constrain internal padding so the 48dp minimum interactive touch bounding boxes never intersect.
- **Marquee Text Integration**: Apply `Modifier.basicMarquee()` to the mini-player song title so that long titles scroll smoothly instead of being truncated.
- **Bottom Edge Progress Micro-bar**: Embed the 2dp linear progress indicator flush against the bottom edge with matching bottom corner clips.

### 4. Server Management Card Grid Alignment & Action Consolidation
- **Grid Realignment**: Eliminate `Modifier.padding(start = 52.dp)`. Organize the card into a cohesive vertical column:
  - Header: 40dp server circle icon, flexible title/host column (`weight(1f)`), and compact protocol/active badge chips.
  - Metadata Row: Flow row containing username chip and self-signed certificate indicator, left-aligned with the header text.
  - Tonal Divider: Subtle 0.5dp line with `outlineVariant` alpha.
  - Footer Action Row: Left-aligned connection status text (with spinner/check/warning icons) and right-aligned compact icon buttons ("Test Connection" and "More Menu").
- **Tonal Elevation Hierarchy**: Active servers receive `surfaceContainerHigh` with a subtle primary tone highlight; inactive servers use `surfaceContainerLow`.

### 5. Full Player Sheet Minimalist Refinement (Variant C)
- **Top Header Elimination**: Remove `TopAppBar`, the down-arrow collapse button, and the "Now Playing / Lyrics" text.
- **Minimalist Drag Handle**: Render a centered drag handle (width 36dp, height 4dp, rounded corners 2dp) with vertical swipe dismiss gestures.
- **Audiophile Detail Capsule**: Render a prominent pill badge directly beneath the track title and artist name displaying high-resolution parameters derived from domain models:
  ```text
  ⚡ FLAC · 96kHz / 24-bit · 2450 kbps
  ```
  (or format/bitrate equivalent for MP3, WAV, WMA).
- **Dual-Layer Buffered Progress Slider**:
  - Primary slider thumb and active track represent current playback elapsed time.
  - Translucent secondary track beneath the slider represents remote WebDAV streaming buffer progress.
- **Marquee Title**: Enable `Modifier.basicMarquee()` on the song title for seamless display of long song titles.

### 6. Vector Adaptive App Icon & Monochrome Theming
- **Asset Directory Structure**: Create adaptive icon XML resources under `res/mipmap-anydpi-v26/` and corresponding density drawables:
  - `ic_launcher_background.xml`: Deep blue-indigo circular gradient base.
  - `ic_launcher_foreground.xml`: Precision vector combining minimalist vinyl grooves and streaming cloud wave lines.
  - `ic_launcher_monochrome.xml`: Flat silhouette mask enabling Material You wallpaper dynamic color tinting on Android 13+.
- **Manifest Registration**: Update `AndroidManifest.xml` to replace `@android:drawable/sym_def_app_icon` with `@mipmap/ic_launcher` and `android:roundIcon="@mipmap/ic_launcher_round"`.

### 7. In-List Active Equalizer Indicator
- **State Propagation**: Pass `activeTrackPath: String?` and `isPlaying: Boolean` to `AudioTrackItemRow`.
- **Dynamic Waveform Rendering**:
  - When the track matches the active session track, render an equalizer wave overlay on the album art thumbnail consisting of three vertical bars.
  - Animate bar heights (4dp to 20dp) with staggered phases when `isPlaying == true`.
  - Freeze bar heights when `isPlaying == false` (paused).
  - Hide the overlay when the track is not the currently active track.
- **Row Highlight**: Apply `surfaceContainerHigh` background tint and primary font color to the active track title.

## Testing Decisions

- **Good Test Criteria**: Tests must verify user-observable behavior and component state contracts across established architectural seams, without asserting against private internal composable details.
- **Testing Seam A: UI State & Badge Resolution**:
  - Test audio quality badge resolution on `AudioTrack` and `RemoteFile` to ensure sample rates, bit depths, bitrates, and format strings format correctly for the audiophile badge capsule.
- **Testing Seam B: Equalizer State Logic**:
  - Test that the active track identification predicate accurately distinguishes playing, paused, and idle tracks based on session state and track paths.
- **Testing Seam C: Resource & Manifest Verification**:
  - Test that adaptive icon resources exist, compile under AAPT2, and are correctly linked in `AndroidManifest.xml`.
- **Prior Art**:
  - Similar UI state and domain evaluation unit tests located in `app/src/test/java/com/webdav/player/ui/` and `app/src/test/java/com/webdav/player/domain/`.

## Out of Scope

- Implementing an online lyrics search service or downloading third-party lyrics (relying on existing dual-source local/remote LRC resolution).
- Redesigning the audio playback engine core or modifying Media3 ExoPlayer decoders.
- Adding server-side transcoding or modifying remote WebDAV server capabilities.
- Redesigning the add/edit server dialog form fields.

## Further Notes

- The visual structure and user interactions defined in this specification have been empirically verified via the interactive HTML prototype stored at `.scratch/prototype/ui-prototype.html`.
