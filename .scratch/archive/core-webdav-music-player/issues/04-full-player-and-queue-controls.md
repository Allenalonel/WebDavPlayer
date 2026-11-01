# 04: Full Player View, Seeking, and Queue Controls

**What to build:** Tapping the mini-player expands a full-screen player view. The user can view track title and artist, drag a seek bar to jump to any position in the audio stream, skip to next/previous tracks, cycle playback modes (List Loop, Single Loop, Shuffle), and open a Playback Queue bottom sheet to inspect upcoming songs, jump to any queued track, or remove tracks from the queue.

**Blocked by:** 03: Basic Audio Streaming and Mini-Player

**Status:** resolved

- [x] Tapping the mini-player animates into an expanded full-screen player view.
- [x] Seek slider reflects current playback position (mm:ss) and total duration, allowing interactive scrubbing that cleanly seeks the remote audio stream.
- [x] Next and previous track buttons navigate through the current `PlaybackQueue`.
- [x] Playback mode button cycles between List Loop, Single Loop, and Shuffle, with visual indicator icons.
- [x] Track completion triggers automatic transition to the next track according to the active `PlaybackMode`.
- [x] Queue button opens a bottom sheet displaying the ordered list of tracks in the queue, with the active track highlighted.
- [x] Tapping any track in the queue sheet switches playback directly to that track; swiping or tapping delete removes it from the queue.
- [x] Tests verify seek event handling, playback mode transitions (shuffle permutation, repeat-one, repeat-all), and queue modifications.

## Comments

### Implementation Summary
1. **Domain Layer**:
   - Added `PlaybackMode` enum (`LIST_LOOP`, `SINGLE_LOOP`, `SHUFFLE`) with label and cycle logic.
   - Enhanced `PlaybackQueue` with `removeTrackAt` handling all index boundary conditions, plus `getNextIndex` and `getPreviousIndex` calculation functions for loop and shuffle permutations.
   - Updated `PlayerSessionState` to track active `playbackMode`.
   - Updated `MusicPlayerAppSession` and `AudioPlayerEngine` contracts with `cyclePlaybackMode`, `setPlaybackMode`, `removeQueueTrack`, and `seekTo`.
2. **Media3 Integration**:
   - Wired `Media3AudioPlayerEngine` to map `PlaybackMode` to ExoPlayer `repeatMode` (`REPEAT_MODE_ALL`, `REPEAT_MODE_ONE`) and `shuffleModeEnabled`.
   - Added `removeTrack` implementation in `Media3AudioPlayerEngine` delegating to `ExoPlayer.removeMediaItem`.
   - Enhanced `skipToNext` and `skipToPrevious` navigation logic to wrap around in list loop mode.
3. **UI Layer**:
   - Implemented `PlayerTimeFormatter` for `mm:ss` and `hh:mm:ss` string conversion.
   - Created `FullPlayerView` featuring album artwork placeholder, track metadata, seek slider with non-spamming interactive scrubbing, previous/play/pause/next controls, playback mode toggle button, and playback queue trigger.
   - Created `PlaybackQueueBottomSheet` displaying the ordered queue, highlighting the active playing track with equalizer indicator, supporting direct track selection, delete button, and swipe-to-dismiss deletion.
   - Integrated full-screen slide animation (`AnimatedVisibility` with slide/fade) into `DirectoryBrowserScreen` triggered on mini-player click and dismissed on collapse/back navigation.
4. **Testing**:
   - Added `PlaybackQueueTest` verifying queue modifications, boundary conditions, and navigation index resolution across list loop, single loop, and shuffle.
   - Added `PlayerTimeFormatterTest` verifying time formatting across 0, seconds, minutes, and hour ranges.
   - Extended `MusicPlayerAppSessionTest` covering mode cycling, track removals, list loop wraparound, single-loop repeat, and shuffle permutations.
   - Extended `Media3AudioPlayerEngineTest` and `DirectoryBrowserViewModelTest`.
