Status: completed

# Spec: Codebase De-duplication and Architecture Deepening

## Problem Statement

When users and developers interact with the WebDavPlayer codebase, architectural friction and redundant logic manifest across streaming, domain modeling, session synchronization, and media presentation:

1. **Dual Streaming Adapters & Leaked MediaItem Assembly**: Although a deep media source adapter was introduced to encapsulate WebDAV streaming, an obsolete data source factory remains actively instantiated and synchronized inside the audio playback engine. Simultaneously, media item metadata assembly is duplicated line-for-line across both the playback engine and the media source adapter. This causes confusing dual-track maintenance, leaky seams, and phantom test surfaces.
2. **Shallow Utility Objects & Fractured Domain Logic**: Crucial path normalization, parent navigation, and breadcrumb derivation logic are stranded in a shallow UI utility object. Consequently, the directory repository reimplements its own private path normalization, creating fragmented path semantics. Similarly, playback duration and progress formatting is extracted into a standalone 10-line utility object, leaving the playback progress domain model anemic and forcing UI components to reach for disparate formatting helpers.
3. **Dual-Write Session State & Redundant Metadata Dispatch**: The directory browser view model and the music player app session suffer from bidirectional state management friction. The view model observes server changes and manually pushes directory path updates into the session. Furthermore, when a user enters a directory and initiates track playback, both the view model and the session host independently trigger full HTTP Range metadata resolution for the same audio files, creating uncoordinated network competition, lock contention, and redundant IO traffic.
4. **Presentation-Bound Audio Quality Rules**: Bitrate parsing from filenames, bitrate estimation from file size and track duration, and quality tier classification (Lossless, High Quality, Standard, Compressed) are implemented in a UI-specific helper. This prevents the full player sheet, docked mini-player, and external system notifications from accessing or displaying consistent audio quality characteristics.

## Solution

A cohesive architectural deepening and de-duplication effort that eliminates shallow modules, consolidates domain behaviors into deep models, and establishes clean, single-direction seams:

1. **Unified Deep Media Source Seam**: Execute the deletion test on the obsolete data source factory. Deepen the WebDAV media source adapter to serve as the sole seam between remote WebDAV audio streaming and the playback engine, encapsulating media item conversion, custom extractors, network connection pooling, and resilient retry policies.
2. **Self-Contained Domain Models for Paths and Progress**: Absorb path normalization, parent hierarchy derivation, ancestor checks, and breadcrumb strip calculation directly into the remote directory domain model. Absorb formatted millisecond presentation directly into the playback progress model. Delete the shallow breadcrumb and time formatter utilities, ensuring repository and presentation layers share uniform domain behaviors.
3. **Single-Direction Session Synchronization & Deduplicated Metadata Dispatch**: Establish the music player app session as the single source of truth for active server and directory navigation state. Coordinate background HTTP Range metadata resolution exclusively during directory loading, removing duplicate triggers during playback queue startup.
4. **First-Class Domain Audio Quality Specifications**: Elevate bitrate estimation, lossless detection, and quality level classification into first-class properties of audio track and remote file models. UI components consume these rich domain properties purely for visual badge rendering without duplicating extraction logic.

## User Stories

1. As a listener streaming audio over WebDAV, I want my playback pipeline to use a clean, unified streaming adapter without residual obsolete configurations, so that streaming is robust and predictable.
2. As a listener playing specialized formats such as WMA or FLAC, I want media source and extractor preparation to occur seamlessly behind a single interface, so that format support never breaks due to mismatched factory calls.
3. As a listener navigating deep directory hierarchies, I want path separators and redundant slashes to be normalized uniformly across the UI and the directory cache, so that I never encounter navigation errors or split cache keys.
4. As a listener tapping items in the directory breadcrumb strip, I want parent navigation and ancestor verification to rely on centralized domain rules, so that clicking any breadcrumb jumps reliably to the exact target directory.
5. As a listener observing playback elapsed time and track duration in the docked mini-player, I want consistent time formatting without UI rendering glitches, so that the time readout is always accurate.
6. As a listener scrubbing playback progress in the full player sheet, I want the progress model to provide its own formatted representations, so that UI components remain lightweight and smooth.
7. As a listener switching between WebDAV servers, I want the active server and last visited directory to update through a single authoritative session flow, so that my browsing context is seamlessly preserved without state desynchronization.
8. As a listener entering a directory with many audio tracks, I want metadata resolution to run efficiently once in the background, so that my network bandwidth and device battery are not wasted on duplicate HTTP Range scans.
9. As a listener immediately tapping play on an audio track upon opening a folder, I want playback to start instantly without waiting for a redundant second metadata resolution pass, so that music starts playing with minimum latency.
10. As a listener browsing high-resolution audio files, I want clear audio quality badges indicating FLAC/WAV lossless quality or MP3/AAC bitrates, so that I know the encoding fidelity of my music.
11. As a listener opening the full player sheet, I want to see audio quality summaries consistent with what was displayed in the directory browser list, so that track information remains coherent across screens.
12. As an engineer maintaining the codebase, I want obsolete data source factories deleted, so that future modifications to streaming logic only touch one deep adapter.
13. As an engineer writing tests, I want path manipulation and progress formatting to be tested directly on the domain models, so that tests are straightforward and free of UI framework overhead.
14. As an engineer diagnosing network performance, I want metadata resolution dispatch to be traceable to a single entry point per directory visit, so that logs are clear and unambiguous.
15. As an engineer inspecting the codebase, I want zero duplicated `buildMediaItem` implementations, so that changes to metadata fields or album art URIs are made in exactly one location.

