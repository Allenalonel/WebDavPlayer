# Spec: UI Satellite Consolidation and Screen Locality Deepening

Status: ready-for-agent

## Problem Statement

As a software engineer and AI agent maintaining and extending the WebDavPlayer user interface, several historical code-splitting artifacts in the UI presentation layer cause navigation friction and violate the repository's deep module architecture:

1. **Fragmentation of the Library Search Presentation Layer**:
   The in-library search presentation in the directory browser was historically split into 4 separate small files of roughly 100 to 140 lines each: search results container, search history list, directory match section, and track match item. Three of these four files are single-caller satellites whose only consumer is the search results view. Maintainers seeking to understand, style, or refactor search state transitions, history chips, or search list items must bounce across four disjoint files, fragmenting locality for a single visual feature.

2. **Leaking Satellite Modules in Remote Directory Item Rendering**:
   Within the remote directory browser, standalone file rendering delegates non-audio file fallback rendering to a 103-line satellite file and file inspection dialog rendering to a 134-line satellite file. Neither satellite has any other caller in the entire project. This artificial separation forces readers to inspect three separate files to understand how a single file row item displays its badge, subtitle, and contextual details, obscuring the cohesive responsibility of the directory file item module.

3. **Orphaned Single-Caller Dialog in Server Management**:
   The server management screen exiles its 41-line delete confirmation dialog into an isolated satellite file. The server list screen is a concise 203 lines, meaning this artificial split provides zero depth while introducing cross-file jumping for a simple confirmation dialogue.

4. **Preservation of Core Database Architectural Boundaries**:
   While UI presentation components suffered from artificial satellite splitting, data persistence boundaries between Room database schema entities and query interface DAOs represent a genuine separation of concerns between relational table structure and data access operations. Attempting to indiscriminately merge database layers alongside UI cleanups would blur schema definitions and introduce KSP code generation fragility.

## Solution

1. **Consolidate Library Search Presentation into a Single Deep Search Module**:
   - Fold the search history section, directory match section, and track match item directly into the primary search results view module.
   - The consolidated module will host all search UI states (empty query with LRU history chips, search in progress, zero results fallback, and dual-section directory/track matches) behind a single public interface (`SearchResultsContent`).
   - Delete the three shallow satellite files, concentrating complexity inside the owning search module (~480 lines, well below the 600-line safety ceiling).

2. **Internalize Non-Audio File Display and File Inspection into a Deep File Row Module**:
   - Internalize non-audio file rendering and the file information inspection dialog directly into the directory file row module.
   - Restrict the visibility of the non-audio row and file info dialog as private collaborators within the file row module, narrowing the exposed interface to the single primary file row entry point.
   - Delete the two single-caller satellite files, establishing a deep, cohesive ~360-line module.

3. **Internalize Server Delete Confirmation into the Server List Screen**:
   - Move the 41-line delete confirmation dialog directly into the server list screen as a private composable function.
   - Delete the isolated satellite dialog file, keeping the server list screen compact (~244 lines) and completely self-contained.

4. **Strictly Preserve Room Entity and DAO Seam Separation**:
   - Maintain the architectural separation between Room table entities and DAO interfaces in the local data layer, avoiding unnecessary churn on relational schema definitions.

## User Stories

1. As a developer modifying search result styling, I want directory matches, track matches, and history chips to reside in a single file, so that I can update search layout without bouncing across four files.
2. As a developer debugging search empty state transitions, I want the query routing, loading spinner, and zero-match fallback views to be co-located with the results list, so that search state transitions have high locality.
3. As an AI agent inspecting directory browser components, I want fewer fragmented 100-line files, so that repository exploration requires fewer tool calls and semantic density is maximized.
4. As a developer enhancing file details display, I want the file inspection dialog and subtitle formatting to live inside the directory file row module, so that row inspection logic is self-contained.
5. As a developer maintaining non-audio file representation (such as lyrics and CUE sheets), I want non-audio fallback styling to be encapsulated in the file row module, so that file row rendering rules do not leak across the package.
6. As a maintainer auditing UI code against coding standards, I want the codebase to contain zero single-caller satellite files, so that artificial fragmentation is eliminated.
7. As a developer adjusting server deletion feedback, I want the confirmation dialog and server list screen to be co-located, so that server management flows are unified in one place.
8. As a test writer, I want search track subtitle formatting and audio subtitle resolution to maintain exact public contracts, so that existing unit tests continue to pass with zero regressions.
9. As a release engineer, I want UI module consolidations to cause zero public API churn, so that existing screen navigation coordinators and view models remain untouched.
10. As a performance engineer, I want consolidated Compose modules to maintain identical recomposition boundaries and test tags, so that 60fps rendering and UI automation tests remain stable.
11. As a database maintainer, I want Room entities to remain separated from DAOs, so that database table schemas remain distinct from query access logic.
12. As an audiophile user of the app, I want search interactions, directory browsing, and server configuration to behave with 100% visual and functional fidelity, so that playback and library browsing remain uninterrupted.

