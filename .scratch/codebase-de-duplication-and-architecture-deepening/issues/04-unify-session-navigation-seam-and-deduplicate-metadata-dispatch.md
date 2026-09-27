# 04: Unify Session Navigation Seam and Deduplicate Metadata Dispatch

**What to build:** A clean, unidirectional navigation seam between the directory browser view model and the application session, eliminating dual-write session synchronization and duplicate HTTP Range metadata parsing during playback initiation.

**Blocked by:** 02: Deepen Domain Models for Paths and Progress

**Status:** completed

- [x] `DirectoryBrowserViewModel` consumes active server and directory navigation state unidirectionally from `MusicPlayerAppSession`, eliminating manual double-writing of active servers and directory paths.
- [x] Redundant `trackMetadataRepository.resolveMetadata` trigger inside `MusicPlayerAppSession.playDirectoryTrack` is removed.
- [x] Background metadata resolution for directory files is coordinated strictly upon directory load completion, eliminating redundant network competition and lock contention.
- [x] Directory navigation, server switching, and audio playback startup pass all integration and regression tests without latency spikes or missing metadata.
