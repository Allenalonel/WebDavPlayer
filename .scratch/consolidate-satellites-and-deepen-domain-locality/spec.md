# Spec: Consolidate Satellite Modules, Align Test Surfaces, and Deepen Domain Locality

Status: ready-for-agent

## Problem Statement

As a software engineer and AI agent maintaining and navigating the WebDavPlayer codebase, several architectural friction points and code smells inherited from earlier mechanical line-count splits impede maintainability:

1. **Artificial Satellite File Fragmentation in UI Presentation**:
   The playlist track row component was artificially fragmented across two files: a 159-line parent file and a 292-line satellite file (`*Collaborators.kt`). This explicitly violates the repository's coding standards against single-caller satellite files. Similarly, the playback queue bottom sheet's track row was exiled to a separate shallow file despite being consumed exclusively by the queue sheet. Maintainers seeking to understand or modify playlist and queue row layout, swipe-to-dismiss gestures, or visual thresholds must constantly jump back and forth across multiple shallow files.

2. **Incomplete Domain Encapsulation and Leaking CUE Heuristics**:
   While earlier architectural work established a deep CUE sheet resolver to encapsulate remote network fetching and in-memory LRU caching, the heuristic audio file association algorithm remained stranded in an orphaned 66-line static helper object. Furthermore, unit tests were written directly against this internal static helper rather than exercising file association through the public domain resolver seam, violating the principle that "the interface is the test surface."

3. **Sub-10-Line Micro-Model File Proliferation**:
   The domain model package contains several sub-10-line data classes and enums residing in isolated single-class files (e.g. `LyricLine`, `InactiveReason`, `Breadcrumb`). This carries over obsolete Java-centric single-class-per-file habits into Kotlin, cluttering the file tree and forcing readers and AI navigators to inspect multiple files to grasp tightly-coupled child value types that naturally belong to their parent concepts (`Lyrics`, `PlaylistTrackEntry`, `RemoteDirectory`).

## Solution

1. **Collapse UI Satellite Files into Deep Cohesive Modules**:
   - Fold the playlist track row satellite helpers (gesture thresholds, swipe dismiss handlers, background content, defaults) directly into the primary playlist track row module. The consolidated module remains well within the repository's 600-line safety limit (~430 lines) while achieving high locality.
   - Fold the queue track row and its specific helpers (subtitle formatting, dismiss handlers, leading content) directly into the playback queue bottom sheet module (~360 lines), treating row rendering as a private implementation detail behind the sheet's clean public seam.

2. **Internalize Audio Matching into the Deep CUE Sheet Resolver**:
   - Internalize the heuristic audio file association algorithm as private collaborator logic within the deep CUE sheet resolver.
   - Delete the standalone static helper object, passing the deletion test by concentrating complexity directly inside the owning domain module.
   - Align the test surface strictly to the domain interface: retire the standalone helper test and verify heuristic file matching end-to-end through the resolver's public directory resolution seam.

3. **Consolidate Tightly-Coupled Domain Micro-Models into Owning Concept Modules**:
   - Merge `LyricLine` into `Lyrics`, `InactiveReason` into `PlaylistTrackEntry`, and `Breadcrumb` into `RemoteDirectory`.
   - Preserve exact package names, class names, and immutable semantics, achieving zero caller disruption across the codebase while eliminating file clutter and enhancing semantic density.

## User Stories

1. As a developer modifying playlist swipe-to-dismiss actions, I want gesture physics, dismiss state handlers, and row layout to live in a single cohesive file, so that I can understand and modify row behavior without bouncing across satellite files.
2. As a developer styling the playback queue bottom sheet, I want the track row presentation and swipe-to-delete logic to be co-located with the sheet's list container, so that queue presentation changes have high locality.
3. As a developer debugging CUE sheet album detection, I want heuristic audio file pairing to be encapsulated inside the CUE sheet resolver, so that CUE resolution logic is self-contained behind a single domain seam.
4. As an AI coding agent navigating the domain models, I want closely coupled domain value types (such as lyrics and their individual lines) to be co-located in the same file, so that contextual understanding requires fewer tool calls and file reads.
5. As an engineer reviewing code, I want the repository to strictly adhere to its documented coding standards by eliminating all remaining `*Collaborators.kt` files, so that code smells do not proliferate.
6. As a developer extending playlist track status, I want the track entry model and its inactive reason enum to reside together, so that domain model relationships are immediately obvious.
7. As a developer inspecting remote directory traversal, I want breadcrumb navigation models to be co-located with remote directory structures, so that directory representations are cohesive.
8. As a test writer, I want CUE association heuristics to be verified through the public CUE sheet resolver interface, so that refactoring internal algorithms does not break implementation-locked test assertions.
9. As a developer maintaining queue track subtitle formatting, I want queue duration display rules to be scoped to the queue bottom sheet, so that queue-specific display rules do not leak into general UI packages.
10. As a performance engineer, I want consolidated UI components to maintain identical Compose recomposition boundaries and memory footprints, so that 60fps scrolling performance remains smooth.
11. As a release engineer, I want internal module consolidations to cause zero public API churn, so that existing integration pipelines and packaging budget gates remain intact.
12. As a developer searching for domain concepts in the project index, I want fewer fragmented micro-files, so that search results surface meaningful high-density modules.
13. As a maintainer auditing test surfaces, I want unit tests to target public module interfaces, so that tests validate real observable behavior rather than private implementation details.
14. As an audiophile user of the app, I want playlist swipe gestures, CUE album recognition, and queue interactions to continue functioning with 100% fidelity, so that playback and playlist management remain rock-solid.

