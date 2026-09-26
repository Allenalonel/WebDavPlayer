# 04: Full Player Sheet and Gesture Lyrics

**What to build:**
An immersive, gesture-driven `Full Player Sheet` that expands from the `Docked Mini-Player`. The sheet dynamically generates a soft atmospheric gradient background from the current track's album artwork and provides smooth horizontal swipe gestures between large album artwork and auto-scrolling synchronized lyrics with tap-to-seek support.

**Blocked by:** 01: Scaffold Navigation and Docked Mini-Player

**Status:** ready-for-agent

- [ ] `Full Player Sheet` expands smoothly upon tapping or swiping up on the `Docked Mini-Player`, and dismisses cleanly upon downward swipe or tapping the collapse arrow.
- [ ] Background displays an atmospheric gradient dynamically sampled from the current track's cover art, with elegant fallback to MD3 `surfaceContainer` colors.
- [ ] Horizontal pager / swipe gesture smoothly switches between the album artwork page and the synchronized lyrics page.
- [ ] Synchronized lyrics view highlights and smoothly scrolls the active vocal line, and allows listeners to tap any lyric line to instantly seek audio playback to that timestamp.
- [ ] Controls bar includes expressive play/pause, seek slider with real-time timestamps, previous/next, playback mode cycle (List Loop, Single Loop, Shuffle), and queue sheet triggers.
- [ ] Automated tests verify player sheet expansion state, lyric seek interactions, and dominant color extraction fallback.
