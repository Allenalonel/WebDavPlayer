Status: ready-for-agent

# Feature Specification: Architecture Deepening and Codebase Simplification

## Problem Statement

As the WebDavPlayer application expanded with SWR persistent directory caching, native FFmpeg decoders, and playback session resumption, the codebase accumulated architectural friction across three distinct areas:

1. **Shallow Directory Caching & SWR Semantic Gap**: While ADR-0008 decided on a persistent directory cache with Stale-While-Revalidate (SWR), the current repository implementation is shallow. The directory browser ViewModel is forced to manually orchestrate two separate calls—first querying the local cache, then invoking remote listing. Crucially, the repository's listing method internally short-circuits upon a cache hit, preventing the background remote PROPFIND from ever executing during standard browsing. This defeats the asynchronous revalidation promise of ADR-0008, and leaves caching coordination logic leaking into presentation components.
2. **Shallow ViewModel Interface & Leaking Playback Responsibilities**: The directory browser ViewModel exposes eight pass-through playback methods (`togglePlayPause`, `seekTo`, `skipToNext`, `skipToPrevious`, `cyclePlaybackMode`, `playQueueIndex`, `removeQueueTrack`) and a playback session state property. The directory browser screen never calls any of these methods because the docked mini-player and expanded player view are bound directly to the shared music player application session. These pass-throughs artificially inflate the ViewModel's interface, blur domain boundaries between browsing and playback, and force dozens of trivial forwarding unit tests.
3. **Redundant Inheritance Wrapper in Extractor Module**: The ASF audio container demuxer is split across two classes: an empty fifteen-line subclass that merely re-exports GUID constants and inherits from a native extractor class containing the real demuxing implementation. This inheritance layer provides no abstraction, adds zero polymorphic value, and exists solely as legacy glue from an earlier migration.

## Solution

Consolidate and deepen these modules according to deep-module design principles, maximizing depth, improving locality, and eliminating pass-through glue:

1. **Deep Directory Repository with Streaming SWR**: Deepen the directory repository so that it fully encapsulates L1 memory caching, L2 database caching, and remote PROPFIND network execution behind a single reactive stream interface. When a directory is observed, the repository immediately emits the cached snapshot (0ms delay) and transparently launches a background network revalidation job. If remote contents differ from the cache, it updates local persistence and emits the fresh directory snapshot; if the network fails, the already-rendered cache view remains intact without error disruption. Callers provide only the target server and directory path, completely freed from cache coordination.
2. **Pruned Directory Browser ViewModel Interface**: Eliminate all eight playback control forwarding methods and the exposed playback session state from the directory browser ViewModel. The ViewModel focuses strictly on its core responsibilities: directory loading, breadcrumb generation, folder navigation, and dispatching track selection to the application playback session.
3. **Collapsed Unified ASF Extractor**: Merge the empty subclass directly into the primary extractor module to create a single deep ASF extractor implementing the media framework's extractor interface. GUID constants, seek map generation, and native demuxing hooks reside in one cohesive module without intermediate inheritance hops.

---

## User Stories

### SWR Directory Browsing & Cache Consistency

1. As a listener browsing folders in my WebDAV library, I want previously opened directories to render immediately (0ms) from cache, so that navigating through my library feels instant and stutter-free.
2. As a listener browsing a cached directory where new music files were recently added on the server, I want the view to automatically update in the background with the new files, so that I always see the latest library content without having to pull-to-refresh manually.
3. As a listener on an unstable mobile network, I want to browse previously cached directories without interruption, so that transient network dropouts do not show error dialogs or blank out already loaded folders.
4. As a listener in an empty directory or visiting a new folder for the first time, I want to see a clear loading indicator while the server is contacted, so that I know network progress is occurring.
5. As a listener pulling down to refresh a directory, I want the refresh action to bypass the local cache and query the server directly, so that I can explicitly force a complete directory reload.
6. As a developer maintaining the browsing experience, I want directory cache retrieval and remote synchronization to be handled by a single repository stream, so that presentation layers never have to manage caching logic or background revalidation timing.

### ViewModel Interface Focus & Decoupling

7. As a listener using the directory browser, I want tapping an audio file to immediately queue and start playback through the global player session, so that my music starts playing without UI stutter.
8. As a listener navigating through nested subfolders, I want breadcrumbs and back navigation to remain responsive and accurate, so that I can easily navigate up or down directory hierarchies.
9. As a developer writing tests for the directory browser, I want the ViewModel interface to contain only directory navigation and file click methods, so that tests only verify browsing behavior rather than mocking forwarding calls to the player engine.
10. As a developer modifying playback features (such as repeat modes or queue reordering), I want playback controls to exist solely within the playback session module, so that changes to playback logic never risk regressing the directory browser.

### Audio Format Demuxing & Extractor Simplicity

