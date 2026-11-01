# 01: Purge Zombie Forwarding Files and Dead Compatibility Code

**What to build:**
Physically remove 6 zero-production-usage files left over as transitional scaffolding from earlier refactorings:
1. **Obsolete ViewModel Factories**:
   - `ui/browser/DirectoryBrowserViewModelFactory.kt`
   - `ui/server/ServerManagementViewModelFactory.kt`
   - `ui/playlist/PlaylistListViewModelFactory.kt`
   - `ui/playlist/PlaylistDetailViewModelFactory.kt`
   *(All viewmodel instantiations in MainActivity, screens, and tests are now 100% routed through `AppViewModelFactory` or `AppContainer`)*.
2. **Deprecated Static Cache**:
   - `data/cue/CueTextCache.kt` (superseded by `CueSheetResolver` bounded LRU cache since ADR-0021, zero usages in the repository).
3. **Dead Compatibility Aliases**:
   - `ui/browser/components/AudioTrackItemRowCompat.kt` (105 lines of dead aliases: `FileItemRow`, `AudioBadge`, `LyricBadge`, `OtherBadge`).
4. **Test Alignment**:
   - Update `AudioTrackItemRowTest.kt` to prune obsolete reflection assertions verifying bytecode presence of `FileItemRow` and old aliases, focusing test surface strictly on real UI behavior.

**Blocked by:** None

**Status:** resolved

- [x] Remove 4 obsolete `*ViewModelFactory.kt` forwarding files
- [x] Remove deprecated `data/cue/CueTextCache.kt`
- [x] Remove `ui/browser/components/AudioTrackItemRowCompat.kt`
- [x] Update `AudioTrackItemRowTest.kt` to align with real component surface
- [x] Verify clean compilation and 100% test pass rate

## Answer

### Changes Made
1. **Purged Obsolete ViewModel Factories**:
   - Physically deleted `DirectoryBrowserViewModelFactory.kt`, `ServerManagementViewModelFactory.kt`, `PlaylistListViewModelFactory.kt`, and `PlaylistDetailViewModelFactory.kt`. All ViewModel creation across the codebase now flows exclusively through `AppViewModelFactory` or `AppContainer`.
2. **Purged Deprecated Static Cache**:
   - Physically deleted `CueTextCache.kt`, completing the migration to `CueSheetResolver`'s bounded LRU cache (ADR-0021).
3. **Purged Compatibility Aliases & Aligned Imports**:
   - Physically deleted `AudioTrackItemRowCompat.kt` (removing dead aliases `FileItemRow`, `AudioBadge`, `LyricBadge`, `OtherBadge`).
   - Added direct imports for `EqualizerTrackIndicator` and `AudioQualityBadgePill` from `com.webdav.player.ui.common` in `CueAlbumCard.kt`.
4. **Test Alignment**:
   - Pruned obsolete reflection assertions in `AudioTrackItemRowTest.kt` that inspected bytecode aliases from `AudioTrackItemRowCompat`, focusing test surface strictly on real UI behaviors (`resolveAudioSubtitle`, `resolveBadge`, and file budget compliance).
5. **Verification**:
   - Ran `./gradlew testDebugUnitTest` verifying 100% test pass rate (998 passing tests, 0 failures).
