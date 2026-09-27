# 04: Minimalist Full Player Sheet (Variant C Audiophile Specs & Dual Buffer)

**What to build:** Refactor the full player sheet to maximize album artwork scale and atmospheric immersion by eliminating the top app bar, collapse arrow button, and redundant "Now Playing / Lyrics" title text. Retain exclusively a refined central drag handle supporting tap-to-dismiss and vertical swipe-down dismiss gestures. Display an audiophile specification badge capsule beneath the track title showing format, bit depth, sampling rate, and bitrate. Implement a dual-layer progress slider displaying both playback progress and WebDAV remote streaming buffer progress.

**Blocked by:** 01 (Immersive Edge-to-Edge System Chrome and Top Bar Harmonization), 02 (Conflict-Free Docked Mini-Player Capsule and Marquee)

**Status:** ready-for-agent

- [ ] The top app bar, top-left collapse icon, and "Now Playing/Lyrics" title text are completely removed from the full player sheet.
- [ ] A minimalist centered drag handle is displayed at the top, supporting tap-to-collapse and downward swipe-to-collapse gestures.
- [ ] Album artwork and atmospheric cover background gradients expand with generous vertical breathing room.
- [ ] An audiophile specification badge capsule displays prominently beneath the title displaying Hi-Res gold styling, audio format, sample rate, bit depth, and bitrate.
- [ ] The progress slider features a dual-layer track: a translucent secondary track indicating remote WebDAV streaming buffer cache and a primary track indicating elapsed playback time.
- [ ] The player sheet content safely avoids camera cutouts and bottom gesture bars using status bar and navigation bar insets.
- [ ] Switching between cover art and synchronized lyrics remains smooth and functional.
