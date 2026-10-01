# Cache Invalidation and Refresh Resilience Specification

## Problem Statement

When users clear the application cache through Android system settings or when the operating system reclaims cache directories under low-storage conditions, local thumbnail files stored in the cache directory are permanently deleted while the persistent Room database and DataStore session state remain intact.

Upon reopening the application, users experience multiple cascading failures:
1. Cover artwork thumbnails for all tracks in directories and the playback queue fail to load and display as generic placeholder icons, while system media notification services and lock screen handlers spam `FileNotFoundException (ENOENT)` errors.
2. The self-healing cache logic fails to detect missing folder artwork files on disk because it queries the storage layer using the audio track's path instead of checking the actual thumbnail file path recorded in the cache entity.
3. Once the cache directory has been purged, subsequent attempts to download or self-heal artwork fail silently because the storage layer relies on an un-recreated directory reference and never calls parent directory creation before writing files.
4. When users pull to refresh or tap the refresh action to restore missing artwork, the directory browser view model immediately dismisses the refresh indicator before background track metadata resolution completes, and subsequent refresh gestures cancel ongoing background extraction tasks in a destructive race condition.
5. In-memory negative caching in the metadata resolver prevents subsequent network probes for folder artwork from running, locking the application into an artwork-less state even after an explicit user refresh.

## Solution

Build resilient cache invalidation and self-healing across the storage, metadata resolution, session restoration, and user interface layers:
1. Make cover art storage dynamically self-healing by ensuring parent directory creation before every write and whenever thumbnail directories are accessed.
2. Correct the self-healing metadata cache evaluation to verify the physical existence of the referenced thumbnail file on disk rather than evaluating synthetic path hashes.
3. Synchronize refresh actions with the track metadata resolver by exposing a cache invalidation seam that clears in-memory negative cache records and probes when an explicit refresh occurs.
4. Harmonize the directory browser refresh lifecycle so that the refresh indicator remains active until initial track metadata self-healing completes, preventing rapid user refresh gestures from canceling ongoing extraction jobs.
5. Guard session resumption by validating that restored track artwork URIs point to physically existing files on disk before publishing them to the media session, avoiding system notification exceptions and triggering transparent self-healing for the active track.

## User Stories

1. As a listener who cleared the app cache in system settings to reclaim storage, I want the application to automatically regenerate and display cover art when I reopen the app, so that my music browsing experience is visually complete without broken images.
2. As a listener viewing a remote directory whose cached thumbnails were cleared, I want pull-to-refresh to fetch fresh cover art from the WebDAV server and save new thumbnails, so that I can manually fix missing artwork.
3. As a listener pulling down to refresh a remote directory, I want the refresh indicator to stay visible until both the directory files and the track metadata have finished updating, so that I clearly know when the refresh operation is genuinely done.
4. As a listener who pulls down to refresh multiple times, I want the app to handle successive refresh gestures gracefully without canceling in-flight metadata extraction into an unrecoverable broken state, so that network requests stay reliable.
5. As a listener whose directory contains shared folder artwork (such as cover.jpg or folder.jpg), I want the self-healing cache logic to correctly detect when the shared folder thumbnail was deleted from disk, so that it is re-downloaded rather than stuck in an endless loop of missing images.
6. As a listener who adds a new cover.jpg to a WebDAV directory and taps refresh in the app, I want the app to bypass its in-memory negative cache and probe the server for the new artwork, so that newly added folder covers appear immediately.
7. As a listener resuming a playback session after clearing the cache, I want the system notification and lock screen player to either display the newly healed cover art or a clean fallback without crashing the system media image loader, so that playback controls remain stable.
8. As a listener with tracks from different remote directories in my playback queue, I want the active track's missing artwork to self-heal seamlessly in the background without causing playback interruptions or audible gaps.
9. As a listener on a slow or high-latency network connection, I want directory browsing to display cached track titles and artists immediately while missing thumbnails regenerate progressively in the background, so that navigation is never blocked by image downloads.
10. As a listener encountering transient network errors while refreshing thumbnails, I want the app to retain existing metadata and avoid overwriting valid track information with empty fields, so that transient connection drops do not destroy library data.