11. As a listener playing WMA audio files over WebDAV, I want playback to start cleanly with accurate duration and seek maps, so that track seeking is smooth and responsive.
12. As a listener streaming extended audio formats, I want the media framework's extractor pipeline to instantiate a single cohesive ASF extractor, so that playback initialization has minimal object allocation overhead.
13. As a developer maintaining audio demuxing and decoding components, I want all ASF container parsing, GUID specifications, and native demuxer bindings in one self-contained class, so that debugging format issues does not require tracing through redundant class hierarchies.

---

## Implementation Decisions

### Deep Directory Repository SWR Pipeline

- The directory repository interface will expose a unified directory observation stream (`observeDirectory(server, path, forceRefresh)` or equivalent Flow-based contract) instead of requiring callers to call separate cache and listing methods.
- When `forceRefresh = false`:
  - The repository immediately checks the L1 memory cache and L2 database cache, emitting a success state with the cached snapshot if found.
  - Concurrently or subsequently, the repository executes a background remote WebDAV listing.
  - If the remote response succeeds and differs from the cached snapshot, the repository updates L1 memory, persists to L2 database, and emits the updated directory snapshot to the stream.
  - If the remote request encounters a network error while a cached snapshot was already emitted, the repository swallows the network error so the user continues browsing the cached data undisturbed.
  - If no cache exists and the remote request fails, the repository emits an error state.
- When `forceRefresh = true`:
  - The repository skips cache emission, fetches directly from the remote WebDAV server, updates L1 and L2 caches upon success, and emits the fresh result.
- The repository continues to manage bounded LRU memory eviction and cache clearing per server.

### Pruning DirectoryBrowserViewModel

- Remove the following methods from the directory browser ViewModel:
  - `togglePlayPause()`
  - `seekTo(positionMs: Long)`
  - `skipToNext()`
  - `skipToPrevious()`
  - `cyclePlaybackMode()`
  - `playQueueIndex(index: Int)`
  - `removeQueueTrack(index: Int)`
  - `val playerSessionState: StateFlow<PlayerSessionState>?`
- The ViewModel retains:
  - `uiState: StateFlow<DirectoryBrowserUiState>`
  - `onDirectoryClicked(directory: RemoteDirectory)`
  - `onBreadcrumbClicked(breadcrumb: Breadcrumb)`
  - `onAudioTrackClicked(file: RemoteFile)`
  - `playNext(file: RemoteFile)`
  - `onNavigateUp(): Boolean`
  - `onRefresh()`
  - `onRetry()`
  - `resetToRoot()`
- The ViewModel subscribes to the deepened directory repository stream, updating its UI state accordingly without manual SWR orchestration.

### Consolidating AsfExtractor

- Remove the empty `AsfExtractor` subclass.
- Rename the concrete native extractor class directly to `AsfExtractor`, keeping all JNI bridge bindings, seek map calculations, packet parsing, and GUID constants inside this single module.
- Update extractor factory instantiations and unit tests to reference the unified `AsfExtractor` directly.

---

## Testing Decisions

- **Good Test Principle**: Tests must exercise external behavior through public module interfaces, never private fields, internal caches, or pass-through forwarding verifications.
- **Seam 1: Directory Repository Seam**:
  - Prior art: `DirectoryRepositoryTest.kt`.
  - Test cases:
    - Verifying immediate emission of cached snapshot followed by remote update emission.
    - Verifying graceful fallback to cached content when background remote listing throws a network exception.
    - Verifying direct remote execution and cache overwrite on forced refresh.
    - Verifying cold miss scenario emits loading then success or error.
- **Seam 2: Directory Browser ViewModel Seam**:
  - Prior art: `DirectoryBrowserViewModelTest.kt`.
  - Test cases:
    - Navigating folders, updating breadcrumbs, navigating up.
    - Clicking an audio track dispatches playback to the application session with correct directory queue.
    - Pull-to-refresh triggers forced reload on the repository stream.
    - All obsolete unit tests asserting ViewModel-to-player forwarding are safely deleted.
- **Seam 3: ASF Extractor Media3 Seam**:
  - Prior art: `AsfExtractorTest.kt`.
  - Test cases:
    - Verifying ASF header sniffing and GUID identification.
    - Verifying seek map creation and position-to-seek-point translation.
    - Verifying extraction pipeline without intermediate subclass indirection.

---

## Out of Scope

- Changes to Server Management or Server Repository deletion cascading (Candidate 4).
- Architectural decoupling of Android MediaSessionService and Media3AudioPlayerEngine (Candidate 5).
- Any modifications to the Jetpack Compose UI layout, Material Design 3 styling, or user-facing navigation tabs.
- Introduction of new audio decoding codecs or third-party streaming libraries.

---

## Further Notes

- All changes maintain full backward compatibility with existing Room database schema (no migration version bump required as `directory_cache` table structure is unchanged).
- All tests will run via `./gradlew.bat testDebugUnitTest` to guarantee zero regression before completing work.
