# 02: Two-Track Reactive Playback Session State Optimization

**What to build:** A two-track reactive state architecture within the application session module that decouples high-frequency millisecond progress updates from low-frequency structural session data. Structural session state (active WebDAV Server, Playback Queue, current Audio Track, and Playback Mode) updates exclusively on coarse domain lifecycle events. High-frequency playback progress (millisecond position, duration, and buffered progress) streams via a dedicated, lightweight reactive flow calculated and throttled off the Android main thread. The docked mini-player and directory browser screens subscribe only to the low-frequency state, while the full player sheet and lyric views consume the progress flow, eliminating main-thread Looper delays and UI frame drops.

**Blocked by:** None (can start immediately)

**Status:** completed

## Acceptance criteria

- [x] The application session exposes two distinct reactive streams: a structural session state stream and a dedicated playback progress stream.
- [x] Millisecond playback position updates do not emit new copies of the entire structural session state object, preventing unnecessary state churn across non-progress UI components.
- [x] High-frequency progress ticks are calculated and throttled on a background coroutine dispatcher before delivery to UI observers.
- [x] The docked mini-player, directory browser, and server management screens subscribe only to structural state and do not recompose on sub-second playback progress changes.
- [x] The full player sheet and gesture lyrics view smoothly consume the dedicated progress stream with millisecond fidelity and accurate lyric synchronization.
- [x] Cold-start restoration and process resumption seamlessly restore both the structural session state and the last known playback position from persistent storage.
- [x] Automated state tests verify that high-frequency progress changes produce zero emissions on the structural session state stream while accurately updating the progress stream.
