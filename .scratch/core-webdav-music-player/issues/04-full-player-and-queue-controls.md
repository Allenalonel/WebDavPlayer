# 04: Full Player View, Seeking, and Queue Controls

**What to build:** Tapping the mini-player expands a full-screen player view. The user can view track title and artist, drag a seek bar to jump to any position in the audio stream, skip to next/previous tracks, cycle playback modes (List Loop, Single Loop, Shuffle), and open a Playback Queue bottom sheet to inspect upcoming songs, jump to any queued track, or remove tracks from the queue.

**Blocked by:** 03: Basic Audio Streaming and Mini-Player

**Status:** ready-for-agent

- [ ] Tapping the mini-player animates into an expanded full-screen player view.
- [ ] Seek slider reflects current playback position (mm:ss) and total duration, allowing interactive scrubbing that cleanly seeks the remote audio stream.
- [ ] Next and previous track buttons navigate through the current `PlaybackQueue`.
- [ ] Playback mode button cycles between List Loop, Single Loop, and Shuffle, with visual indicator icons.
- [ ] Track completion triggers automatic transition to the next track according to the active `PlaybackMode`.
- [ ] Queue button opens a bottom sheet displaying the ordered list of tracks in the queue, with the active track highlighted.
- [ ] Tapping any track in the queue sheet switches playback directly to that track; swiping or tapping delete removes it from the queue.
- [ ] Tests verify seek event handling, playback mode transitions (shuffle permutation, repeat-one, repeat-all), and queue modifications.
