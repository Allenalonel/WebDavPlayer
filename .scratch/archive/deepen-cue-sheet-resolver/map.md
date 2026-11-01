# Map: Deepen CUE Sheet Resolver and Purge Network Leakage

## Notes

- Collapse fragmented CUE sheet processing (`CueTextCache`, `CueAssociationHelper`, `CueParser`) behind a single deep domain module: `CueSheetResolver` (implementation: `DefaultCueSheetResolver`).
- Transient 50-entry thread-safe in-memory LRU cache adhering strictly to ADR-0002 zero-disk retention.
- Eliminate raw `WebDavClient` dependencies and leaks from `DirectoryBrowserViewModel` and `MusicPlayerAppSessionImpl`.
- Eliminate `cueTextProvider` lambda and redundant CUE parsing from `LibrarySearchRepositoryImpl`.

## Decisions-so-far

- [01 resolved] Implemented `CueSheetResolver` in `domain/cue` and `DefaultCueSheetResolver` in `data/cue` with bounded LRU cache (50 items), association matching, and streaming AST parsing. Verified with 14 unit tests in `DefaultCueSheetResolverTest`. (See `.scratch/deepen-cue-sheet-resolver/issues/01-build-deep-cue-sheet-resolver.md`)
- [02 resolved] Migrated callers (`DirectoryBrowserViewModel`, `MusicPlayerAppSessionImpl`, `LibrarySearchRepositoryImpl`) to consume `CueSheetResolver`. Deprecated `CueTextCache` and removed all production usages. Added non-blocking `getCachedVirtualTracks` to prevent main-thread deadlocks. (See `.scratch/deepen-cue-sheet-resolver/issues/02-migrate-callers-and-eliminate-webdavclient-leak.md`)
- [03 resolved] Achieved 100% test pass rate across all suites, passed packaging budget (Release APK 6.67 MB <= 7.5 MB), recorded ADR-0021, and validated live wireless device execution. (See `.scratch/deepen-cue-sheet-resolver/issues/03-end-to-end-verification-and-adr-crystallization.md`)
- [spec accepted] Spec officially accepted and verified: User Stories 1-6 satisfied, no runtime regressions or leaks, release APK verified, device run verified.
