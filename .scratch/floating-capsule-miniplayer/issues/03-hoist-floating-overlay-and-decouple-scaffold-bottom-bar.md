# 03: Hoist Floating Overlay and Decouple Scaffold Bottom Bar

**What to build:** As a listener, I want the MiniPlayer to float above the navigation bar as an independent overlay rather than being embedded in the bottom bar, so that both ends have clean breathing margins without solid rectangular color blocks bleeding through.

**Blocked by:** 02: Redesign MiniPlayer with Adaptive Liquid Glass and Swipe Gestures

**Status:** ready-for-agent

- [ ] `MiniPlayer` is completely removed from `Scaffold.bottomBar` in `MainActivity.kt`.
- [ ] `Scaffold.bottomBar` contains solely the persistent `NavigationBar`.
- [ ] `MiniPlayer` is hoisted into an overlay container aligned to `Alignment.BottomCenter` directly above the navigation bar, with dedicated lateral breathing margins (14–16dp) and vertical elevation spacing (8–10dp).
- [ ] Touch gestures inside the breathing margins outside the capsule pass through completely to scroll the underlying screen without dead touch zones.
- [ ] Tapping the queue button on the floating capsule opens the existing `Playback Queue Bottom Sheet`.
- [ ] Enter and exit animations for the floating capsule remain smooth and visually anchored above the navigation bar.
