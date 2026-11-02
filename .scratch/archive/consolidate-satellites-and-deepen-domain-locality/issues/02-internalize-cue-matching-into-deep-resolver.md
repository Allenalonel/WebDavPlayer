# 02: Internalize Audio Heuristic Matching into Deep CUE Sheet Resolver and Align Test Surface

**What to build:** Complete encapsulation of the CUE sheet domain resolver. The heuristic parent audio matching algorithm is internalized as a private helper inside `DefaultCueSheetResolver.kt`, eliminating the orphaned 66-line static helper object `CueAssociationHelper.kt`. Adhering to the principle that "the interface is the test surface", `CueAssociationHelperTest.kt` is retired, and all heuristic file matching scenarios (exact match, base name match without extension, single-pair fallback, prefix match) are tested directly through the public `CueSheetResolver.resolveDirectoryCueAlbums` domain seam.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] `CueAssociationHelper.kt` is deleted
- [x] Heuristic audio matching logic (`findMatchingAudioFile`) is internalized as private logic inside `DefaultCueSheetResolver.kt`
- [x] `CueAssociationHelperTest.kt` is deleted
- [x] `DefaultCueSheetResolverTest` includes test cases exercising parent audio matching through `resolveDirectoryCueAlbums` across all precedence rules
- [x] `CueSheetResolver` public interface remains unchanged and passes all tests

## Comments
- Internalized heuristic audio matching algorithm (`findMatchingAudioFile`) as private method in `DefaultCueSheetResolver.kt`.
- Removed orphaned static helper `CueAssociationHelper.kt` and retired `CueAssociationHelperTest.kt`.
- Transferred all matching precedence tests (exact match, base name match without extension, single-pair fallback, prefix match, case-insensitivity) into `DefaultCueSheetResolverTest.kt`, verifying behavior through public domain interfaces `resolveDirectoryCueAlbums` and `resolveInitialCueAlbums`.
- All CUE tests passed with 100% success rate.
