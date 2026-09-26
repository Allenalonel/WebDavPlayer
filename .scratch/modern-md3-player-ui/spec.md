Status: ready-for-agent

# Feature Specification: Modern Material Design 3 UI and Navigation Architecture

## Problem Statement

Users of WebDavPlayer currently face a fragmented and visually outdated interface that fails to meet the ergonomic and aesthetic standards of modern mobile music players.

Specifically, the application relies on an abrupt screen replacement between the server management screen and the directory browser. The playback control bar is trapped solely inside the directory browser screen, causing playback controls to vanish whenever a user navigates to the server list, even though audio continues streaming in the background. Furthermore, the directory browser presents flat text buttons for breadcrumbs and unformatted plain text lists for music tracks, offering minimal visual hierarchy, zero audio format/quality indication, and lacking cohesive Material Design 3 styling. Finally, the full-screen player and lyrics display are rigidly separated and lack fluid gesture controls, immersive album artwork atmospherics, or smooth synchronized lyric scrolling, creating a jarring user experience compared to mainstream music players.

## Solution

Modernize the entire user interface and interaction flow in accordance with Material Design 3 (MD3) expressive guidelines and mainstream streaming music player conventions.

1. **Scaffold-Hoisted Navigation Architecture**: Establish a persistent top-level layout with a `Primary Navigation Bar` featuring two primary tabs: [Library / Directory Browser] and [Server Management]. State, scroll positions, and audio playback remain uninterrupted across tab transitions.
2. **Docked Mini-Player**: Hoist a persistent, elevated floating pill card above the `Primary Navigation Bar`. It remains visible across all screens whenever an audio track is queued or playing, displaying track artwork, title, artist, playback state, and interactive controls, with tap or upward-swipe gesture expansion to the `Full Player Sheet`.
3. **Immersive Full Player Sheet with Gesture-Driven Lyrics**: Implement an atmospheric full-screen player that dynamically samples dominant colors from the current album art to generate a soft background gradient. Enable fluid horizontal swipe gestures to switch between large album artwork and smooth, auto-scrolling, synchronized lyrics with tap-to-seek support.
4. **Enhanced Directory Browsing**: Introduce a `Directory Breadcrumb Strip` featuring scrollable MD3 chips for effortless hierarchical navigation, along with rich `Audio Track` list items displaying embedded cover thumbnails, metadata, and prominent `Audio Quality Badge` pills (e.g., FLAC, WAV, MP3).
5. **Seamless Server Switching**: Allow users to tap any server card in the Server Management view to immediately set it as the `Active Server` and smoothly navigate directly to the root of the music library.

---

## User Stories

### Global Navigation & Scaffold

1. As a listener, I want a persistent `Primary Navigation Bar` with [Browser] and [Servers] tabs, so that I can switch between browsing my music library and managing storage sources at any time with a single tap.
2. As a listener, I want my browsing directory position and scroll state preserved when switching back and forth between tabs, so that I don't lose my place in large music folders.
3. As a listener, I want a `Docked Mini-Player` floating above the `Primary Navigation Bar` that remains visible across all top-level destinations, so that I never lose playback control while exploring servers or changing settings.
4. As a listener, I want the `Docked Mini-Player` to only appear when there is an active `Playback Session State` (loaded or playing track), keeping the screen clean when idle.
5. As a listener, I want the `Docked Mini-Player` styled as an MD3 floating pill with rounded corners, subtle container elevation, and responsive ripple feedback, so that it looks polished and modern.
6. As a listener, I want to tap anywhere on the `Docked Mini-Player` or swipe upwards on it, so that the `Full Player Sheet` expands smoothly.
7. As a listener, I want quick play/pause toggle and skip-to-next buttons on the `Docked Mini-Player`, so that I can perform frequent actions without expanding the full player.

### Immersive Full Player Sheet

8. As a listener, I want the `Full Player Sheet` to sample dominant colors from the current track's album art to generate a soft, adaptive gradient background, so that the listening experience feels immersive and tailored to each song.
9. As a listener with tracks lacking cover art, I want a graceful fallback to standard MD3 `surfaceContainer` colors, so that the player remains legible and aesthetically consistent.
10. As a listener, I want to swipe horizontally on the player body to smoothly transition between the album artwork view and the synchronized lyrics view, so that switching views feels natural and responsive.
11. As a listener, I want an explicit lyrics icon button in the player control bar that also toggles between artwork and lyrics, so that I have multiple accessible ways to switch views.
12. As a listener, I want to swipe downward or tap a collapse arrow button, so that the `Full Player Sheet` slides down smoothly back to the `Docked Mini-Player`.
13. As a listener, I want a refined MD3 progress slider with elapsed time and remaining duration labels, allowing me to scrub through the track with real-time time updates.
14. As a listener, I want an expressive, prominently styled play/pause button alongside previous, next, playback mode (List Loop, Single Loop, Shuffle), and queue sheet triggers, so that all playback controls are ergonomically positioned.

### Synchronized Lyrics Interaction

15. As a listener, I want synchronized lyrics to scroll smoothly with the current vocal line highlighted and enlarged, so that I can easily sing along or follow the song.
16. As a listener, I want to tap any lyric line in the synchronized lyrics view to immediately seek playback to that exact timestamp, so that I can replay favorite sections without guessing on the progress bar.
17. As a listener listening to a track without lyrics, I want an elegant empty state indicating that no lyrics are available, so that I understand why the view is blank.

