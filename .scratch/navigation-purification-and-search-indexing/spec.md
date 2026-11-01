# Spec: Navigation Seam Purification and Search Indexing

## Summary
Implement Candidate 3 (Navigation Seam Purification) and Candidate 4 (Library Search Indexing & CUE Encapsulation) from the Round 3 Architecture Audit:
1. **Candidate 3 (Purify MainNavigationCoordinator)**:
   - Remove `musicPlayerAppSession` parameter and unused pass-through methods (`togglePlayPause`, `skipToNext`) from `MainNavigationCoordinator`.
   - Fully decouple navigation routing from playback intent execution (which is now 100% unified under `PlaybackActions`).
   - Clean up call sites in `MainActivity.kt` and `MainNavigationCoordinatorTest`.
2. **Candidate 4 (Deepen Search Indexing & Purge CUE Leakage)**:
   - Eliminate direct usage of `CueAssociationHelper.findMatchingAudioFile` in `LibrarySearchRepositoryImpl`, routing CUE resolution strictly through `CueSheetResolver`.
   - Optimize directory search pipeline to avoid redundant full-table JSON deserialization during high-frequency typing by leveraging structured path matching and in-memory cache pre-filtering.
   - Maintain exact search semantics: matching directory names, directory paths, subdirectories, audio track titles/artists/albums/filenames, and CUE virtual tracks.

---

## Detailed Architectural Seams

### 1. `MainNavigationCoordinator` (Pure Navigation State Machine)
- **Before**: Holds `musicPlayerAppSession: MusicPlayerAppSession?` and provides:
  ```kotlin
  fun togglePlayPause() { musicPlayerAppSession?.togglePlayPause() }
  fun skipToNext() { musicPlayerAppSession?.skipToNext() }
  ```
- **After**:
  - `MainNavigationCoordinator` only depends on navigation-specific parameters:
    ```kotlin
    class MainNavigationCoordinator(
        initialDestination: AppDestination = AppDestination.SERVER_LIST,
        hasExplicitInitialDestination: Boolean = false,
        onDestinationChanged: ((AppDestination) -> Unit)? = null,
    )
    ```
  - State updates for `isMiniPlayerVisible` and `isFullPlayerExpanded` are driven reactively via:
    `fun onSessionStateChanged(sessionState: PlayerSessionState)`
  - All playback control actions flow exclusively through `PlaybackActions` in Compose UI (`MainActivity.kt` and player sheets).

### 2. `LibrarySearchRepositoryImpl` (Search Seam Deepening)
- **CUE Sheet Resolution Encapsulation**:
  - `LibrarySearchRepositoryImpl` must not import or invoke `CueAssociationHelper` directly.
  - CUE virtual track probing delegates to `CueSheetResolver.resolveCachedCueAlbums(directory)` or `getCachedVirtualTracks(...)`.
- **Search Query Pipeline Optimization**:
  - Combine reactive flows from `directoryCacheDao` and `trackMetadataDao`.
  - For directory entities, pre-filter by path and directory name before full deserialization.
  - Retain LRU search history persistence and fast deduplication.

---

## Goals & Invariants
1. **Zero Playback Tramp Methods**: `MainNavigationCoordinator` contains zero playback control forwarding stubs.
2. **Zero Internal CUE Leaks**: No file outside `data/cue/` imports `CueAssociationHelper`.
3. **Seamless Search UX**: Instant sub-millisecond search results matching directories, regular audio files, and CUE virtual tracks.
4. **File Budget Compliance**: Both `MainNavigationCoordinator.kt` and `LibrarySearchRepositoryImpl.kt` stay strictly under 250 lines (far below 600 lines).
5. **100% Test Pass Rate**: Full unit test suite (`./gradlew testDebugUnitTest`) passes with zero regressions.
