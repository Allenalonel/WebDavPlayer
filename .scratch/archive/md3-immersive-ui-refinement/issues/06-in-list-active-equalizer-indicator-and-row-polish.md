# 06: In-List Active Equalizer Indicator and Row Polish

**What to build:** Enhance the directory browser audio track list so listeners immediately identify the currently playing song when navigating folders. Render a dynamic three-bar jumping equalizer wave overlay over the album thumbnail of the active track that animates when playing and freezes when paused. Highlight the active row with a subtle tonal background and primary color accent. Enable running marquee text for overflowing track titles.

**Blocked by:** 01 (Immersive Edge-to-Edge System Chrome and Top Bar Harmonization)

**Status:** resolved

- [x] `AudioTrackItemRow` observes active track identity and playback state (playing vs paused).
- [x] When an item matches the currently playing track, an animated three-bar equalizer wave overlay displays over its thumbnail.
- [x] Equalizer bars bounce dynamically when audio is playing, freeze at fixed heights when audio is paused, and disappear when another track is selected.
- [x] Active tracks feature subtle `surfaceContainerHigh` background tinting and primary-colored title text.
- [x] Overlength audio track titles scroll with smooth horizontal marquee animations instead of harsh ellipsis clipping.
