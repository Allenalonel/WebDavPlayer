# 03: Breadcrumb Strip and Quality Badges

**What to build:**
A modernized Directory Browser experience featuring an interactive horizontal scrolling `Directory Breadcrumb Strip` built with MD3 chips for fast ancestor directory jumps, accompanied by refined `Audio Track` items that feature album cover thumbnails, format & audio quality badges (e.g., FLAC, MP3 320k, WAV), and secondary action menus.

**Blocked by:** 01: Scaffold Navigation and Docked Mini-Player

**Status:** resolved

- [x] Directory path rendered as a `Directory Breadcrumb Strip` using horizontally scrollable MD3 `AssistChip` components, auto-scrolling to the active tail.
- [x] Tapping any chip in the breadcrumb strip navigates directly back to that ancestor directory level.
- [x] Audio track items styled as MD3 `ListItem` with cover thumbnail, bold title, artist/album subtitle, and prominent `Audio Quality Badge` pills indicating audio format/quality.
- [x] Track items provide a secondary action button ("...") for track information and future contextual actions.
- [x] Directory folder items feature elevated MD3 container styling and distinct folder iconography.
- [x] Automated tests verify breadcrumb ancestor traversal and audio format badge mapping logic.
