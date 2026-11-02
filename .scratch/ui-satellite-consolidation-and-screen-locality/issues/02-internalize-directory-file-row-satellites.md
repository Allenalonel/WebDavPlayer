# 02: Internalize Directory File Row Satellites

**What to build:** Consolidation of directory file row rendering and item inspection into a deep, cohesive module. `NonAudioFileRow.kt` and `FileInfoDialog.kt` are folded directly into `AudioTrackItemRow.kt`, establishing a unified ~360-line module. Both helper views are restricted to private visibility within the module, narrowing the exposed public interface to the single primary `AudioTrackItemRow` composable while deleting 2 shallow satellite files.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `NonAudioFileRow.kt` and `FileInfoDialog.kt` are deleted
- [ ] Non-audio fallback row rendering and the file information inspection dialog are integrated into `AudioTrackItemRow.kt` with private visibility
- [ ] `AudioTrackItemRow` public composable signature and external presentation behavior remain 100% identical
- [ ] Internal subtitle formatting helper `resolveAudioSubtitle` is preserved in the package
- [ ] `AudioTrackItemRowTest` passes with zero regressions, verifying subtitle fallback hierarchies
