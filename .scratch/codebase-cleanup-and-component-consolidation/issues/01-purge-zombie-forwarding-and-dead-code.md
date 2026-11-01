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

**Status:** open

- [ ] Remove 4 obsolete `*ViewModelFactory.kt` forwarding files
- [ ] Remove deprecated `data/cue/CueTextCache.kt`
- [ ] Remove `ui/browser/components/AudioTrackItemRowCompat.kt`
- [ ] Update `AudioTrackItemRowTest.kt` to align with real component surface
- [ ] Verify clean compilation and 100% test pass rate
