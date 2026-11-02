# 04: Penetrative Edge-to-Edge Scrolling and Static Content Padding

**What to build:** As a listener, I want the music track list to smoothly slide underneath and behind the floating capsule while browsing, and when scrolling to the bottom, I want the last track to stop cleanly above the capsule without being covered or causing layout jumps.

**Blocked by:** 03: Hoist Floating Overlay and Decouple Scaffold Bottom Bar

**Status:** resolved

- [x] All primary browsing lists (`DirectoryBrowserScreen`, `PlaylistDetailScreen`, `ServerListScreen`) have their scrollable content (`LazyColumn`) extended edge-to-edge behind the bottom bar and floating capsule.
- [x] A standardized bottom content padding (approx. 160dp) is established so that the last item in any list rests completely above the capsule when scrolled to the end.
- [x] List viewport height does not dynamically resize or jump when the MiniPlayer appears or disappears, ensuring zero layout jank.
- [x] Items visibly pass through behind the translucent capsule during scrolling with fluid depth perception.

## Answer

Established penetrative edge-to-edge scrolling contract across all primary screen collections:
1. Removed the bottom padding constraint on the screens container in `MainActivity.kt`, allowing screens to render edge-to-edge behind the navigation bar and floating capsule.
2. Standardized `MiniPlayerDefaults.PenetrativeContentBottomPadding = 160.dp` across:
   - `DirectoryBrowserScreen` (and `SearchResultsViews`)
   - `PlaylistDetailScreen`
   - `PlaylistListScreen`
   - `ServerListScreen`
3. Because the inset is statically preserved at 160dp regardless of miniplayer presence, viewport heights remain rock-solid with zero layout jank or item jumping when playback begins or pauses.
4. When scrolling, list items smoothly pass underneath the translucent liquid glass capsule, resting completely above the capsule when reached at the absolute bottom.
