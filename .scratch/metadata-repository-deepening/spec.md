# Metadata Repository Deepening Specification

## Problem Statement

In the current codebase, the `TrackMetadataRepository` interface is too shallow, merely acting as a thin pass-through over the Room DAO and network resolver without encapsulating infrastructure invariants:
1. **Infrastructure Leakage across Domain Seam**: The repository returns database entities containing raw disk file paths (`coverThumbnailPath`) without guaranteeing physical file validity. When thumbnail files are cleared from the cache directory by the OS or user, the database retains stale file paths. Consequently, caller modules like `MusicPlayerAppSessionImpl` are forced to inject `CoverArtStorage`, call `isValidThumbnailFile` in 6 distinct locations, maintain a duplicate in-memory cache (`latestMetadataCache`), and orchestrate ad-hoc background self-healing coroutines.
2. **Hypothetical Public Seam (One Adapter)**: `TrackMetadataResolver` is exposed as a top-level domain interface alongside `TrackMetadataRepository`. However, there is only one concrete implementation (`DefaultTrackMetadataResolver`), violating the design principle that "one adapter means a hypothetical seam, two adapters means a real one." As a result, upstream callers like `LyricsRepositoryImpl` are forced to inject both abstractions and execute branching logic (`when { repository != null -> ... resolver != null -> ... }`).
3. **Session Layer Accidental Complexity**: The session module `MusicPlayerAppSessionImpl` is cluttered with 150+ lines of thumbnail file verification, track sanitization, duplicate in-memory map management, and manual `RemoteFile` reconstruction.

## Solution

Deepen `TrackMetadataRepository` to form an authoritative, resilient, and encapsulated metadata module:
1. **Absorb Thumbnail Validation and Self-Healing behind the Repository Seam**:
   - `TrackMetadataRepositoryImpl` encapsulates `CoverArtStorage`.
   - Every metadata query (`getCachedMetadata`, `getMetadataFlow`, `getMetadataForPathsFlow`, `getAllMetadataFlow`) guarantees that any returned `coverThumbnailPath` points to a verified physical file on disk.
   - If a database record has a thumbnail path pointing to an evicted file, the repository synchronously returns safe metadata with `coverThumbnailPath = null` (preventing broken image loading and `ENOENT` exceptions) and transparently enqueues background self-healing to re-extract and persist the thumbnail, broadcasting updates through Room flows.
2. **Internalize `TrackMetadataResolver`**:
   - Retire the public `TrackMetadataResolver` domain interface.
   - Make `DefaultTrackMetadataResolver` an `internal` class consumed exclusively by `TrackMetadataRepositoryImpl`.
   - Purify `LyricsRepositoryImpl` to depend strictly on `TrackMetadataRepository`, eliminating double injection and branch code.
3. **Purify Domain Session**:
   - Remove `CoverArtStorage` from `MusicPlayerAppSessionImpl` constructor and properties.
   - Delete `isValidThumbnailFile`, `sanitizeTrackWithCoverValidation`, and `latestMetadataCache`.
   - The session layer trusts `TrackMetadataRepository` completely.

## User Stories

1. As a listener whose device cache was cleared, when I resume playback, I want the music player session to load cleanly without crashing or showing broken artwork, and I want the cover to restore itself automatically in the background.
2. As a developer maintaining `MusicPlayerAppSessionImpl`, I want the domain session layer to be completely free of disk I/O checks and thumbnail file existence validation, so that the session focus remains purely on playback queue state and user commands.
3. As a developer integrating lyrics resolution, I want to inject only `TrackMetadataRepository` without having to decide between `TrackMetadataResolver` and `TrackMetadataRepository`, so that the interface leverage is high and call sites are simple.
4. As a listener browsing directories, I want all metadata emitted by the repository to guarantee valid thumbnail paths, so that the UI never flashes missing image icons.
5. As a developer writing tests for metadata and session, I want tests at the deepened repository interface to cover both database retrieval and disk validation, eliminating the need for two-layer mock setups.

## Implementation Decisions

1. **Deep Repository Seam with Physical Thumbnail Verification**:
   - In `TrackMetadataRepositoryImpl`, all entities retrieved from `TrackMetadataDao` are mapped through an internal sanitization function that verifies `coverThumbnailPath` against `CoverArtStorage.isValidThumbnailFile`.
   - If an entity's thumbnail is missing from disk:
     - The returned domain model has `coverThumbnailPath = null`.
     - An asynchronous self-healing job is launched within `TrackMetadataRepositoryImpl`'s coroutine scope to re-resolve the track via the internal `DefaultTrackMetadataResolver` and update Room.
2. **Internalize Resolver**:
   - Delete `app/src/main/java/com/webdav/player/domain/metadata/TrackMetadataResolver.kt`.
   - Mark `DefaultTrackMetadataResolver` as `internal`.
   - In `LyricsRepositoryImpl`, remove `trackMetadataResolver` from constructor and replace the `when` branching logic with a single call to `trackMetadataRepository.getCachedMetadata` / `resolveSingleTrackMetadata`.
   - In `WebDavApplication`, eliminate direct instantiation and exposure of `TrackMetadataResolver`.
3. **Purify Session Layer**:
   - Strip `CoverArtStorage` parameter and `latestMetadataCache` field from `MusicPlayerAppSessionImpl`.
   - Remove `sanitizeTrackWithCoverValidation` and file existence guards from session restore and queue playback methods.
   - Rely solely on `trackMetadataRepository` for metadata enrichment and flow observation.

## Testing Decisions

- Test coverage on `TrackMetadataRepositoryTest`:
  - Verify that when Room holds a record whose thumbnail file was deleted from disk, `getCachedMetadata` and `getMetadataFlow` return `coverThumbnailPath == null` and launch background self-healing.
  - Verify that when the thumbnail file exists, the path is returned untouched.
  - Verify that single track and batch resolution correctly persist and validate thumbnails.
- Test coverage on `LyricsRepositoryTest`:
  - Verify lyrics extraction using solely `TrackMetadataRepository`.
- Test coverage on `MusicPlayerAppSessionTest` and `PlaybackSessionResumptionTest`:
  - Verify session resumption and track playback without passing `CoverArtStorage`.
- Ensure all existing end-to-end regression tests (`EndToEndCacheResilienceIntegrationTest`, `EndToEndPlaybackPipelineIntegrationTest`) pass completely.

## Out of Scope

- Changing HTTP Range byte sizes (512KB remains standard per ADR-0009).
- Modifying Room database schema or migration scripts (table structures remain unchanged).
- Refactoring `DirectoryRepository` or `Media3AudioPlayerEngine` (reserved for separate candidate flows).

## Further Notes

- Aligns directly with ADR-0009 (dual-source artwork), ADR-0010 (non-disruptive enrichment), and ADR-0011 (deep metadata repository and internalized resolver).
