# Spec: Architecture Hardening and Budget Compliance

## Summary
Execute comprehensive architectural hardening across session orchestration, dependency containerization, and UI presentation seams, addressing Candidates 1, 2, 3, and 4 from the post-Phase-2 architecture audit:
1. **Candidate 1 (Session Budget Compliance)**: Extract `PlaybackTimelineCoordinator` as the 5th internal collaborator for `MusicPlayerAppSessionImpl`, moving 5 observer coroutine pipelines and virtual timeline arithmetic out of the orchestrator. Slashing `MusicPlayerAppSessionImpl.kt` from **789 lines** down to **~380 lines** to strictly comply with the **600-line melting red line** in `AGENTS.md`.
2. **Candidate 2 (Unified AppContainer)**: Define a deep `AppContainer` interface on `WebDavApplication` and establish a unified `AppViewModelFactory`. Purge the 50-line rogue "shadow dependency graph" in `DirectoryBrowserViewModelFactory` and eliminate 4 scattered factory boilerplate files.
3. **Candidate 3 (PlaybackActions Aggregation)**: Aggregate 8 individual playback callback lambdas into a cohesive `PlaybackActions` seam in `ui/player/`, shrinking `FullPlayerView` parameters from **17 down to 5** and cleaning up `MainActivity` wiring.
4. **Candidate 4 (100% Track Presentation Unification)**: Migrate `VirtualTrackItemRow` inside `CueAlbumCard.kt` to the shared `AudioTrackRow` seam via the `leadingContent` track-number slot, eliminating the last 70 lines of orphaned track layout code in the repository.

---

## Detailed Architectural Seams

### 1. `PlaybackTimelineCoordinator` (Session Internal Seam)
`MusicPlayerAppSessionImpl` will delegate physical stream observations and virtual timeline arithmetic to `PlaybackTimelineCoordinator`:
- **Responsibilities**:
  - `startObservingEngine(coroutineScope, progressDispatcher)`: Collects `playerEngine.playbackState`, `currentPositionMs`, `durationMs`, `bufferedPositionMs`, `trackTransitions`, and `serverRepository.getActiveServer()`.
  - `mapEngineProgress(pos, dur, buf)`: Maps ExoPlayer global millisecond timestamps to relative CUE track progress based on `VirtualTimelineEngine`.
  - Encapsulates `progressPipelineJob`, `stateObserverJob`, `durationObserverJob`, `transitionJob`, and `serverObserverJob`.
- **Invariants**:
  - `MusicPlayerAppSession` public interface remains **100% stable** with zero signature changes.
  - All 3,000+ lines in `MusicPlayerAppSessionTest` pass without modification.
  - `MusicPlayerAppSessionImpl.kt` line count drops from 789 to ≤400 lines.

### 2. `AppContainer` & `AppViewModelFactory` (Dependency Seam)
- **Interface**:
  ```kotlin
  interface AppContainer {
      val serverRepository: ServerRepository
      val directoryRepository: DirectoryRepository
      val librarySearchRepository: LibrarySearchRepository
      val playlistRepository: PlaylistRepository
      val trackMetadataRepository: TrackMetadataRepository
      val cueSheetResolver: CueSheetResolver
      val playerEngine: AudioPlayerEngine
      val musicPlayerAppSession: MusicPlayerAppSession
      val sessionStore: PlaybackSessionStore
      val webDavClient: OkHttpWebDavClient
  }
  ```
- **Implementation**: `WebDavApplication` implements `AppContainer`.
- **Unified Factory**: `AppViewModelFactory(container: AppContainer)` resolves all ViewModels (`DirectoryBrowserViewModel`, `ServerManagementViewModel`, `PlaylistListViewModel`, `PlaylistDetailViewModel`) via type-safe dispatch.
- **Deletions**: Purge manual, fallback instantiations of `AppDatabase`, `CoverArtStorage`, and `TrackMetadataRepository` from factory classes.

### 3. `PlaybackActions` (UI Intent Seam)
- **Contract**:
  ```kotlin
  interface PlaybackActions {
      fun togglePlayPause()
      fun seekTo(positionMs: Long)
      fun skipToNext()
      fun skipToPrevious()
      fun cyclePlaybackMode()
      fun playQueueIndex(index: Int)
      fun removeQueueTrack(index: Int)
      fun retryPlayback()
  }
  ```
- **Consumer**: `FullPlayerView` receives `playbackActions: PlaybackActions`, reducing its constructor parameter count from 17 to 5:
  ```kotlin
  @Composable
  fun FullPlayerView(
      sessionState: PlayerSessionState,
      playbackProgress: PlaybackProgress,
      playbackActions: PlaybackActions,
      onCollapse: () -> Unit,
      modifier: Modifier = Modifier,
      // optional dialog/banner customization
  )
  ```
- **Leverage**: `MainActivity` simply constructs or binds a single `PlaybackActions` implementation backed directly by `MusicPlayerAppSession`.

### 4. `VirtualTrackItemRow` → `AudioTrackRow` (Presentation Seam)
- In `ui/browser/components/CueAlbumCard.kt`, replace the hand-rolled `VirtualTrackItemRow` with `AudioTrackRow`:
  - `title`: `track.title`
  - `subtitle`: `track.performer`
  - `durationText`: `PlaybackProgress.formatMs(track.durationMs)`
  - `isActive`: `isTrackActive`
  - `isPlaying`: `isPlaying`
  - `leadingContent`: Two-digit track number formatted pill `Text("%02d")`
  - `onClick`: `onClick`
- Eliminates 70 lines of redundant marquee, layout, and equalizer state calculation.

---

## Acceptance Criteria
1. **Budget Compliance**: Every file in `app/src/main/java` strictly adheres to `AGENTS.md` (lines < 600). Specifically, `MusicPlayerAppSessionImpl.kt` drops from 789 to ≤400 lines.
2. **Zero Rogue Singletons**: `DirectoryBrowserViewModelFactory` contains no fallback instantiation of Room database, OkHttpClient, or repositories.
3. **Parameter Reduction**: `FullPlayerView` parameter count reduced by ≥60%.
4. **Presentation Unification**: 100% of audio track lists in the app (Browser, Playlist, Search, Queue, and CUE Album) render through `AudioTrackRow`.
5. **100% Test Pass Rate**: All unit tests (`./gradlew testDebugUnitTest`) pass with zero regressions.
