# Spec: Deepen CUE Sheet Resolver and Purge Network Leakage

Status: resolved

## Problem Statement

In WebDavPlayer, support for CUE sheet virtual tracks (ADR-0014) is critical for streaming multi-track FLAC/WAV albums without persistent disk downloads. However, the current CUE sheet architecture is fragmented across three shallow, static utility modules: `CueTextCache` (an unbounded, global static `ConcurrentHashMap`), `CueAssociationHelper` (a static matcher object), and `CueParser` (a static text parser).

Because there is no deep domain seam for CUE handling:
1. `DirectoryBrowserViewModel` (a UI ViewModel) holds a direct dependency on `WebDavClient` (low-level HTTP network client) to manually fetch `.cue` text, consult `CueTextCache`, call `CueAssociationHelper`, and parse tracks.
2. `MusicPlayerAppSessionImpl` (the central audio playback session orchestrator) also holds a direct dependency on `WebDavClient` solely to fetch `.cue` text and manually parse virtual track offsets.
3. `LibrarySearchRepositoryImpl` (the instant search index) is forced to take a `cueTextProvider: (Long, String) -> String?` lambda in its constructor and re-implements file association and track parsing.
4. `CueTextCache` has no memory bounds or eviction policy, creating leak risks and cross-test contamination.

Understanding and modifying CUE processing requires touching three distinct subsystems, violating both locality and the deep module principle.

## Solution

Collapse CUE sheet fetching, association matching, text parsing, and caching behind a single deep domain module: `CueSheetResolver` (with `DefaultCueSheetResolver` implementation).

- `CueSheetResolver` encapsulates an internal, bounded in-memory LRU cache (default: 50 items) for transient CUE text, strictly honoring ADR-0002 (zero persistent disk retention).
- `DefaultCueSheetResolver` accepts `WebDavClient` as its private network adapter.
- Exposes a minimal, highly-leveraged seam:
  - `suspend fun resolveDirectoryCueAlbums(server: WebDavServer, directory: RemoteDirectory, parentDurationsByPath: Map<String, Long> = emptyMap()): List<CueAlbumItem>`
  - `suspend fun resolveVirtualTracks(server: WebDavServer, cuePath: String, parentAudioPath: String, totalDurationMs: Long? = null): List<VirtualTrack>`
  - `fun clearCache()`
- Callers (`DirectoryBrowserViewModel`, `MusicPlayerAppSessionImpl`, and `LibrarySearchRepositoryImpl`) shed low-level CUE coordination and delete their `WebDavClient` dependencies.

## User Stories

1. As a listener browsing a remote WebDAV folder with CUE albums, I want CUE album cards and tracks to load smoothly and accurately without the UI ViewModel managing raw HTTP connections.
2. As a listener playing a virtual CUE track, I want the playback session to effortlessly retrieve the virtual track list and start offsets through a clean domain seam without holding a raw WebDAV network client.
3. As a user searching for songs across cached directories, I want CUE virtual tracks returned instantly without the search repository duplicating CUE parsing and matching heuristics.
4. As an Android developer navigating the codebase, I want all CUE sheet parsing, association heuristics, and transient caching unified in one place, so that improvements to CUE parsing (e.g. character encoding or path separators) take effect everywhere immediately.
5. As a maintainer writing unit tests for `DirectoryBrowserViewModel` and `MusicPlayerAppSession`, I want to mock a single `CueSheetResolver` seam rather than mocking `WebDavClient` HTTP methods and manipulating global static state.
6. As a maintainer, I want transient CUE text cached in a bounded LRU structure with a clean eviction lifecycle, so that memory usage remains strictly bounded and tests run in isolation without state bleed.

## Implementation Decisions

1. **Independent Domain Module at `domain/cue/CueSheetResolver.kt`**:
   - `CueSheetResolver` is established as an independent interface in `domain/cue`.
   - `DefaultCueSheetResolver` implements it in `data/cue/DefaultCueSheetResolver.kt`, accepting `WebDavClient` and an optional `CoroutineDispatcher`.
   - `CueAssociationHelper` and `CueParser` are utilized internally as private implementation details.
   - `CueTextCache` is removed from global static scope and internalized into `DefaultCueSheetResolver` as a thread-safe, bounded LRU cache (maximum 50 entries).

2. **Purge WebDavClient from UI and Session Layers**:
   - In `DirectoryBrowserViewModel`: remove `webDavClient: WebDavClient?` constructor argument. Replace `processDirectoryCueAlbums` with a single call to `cueSheetResolver.resolveDirectoryCueAlbums(...)`.
   - In `MusicPlayerAppSessionImpl`: remove `webDavClient: WebDavClient?` constructor argument. Replace manual CUE text retrieval and parsing with `cueSheetResolver.resolveVirtualTracks(...)`.
   - In `LibrarySearchRepositoryImpl`: replace `cueTextProvider: ((Long, String) -> String?)?` constructor argument with `cueSheetResolver: CueSheetResolver?`.

3. **Dependency Injection & Wiring**:
   - `WebDavApplication` creates a single instance of `DefaultCueSheetResolver(webDavClient)` and supplies it to `DirectoryBrowserViewModelFactory`, `MusicPlayerAppSessionImpl`, and `LibrarySearchRepositoryImpl`.

## Testing Decisions

- **Resolver Test Surface**: `DefaultCueSheetResolverTest` rigorously verifies directory CUE album matching, virtual track resolution, LRU cache behavior, fallback matching, and network error resilience using a fake/mock `WebDavClient`.
- **Caller Test Surfaces**:
  - `DirectoryBrowserViewModelTest` verifies UI state emission when `cueSheetResolver` returns CUE albums.
  - `MusicPlayerAppSessionTest` verifies virtual track playback, queue loading, and seeking via `cueSheetResolver`.
  - `LibrarySearchRepositoryTest` verifies instant search matching of CUE virtual tracks via `cueSheetResolver`.
- **E2E Integration & Regressions**: Execute `./gradlew testDebugUnitTest` to guarantee all existing test suites pass with 100% success.

## Out of Scope

- Modifying the public signatures of `VirtualTimelineEngine`.
- Changing CUE virtual track UI rendering or album card layout.
- Introducing persistent disk caching for CUE files (strictly prohibited under ADR-0002).

## Further Notes

- Aligns with ADR-0002 (zero-disk streaming), ADR-0014 (CUE virtual tracks), and ADR-0020 (instant search & cross-server playlists).
- Adds `Cue Sheet Resolver` to `GLOSSARY.md`.
- Records decision as ADR-0021 upon completion.
