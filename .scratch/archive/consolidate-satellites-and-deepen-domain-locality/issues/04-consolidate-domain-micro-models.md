# 04: Consolidate Domain Micro-Models into Owning Concept Files

**What to build:** Co-location of tightly-coupled child value types into their parent domain concept files, reducing file proliferation and improving semantic locality. `LyricLine` is moved into `Lyrics.kt`, `InactiveReason` is moved into `PlaylistTrackEntry.kt`, and `Breadcrumb` is moved into `RemoteDirectory.kt`. The three separate sub-10-line micro-files are deleted. All packages, symbols, and immutable semantics remain completely identical, causing zero disruption to external callers.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] `LyricLine.kt`, `InactiveReason.kt`, and `Breadcrumb.kt` are deleted
- [x] `LyricLine` is co-located in `Lyrics.kt` (~45 lines)
- [x] `InactiveReason` is co-located in `PlaylistTrackEntry.kt` (~85 lines)
- [x] `Breadcrumb` is co-located in `RemoteDirectory.kt` (~85 lines)
- [x] All call sites compile without modification; all existing unit tests in `domain.model` pass without regressions

## Comments
- Co-located `LyricLine` into `Lyrics.kt`.
- Co-located `InactiveReason` into `PlaylistTrackEntry.kt`.
- Co-located `Breadcrumb` into `RemoteDirectory.kt`.
- Deleted isolated sub-10-line micro-files `LyricLine.kt`, `InactiveReason.kt`, and `Breadcrumb.kt`.
- All callers compiled without changes; `domain.model` unit tests passed with 100% success rate.