## Implementation Decisions

1. **Deletion of Obsolete Streaming Factory**:
   - Apply the deletion test to remove the thin data source factory module and its transitional test suite.
   - Deepen the media source adapter module to expose media item and media source factory capabilities directly to callers.
   - Refactor the audio playback engine to consume the media source adapter as its exclusive streaming dependency, removing internal duplicated media item construction and stateful factory calls.

2. **Domain Model Deepening for Navigation and Paths**:
   - Fold path normalization, parent path extraction, ancestor checks, and breadcrumb list generation into the remote directory model and associated domain value types.
   - Update the directory repository to reuse the centralized path normalization rules, eliminating private duplicate sanitization logic.
   - Remove the shallow breadcrumb navigation helper module.

3. **Domain Model Deepening for Playback Progress**:
   - Enrich the playback progress domain model with built-in formatted strings (`formattedCurrentPosition`, `formattedDuration`) adhering to standard `mm:ss` / `hh:mm:ss` display conventions.
   - Update player UI composables (mini-player, full player sheet, queue bottom sheet) to access formatted representations directly from the progress model.
   - Delete the shallow player time formatter utility module.

4. **Session State Consolidation & Metadata Dispatch Single Seam**:
   - Clarify the seam between the directory browser view model and the music player app session: active server changes and directory session state flow unidirectionally from the session.
   - Remove redundant metadata resolution invocations inside directory track playback initiation; metadata resolution is owned exclusively by directory load completion.

5. **Audio Quality as an Intrinsic Audio Track Domain Capability**:
   - Define audio quality levels (Lossless, High Quality, Standard, Compressed) and bitrate estimation algorithms directly on the audio track and remote file domain models.
   - Convert the UI-level audio quality helper into a purely visual rendering composable that maps domain quality levels to Material Design 3 Badge styling.

## Testing Decisions

- **Good Test Criteria**: Tests must exercise external module behaviors across defined seams rather than inspecting private state or asserting against pass-through implementation details.
- **Testing Seam A (Media Source Adapter Seam)**:
  - Test the deep media source adapter directly to verify media item construction, artwork URI binding, format-specific extractor selection (FLAC, WAV, MP3, WMA/ASF), and error handling policies.
  - Prior art: Existing `WebDavMediaSourceAdapterTest` and `WebDavStreamingPlaybackTest`.
- **Testing Seam B (Domain Model Unit Seams)**:
  - Test `RemoteDirectory` path normalization, parent path resolution, and breadcrumb generation across edge cases (root paths, trailing slashes, redundant slashes).
  - Test `PlaybackProgress` formatting across zero, minute-boundary, and multi-hour durations.
  - Test `AudioTrack` and `RemoteFile` audio quality and bitrate calculation against known file formats, file sizes, and metadata durations.
  - Prior art: Existing `BreadcrumbNavigationHelperTest`, `PlayerTimeFormatterTest`, and `AudioQualityBadgeHelperTest`.
- **Testing Seam C (Reactive Session & Directory Flow Seam)**:
  - Verify unidirectional state propagation and single metadata resolution invocation through `MusicPlayerAppSessionTest` and `DirectoryBrowserViewModelTest`.
  - Prior art: Existing `EndToEndMusicPlayerPipelineTest` and `DirectoryBrowserViewModelTest`.

## Out of Scope

- Introducing local disk caching for streamed audio chunks (expressly prohibited by ADR-0002).
- Rewriting the underlying FFmpeg JNI decoder or libavformat bindings (governed by ADR-0001).
- Modifying Room database entity schemas or database migrations (caching schemas remain unchanged).

## Further Notes

- All changes adhere strictly to the vocabulary established in `CONTEXT.md` and the architecture principles in `codebase-design`.
- All modifications will be validated through `./gradlew testDebugUnitTest` prior to final completion.