## Implementation Decisions

1. Dynamic Directory Re-creation in Storage:
   - The cover art storage module must ensure that its target storage directory exists before every thumbnail write operation by creating missing parent directories.
   - Accessors checking for thumbnail existence must safely handle cases where the storage directory has been removed by system-level cache clearing.
   - The storage layer must provide a disk validation check for existing thumbnail file paths.

2. Accurate Self-Healing Cache Detection in Metadata Repository:
   - When evaluating which audio files require metadata resolution in non-force-refresh scenarios, the track metadata repository must check whether the recorded thumbnail file path physically exists on disk.
   - If a track entity in the persistent database records a non-null thumbnail path but the file is absent from disk, the track must be marked for self-healing resolution.
   - If a track entity already has a valid thumbnail file existing on disk, it must be skipped during incremental resolution to preserve bandwidth and eliminate redundant WebDAV requests.

3. Refresh Seam and Negative Cache Invalidation in Track Metadata Resolver:
   - The track metadata resolver must expose a cache invalidation operation that purges in-memory folder artwork resolution mappings and negative cache sentinels.
   - When resolving folder artwork, the resolver must verify that cached path references point to existing files on disk before reusing them, treating missing files as cache misses.
   - When a force-refresh is initiated by the caller, the resolver's cache invalidation operation must be invoked prior to network probing.

4. Harmonized Refresh Lifecycle in Directory Browser ViewModel:
   - The directory browser's user interface state must accurately reflect active refresh operations, maintaining the refreshing state until the directory structure is updated and the initial batch of track metadata has been resolved.
   - The background metadata resolution job must be coordinated so that a user-initiated refresh does not abruptly cancel in-progress disk writes or corrupt partial batches.
   - Pull-to-refresh gestures while a resolution is already active must either join the ongoing operation or sequence cleanly without discarding in-flight batch commits.

5. Dead Artwork URI Guard in Playback Session Resumption:
   - During session resumption from persistent storage, track metadata references must be inspected for physical file existence before being forwarded to the underlying audio player engine and system media session.
   - If a restored track's artwork file has been evicted from disk, the session host must clear the invalid URI, present a clean fallback state to system notification receivers, and trigger asynchronous single-track metadata resolution to restore the artwork file without interrupting audio playback.

## Testing Decisions

- Tests must assert on external observable behavior through public module interfaces, not private internal helper methods or storage implementation details.
- High-seam integration tests will simulate cache directory deletion (removing thumbnail files from the filesystem while preserving database entities) and verify:
  1. That observing a directory triggers self-healing resolution and successfully recreates thumbnail files on disk.
  2. That performing an explicit directory refresh re-probes folder artwork and clears negative cache records.
  3. That session restoration handles deleted artwork gracefully without propagating invalid file URIs to media listeners.
- Unit tests for the storage module will verify directory re-creation under simulated cache eviction before write operations.
- Unit tests for the resolver module will verify that negative cache sentinels and cached folder artwork are purged when the cache invalidation seam is invoked.
- Prior art: Existing test patterns in `TrackMetadataRepositoryTest.kt`, `TrackMetadataResolverTest.kt`, `CoverArtStorageTest.kt`, and `DirectoryRepositoryTest.kt`.

## Out of Scope

- Migrating cover artwork storage from the application cache directory to persistent non-cacheable internal storage, which would violate ADR-0002 and fill device flash storage.
- Adding manual user-configurable thumbnail cache size limits in the settings UI.
- Modifying audio stream buffering or WebDAV network authentication mechanisms.

## Further Notes

- This specification adheres to ADR-0002 (pure streaming without persistent audio caching), ADR-0003 (incremental metadata extraction via HTTP Range), ADR-0008 (persistent directory cache with SWR), ADR-0009 (dual-source artwork pipeline and adaptive 512KB range), and ADR-0010 (first-frame service elevation).