### Modern Directory Browser & Breadcrumbs

18. As a listener, I want a `Directory Breadcrumb Strip` rendered as horizontal scrolling MD3 chips representing the folder path from root to current directory, so that I can see my location in deep NAS folder structures.
19. As a listener, I want to tap any chip in the `Directory Breadcrumb Strip` to instantly jump back to that ancestor directory, so that navigating back up multiple folder levels is instantaneous.
20. As a listener, I want each audio track in the directory list to display a thumbnail cover, track title, artist/album details, and an `Audio Quality Badge` indicating the audio container and encoding quality (e.g., FLAC, MP3 320k, WAV), so that I can easily identify high-fidelity recordings.
21. As a listener, I want an action menu button (three dots) on each audio track item offering secondary actions (e.g., Play Next, View File Info), so that I have flexible queue management without interrupting immediate playback.
22. As a listener, I want directory folders in the list to have distinct MD3 container styling, icon indicators, and file count hints, so that folders stand out clearly from playable music files.
23. As a listener, I want smooth pull-to-refresh animations aligned with MD3 guidelines, so that refreshing remote directories feels responsive.

### Streamlined Server Management

24. As a user, I want server items displayed as modern MD3 elevated cards showing connection status, server protocol (HTTP/HTTPS), address, and active indicator, so that my server configurations look clean and informative.
25. As a user, I want clicking a server card to automatically activate that server as the `Active Server` and immediately switch the tab to the library browser root, so that getting to my music requires the minimum number of taps.
26. As a user, I want edit, test connection, and delete actions neatly contained within card actions or menus, so that server maintenance is straightforward.

---

## Implementation Decisions

- **Root Scaffold & Navigation Hoisting**:
  - The application root will be refactored into a single `Scaffold` containing the `Primary Navigation Bar` and a hoisted `Docked Mini-Player`.
  - The navigation state will maintain the active top-level tab (`BROWSER` vs `SERVERS`) and preserve the back-stack / folder position of the `Directory Browser`.
  - The `Docked Mini-Player` will be rendered as a floating MD3 Surface (`shape = RoundedCornerShape(16.dp)`, margin padding 8.dp) positioned directly above the navigation bar whenever `currentTrack` in `PlayerSessionState` is not null.
  
- **Full Player & Lyrics Architecture**:
  - The `Full Player Sheet` will be hoisted at the root level using an animated slide-up container or modal sheet overlaying the entire screen.
  - A horizontal pager or swipeable state will host two pages: Page 0 for the atmospheric Cover Artwork & metadata; Page 1 for the synchronized, smooth-scrolling `LyricsView`.
  - Background color theming will utilize dominant color extraction from the local cached cover thumbnail (using a lightweight bitmap sampler) to produce a radial or vertical gradient transitioning to `surfaceContainerLowest`.

- **Directory Browser & Breadcrumb Strip**:
  - Replace the current raw horizontal text row with a `Directory Breadcrumb Strip` utilizing MD3 `AssistChip` / `FilterChip` components inside a horizontal scroll row with auto-scroll to the end.
  - Upgrade audio track items into MD3 `ListItem` components with a dedicated format tag component (`Audio Quality Badge`) displaying container type (FLAC, MP3, WAV, WMA) and sample rate / bitrate when available.

- **Theme & Expressive Styling**:
  - Enhance `WebDavPlayerTheme` with customized typography and shape scales adhering to Material 3 Expressive guidelines, ensuring robust contrast in both dynamic Monet and static dark/light themes.

---

## Testing Decisions

- **What Makes a Good Test**:
  - Tests must verify external behavior and state transitions rather than private implementation details or pixel-level UI measurements.
  - Tests verify that clicking a server switches the active server state and triggers navigation to the browser root.
  - Tests verify that playback session state changes (track title, play/pause, buffering) correctly update the hoisted player presentation state.
  - Tests verify that breadcrumb chip selection properly dispatches directory traversal commands to ancestor paths.
  - Tests verify that audio quality badges correctly format audio track extensions and metadata tags.

- **Modules to be Tested**:
  - `DirectoryBrowserViewModel`: Traversal via breadcrumbs, audio format badge classification, and queue population.
  - `ServerManagementViewModel`: Active server selection with navigation intent triggers.
  - `MusicPlayerAppSession`: Persistent session state broadcast to the hoisted UI.
  - UI state mapping and formatter utilities (e.g., `AudioQualityBadgeHelper`, `PlayerTimeFormatter`).

- **Prior Art**:
  - `DirectoryBrowserViewModelTest.kt` for directory navigation and audio track interaction assertions.
  - `ServerManagementViewModelTest.kt` for server configuration and selection flows.
  - `MusicPlayerAppSessionTest.kt` and `PlaybackSessionResumptionTest.kt` for session state integrity.

---

## Out of Scope

- Offline track downloading and persistent local audio caching (strictly prohibited by ADR-0002).
- Third-party music streaming services (e.g., Spotify, Subsonic, Plex); strictly WebDAV audio playback.
- Audio DSP effects (graphic equalizer, spatializer, crossfade).
- Video playback or image gallery browsing.

---

## Further Notes

- All changes maintain strict 100% backward compatibility with Android 10+ (API 29+), existing Room databases, and DataStore schemas.
- No new third-party heavy dependencies are introduced; dynamic color extraction uses lightweight Android bitmap sampling.
