# 02: Conflict-Free Docked Mini-Player Capsule and Marquee

**What to build:** Reshape the docked mini-player into a floating Material 3 capsule with distinct tonal elevation and rounded corners. Eliminate touch target collision and ripple overlap between the play/pause toggle and the skip-next button by enforcing an 8dp physical separation and constraining touch bounds. Provide smooth marquee text scrolling for overflowing track titles.

**Blocked by:** 01 (Immersive Edge-to-Edge System Chrome and Top Bar Harmonization)

**Status:** ready-for-agent

- [ ] The docked mini-player renders as a floating capsule with extra-large rounded corners (`RoundedCornerShape(20.dp)`) and tonal elevation above the navigation bar.
- [ ] The primary play/pause toggle renders with a prominent filled-tonal container accent.
- [ ] The play/pause and skip-next buttons have a minimum of 8dp physical spacing between them, with non-overlapping 48dp touch targets and ripples.
- [ ] Long track titles scroll smoothly using horizontal marquee animations without being truncated by ellipses.
- [ ] A thin linear playback progress micro-bar sits flush against the bottom edge of the capsule with matched corner clipping.
- [ ] Tapping the mini-player expands the full player sheet, and tapping play/pause or next triggers playback actions without mis-hits.
