# 01: Scaffold Navigation and Docked Mini-Player

**What to build:**
A unified top-level Material Design 3 Scaffold with a persistent `Primary Navigation Bar` hosting two primary destinations ([Browser] and [Servers]), alongside a hoisted `Docked Mini-Player`. The mini-player floats above the navigation bar as an elevated pill card whenever audio is loaded or playing, remaining continuously visible and operational even when users switch between library browsing and server management.

**Blocked by:** None (can start immediately)

**Status:** ready-for-review

- [x] Top-level layout replaced with a persistent Material 3 `Scaffold` featuring a `Primary Navigation Bar` with [Browser] and [Servers] tabs.
- [x] Tab switching preserves existing directory browsing depth, scroll positions, and view states without reloading.
- [x] `Docked Mini-Player` hoisted to root layout level, appearing floating above the navigation bar whenever `currentTrack` is non-null.
- [x] `Docked Mini-Player` displays album thumbnail, track title, artist/format subtitle, buffering state, and responsive play/pause and skip-next action buttons.
- [x] Playback controls remain continuously active and visible across tab navigation (never disappearing when navigating to server list).
- [x] Automated tests verify navigation destination switching and persistent mini-player state synchronization with `MusicPlayerAppSession`.
