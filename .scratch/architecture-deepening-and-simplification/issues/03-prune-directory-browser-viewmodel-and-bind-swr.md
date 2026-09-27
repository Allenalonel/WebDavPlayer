# 03: Prune DirectoryBrowserViewModel and Bind SWR Stream

**What to build:** A focused, pruned `DirectoryBrowserViewModel` that consumes `DirectoryRepository`'s reactive SWR stream directly and eliminates eight shallow playback forwarding methods and exposed player session state, keeping browsing logic strictly separated from playback session management.

**Blocked by:** 02: Deepen DirectoryRepository SWR Stream

**Status:** completed

- [x] All eight pass-through playback methods (`togglePlayPause`, `seekTo`, `skipToNext`, `skipToPrevious`, `cyclePlaybackMode`, `playQueueIndex`, `removeQueueTrack`) and the `playerSessionState` property are removed from `DirectoryBrowserViewModel`.
- [x] `DirectoryBrowserViewModel` subscribes directly to `DirectoryRepository.observeDirectory(...)`, eliminating manual two-step cache and remote orchestration from the ViewModel.
- [x] Directory navigation, breadcrumb navigation, audio track click queueing, and playNext continue working seamlessly from `DirectoryBrowserScreen`.
- [x] Obsolete unit tests asserting ViewModel-to-player pass-through in `DirectoryBrowserViewModelTest` are pruned, with directory navigation and track dispatch tests fully passing.
- [x] Full suite unit tests pass via `./gradlew.bat testDebugUnitTest`.
