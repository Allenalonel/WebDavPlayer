# 04: Penetrative Edge-to-Edge Scrolling and Static Content Padding

**What to build:** As a listener, I want the music track list to smoothly slide underneath and behind the floating capsule while browsing, and when scrolling to the bottom, I want the last track to stop cleanly above the capsule without being covered or causing layout jumps.

**Blocked by:** 03: Hoist Floating Overlay and Decouple Scaffold Bottom Bar

**Status:** ready-for-agent

- [ ] All primary browsing lists (`DirectoryBrowserScreen`, `PlaylistDetailScreen`, `ServerListScreen`) have their scrollable content (`LazyColumn`) extended edge-to-edge behind the bottom bar and floating capsule.
- [ ] A standardized bottom content padding (approx. 160dp) is established so that the last item in any list rests completely above the capsule when scrolled to the end.
- [ ] List viewport height does not dynamically resize or jump when the MiniPlayer appears or disappears, ensuring zero layout jank.
- [ ] Items visibly pass through behind the translucent capsule during scrolling with fluid depth perception.