## Implementation Decisions

- **Library Search View Consolidation**:
  - Merge the contents of `SearchHistorySection.kt`, `SearchDirectoriesSection.kt`, and `SearchTrackItem.kt` into `SearchResultsViews.kt`.
  - Maintain `SearchResultsViews.kt` as the canonical file name to preserve existing import paths in calling screen modules.
  - The public interface of the module remains the `SearchResultsContent` composable function, with identical parameter signatures.
  - Maintain the existing subtitle resolution helper function with internal visibility to ensure `SearchTrackItemTest` passes without modification.
  - Delete `SearchHistorySection.kt`, `SearchDirectoriesSection.kt`, and `SearchTrackItem.kt`.

- **Directory File Row Module Consolidation**:
  - Merge `NonAudioFileRow.kt` and `FileInfoDialog.kt` into `AudioTrackItemRow.kt`.
  - Scoped visibility: the non-audio row composable and file info dialog composable are marked private within `AudioTrackItemRow.kt`.
  - The public interface remains the `AudioTrackItemRow` composable function.
  - The internal subtitle formatting helper function remains intact to ensure `AudioTrackItemRowTest` passes without modification.
  - Delete `NonAudioFileRow.kt` and `FileInfoDialog.kt`.

- **Server List Screen Consolidation**:
  - Merge `DeleteConfirmationDialog.kt` into `ServerListScreen.kt` as a private composable function.
  - Delete `DeleteConfirmationDialog.kt`.
  - `ServerListScreen.kt` remains well under 250 lines, presenting a single clean screen seam to the navigation coordinator.

- **Persistence Seam Invariant**:
  - Make zero changes to `WebDavServerDao.kt`, `WebDavServerEntity.kt`, `DirectoryCacheDao.kt`, `DirectoryCacheEntity.kt`, `TrackMetadataDao.kt`, `TrackMetadataEntity.kt`, `PlaylistDao.kt`, `PlaylistEntity.kt`, or `PlaylistTrackEntity.kt`.
  - Preserve the architectural boundary between relational schema definitions and data access objects.

## Testing Decisions

- **Test Against Public Seams and Observable Behavior**:
  - Tests will not assert on private internal dialogs or unexported composables; tests will validate observable formatting and user-facing contracts.
  - **Search Subtitle & Track Item Seam**:
    - The existing `SearchTrackItemTest` suite continues to verify subtitle concatenation, virtual track parent file formatting, and fallback artist handling directly against the consolidated search module in the same package.
  - **Audio Track Item Row Seam**:
    - The existing `AudioTrackItemRowTest` suite continues to verify audio subtitle formatting (artist, album, file size fallback, last modified fallback) directly against the consolidated row module in the same package.
  - **Server Management Screen Seam**:
    - Existing server card presentation and management view model tests (`ServerCardPresentationTest`, `ServerManagementViewModelTest`) continue to validate server operations without modification.
  - **Full Regression & Packaging Budget Gate**:
    - Run the entire automated test suite (`./gradlew testDebugUnitTest`) to verify zero functional regressions across all test fixtures.
    - Run `./gradlew verifyReleasePackaging` to ensure binary thinning budgets (APK size <= 7.5 MB, uncompressed classes.dex <= 4.0 MB, single 64-bit arm64-v8a ABI) remain strictly enforced.

## Out of Scope

- Merging or altering Room entities and DAOs in the local data layer.
- Modifying search indexing algorithms or query debouncing in `LibrarySearchRepository`.
- Changing visual design tokens, colors, or typography in Material 3 search and row cards.
- Refactoring `CueAlbumCard.kt` or `DirectoryBreadcrumbStrip.kt`, which are already cohesive independent components.

## Further Notes

- Deleting 6 single-caller satellite files reduces repository file count by 6 while keeping all consolidated files (`SearchResultsViews.kt` ~480 lines, `AudioTrackItemRow.kt` ~360 lines, `ServerListScreen.kt` ~244 lines) well within the 600-line safety threshold set by `CODING_STANDARDS.md`.
