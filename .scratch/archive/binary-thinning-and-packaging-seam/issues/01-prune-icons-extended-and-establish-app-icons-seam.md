# 01: Prune Icons Extended and Establish AppIcons Seam

**What to build:**
Eliminate the root cause of the 40+ MB DEX bytecode bloat by removing `androidx.compose.material:material-icons-extended` from `app/build.gradle.kts`. Establish a localized, explicit `AppIcons` catalog under `app/src/main/java/com/webdav/player/ui/theme/AppIcons.kt` containing only the icons actually referenced across the app's composables (such as Play, Pause, SkipNext, SkipPrevious, Repeat, Shuffle, Folder, AudioFile, Settings, Storage, Delete, Edit, MoreVert, etc.). Update all UI imports across `ui/browser`, `ui/player`, `ui/server`, and `ui/common` to point to `AppIcons` or default standard material icons.

**Blocked by:** None (can start immediately)

**Status:** completed

- [x] Remove `implementation("androidx.compose.material:material-icons-extended")` from `app/build.gradle.kts`.
- [x] Create `AppIcons.kt` in `com.webdav.player.ui.theme` exporting the explicit set of ImageVectors required by the application.
- [x] Refactor all UI screen imports in `ui/` to reference `AppIcons` or `androidx.compose.material.icons.Icons.Default.*`.
- [x] Verify that UI renders cleanly without missing glyphs and that debug compilation succeeds.
