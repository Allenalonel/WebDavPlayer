# 03: Hoist Floating Overlay and Decouple Scaffold Bottom Bar

**What to build:** As a listener, I want the MiniPlayer to float above the navigation bar as an independent overlay rather than being embedded in the bottom bar, so that both ends have clean breathing margins without solid rectangular color blocks bleeding through.

**Blocked by:** 02: Redesign MiniPlayer with Adaptive Liquid Glass and Swipe Gestures

**Status:** resolved

- [x] `MiniPlayer` is completely removed from `Scaffold.bottomBar` in `MainActivity.kt`.
- [x] `Scaffold.bottomBar` contains solely the persistent `NavigationBar`.
- [x] `MiniPlayer` is hoisted into an overlay container aligned to `Alignment.BottomCenter` directly above the navigation bar, with dedicated lateral breathing margins (14–16dp) and vertical elevation spacing (8–10dp).
- [x] Touch gestures inside the breathing margins outside the capsule pass through completely to scroll the underlying screen without dead touch zones.
- [x] Tapping the queue button on the floating capsule opens the existing `Playback Queue Bottom Sheet`.
- [x] Enter and exit animations for the floating capsule remain smooth and visually anchored above the navigation bar.

## Answer

Decoupled `MiniPlayer` from `Scaffold.bottomBar` in `MainActivity.kt`:
1. `Scaffold.bottomBar` now contains solely the persistent `NavigationBar`.
2. `MiniPlayer` is hoisted into an independent overlay `Box` aligned to `Alignment.BottomCenter` positioned with `bottom = innerPadding.calculateBottomPadding() + 8.dp` and `horizontal = 14.dp`, establishing pure breathing margins and vertical elevation above the bar.
3. The padding belongs to non-intercepting container modifiers, so touches outside the capsule boundary pass through seamlessly to underlying scrollable views.
4. Tapping the queue button on the floating capsule opens `PlaybackQueueBottomSheet`.
5. Enter and exit animations remain smooth and visually anchored above the navigation bar.
