# 01: Immersive Edge-to-Edge System Chrome and Top Bar Harmonization

**What to build:** Activate Android edge-to-edge window decor across the activity lifecycle so the system status bar and gesture navigation bar blend seamlessly with application surfaces. Unify the status bar background color with the top app bar container tone, eliminating any disjointed dividing seams. Extend the bottom navigation bar container through the bottom gesture insets. Streamline the directory browser header by removing the redundant path subtitle and leaving a clean, single-line folder title while retaining the interactive breadcrumb strip as the sole authoritative hierarchy navigator.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] Activity activates edge-to-edge system decor on startup (`enableEdgeToEdge()`).
- [x] Status bar icons automatically adjust between light and dark contrast based on the active Material 3 theme and content surfaces.
- [x] The top app bar container background paints seamlessly behind the status bar icons with no color discrepancy or dividing seam.
- [x] The bottom navigation bar background extends smoothly behind the system gesture navigation bar.
- [x] The directory browser top app bar displays only the current folder or server title, without duplicating the full directory path text.
- [x] The directory breadcrumb strip continues to function correctly beneath the streamlined header for ancestor navigation.