## Implementation Decisions

- **Playlist Track Row Module Consolidation**:
  - Merge the contents of the satellite collaborator file into the owning playlist track row file.
  - The consolidated module will host the public composable entry point, row layout defaults, swipe dismissal handlers, leading indicator composition, and background rendering.
  - The module's external interface remains strictly narrow and unchanged, presenting only the single public row composable.
  - Delete the satellite collaborator file completely.

- **Playback Queue Bottom Sheet Module Consolidation**:
  - Move the queue track item row composable, subtitle resolution logic, leading icon layout, and dismiss handler into the playback queue bottom sheet file.
  - Treat queue item rendering as an internal private implementation detail of the queue bottom sheet rather than an exposed independent adapter.
  - Delete the standalone queue track row file completely.

- **CUE Sheet Resolver Deepening and Helper Internalization**:
  - Incorporate the heuristic audio matching algorithm directly into the production CUE sheet resolver implementation as a private helper method.
  - The public interface of the CUE sheet resolver domain seam remains completely unchanged.
  - Delete the orphaned static helper object file, successfully passing the deletion test as complexity concentrates within the deep resolver.
  - Retain full matching precedence: referenced CUE FILE name, base name match without extension, same base name between CUE and audio, single-pair fallback, and prefix match.

- **Domain Micro-Model Co-Location**:
  - Co-locate the 10-line `LyricLine` data class at the bottom of the `Lyrics` file in the same package.
  - Co-locate the 8-line `InactiveReason` enum at the bottom of the `PlaylistTrackEntry` file in the same package.
  - Co-locate the 8-line `Breadcrumb` data class at the bottom of the `RemoteDirectory` file in the same package.
  - Delete the three separate micro-files.
  - Zero import statements in calling files need to change because package structures and symbol declarations remain identical.

## Testing Decisions

- **Test Against Public Seams and Observable Behavior**:
  - Tests must never assert on internal file facades, private functions, or reflection on Kotlin top-level files.
  - **Playlist Track Row Seam**:
    - The existing playlist track row test suite continues to exercise dismiss thresholds, swipe direction handling, and default dimension calculations through the consolidated module in the same package.
  - **CUE Resolver Seam as Test Surface**:
    - Delete the obsolete helper-specific test suite.
    - Consolidate all heuristic matching test cases (exact match, base name match, single-pair directory fallback, prefix match) into the CUE sheet resolver test suite, verifying them by invoking the public `resolveDirectoryCueAlbums` interface.
  - **Playback Queue Bottom Sheet Seam**:
    - The queue bottom sheet test suite continues to verify subtitle formatting (active progress vs inactive fallback) and swipe dismissal handling against the consolidated module.
  - **Full Regression & Packaging Verification**:
    - Run the entire test suite (`./gradlew testDebugUnitTest`) to ensure zero regressions across all test fixtures.
    - Run `./gradlew verifyReleasePackaging` to ensure APK size and DEX budgets remain strictly compliant.

## Out of Scope

- Introducing new UI animations or altering visual designs of playlist or queue rows.
- Altering the CUE parsing AST state machine (`CueParser.kt` remains untouched).
- Changing SQLite database entities or Room DAOs.
- Consolidating independent top-level domain enums like `PlaybackMode` or `NetworkStatus`.

## Further Notes

- By deleting 5 production files and 1 test file, the repository file count decreases by 6, eliminating shallow clutter while keeping every consolidated file comfortably below the 600-line safety ceiling documented in `CODING_STANDARDS.md`.
