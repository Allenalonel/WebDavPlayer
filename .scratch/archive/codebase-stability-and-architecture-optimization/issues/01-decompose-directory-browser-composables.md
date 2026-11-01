# 01: Decompose Directory Browser Composable Components

**What to build:** The user experiences identical directory browsing behavior, animations, and interactions, while the bloated 1,063-line `DirectoryBrowserScreen.kt` is cleanly decomposed into focused, single-responsibility composable components under `ui/browser/components/`.

**Blocked by:** None (can start immediately)

**Status:** completed

- [x] Extract `DirectoryBreadcrumbStrip` and associated chip logic into `ui/browser/components/DirectoryBreadcrumbStrip.kt`.
- [x] Extract `DirectoryItemRow` into `ui/browser/components/DirectoryItemRow.kt`.
- [x] Extract `AudioTrackItemRow` and audio format badges into `ui/browser/components/AudioTrackItemRow.kt`.
- [x] Extract `FileInfoDialog` into `ui/browser/components/FileInfoDialog.kt`.
- [x] Extract empty folder, network error, and unconfigured server states into `ui/browser/components/BrowserStateViews.kt`.
- [x] `DirectoryBrowserScreen.kt` imports and composes the extracted components without regression in layout, scrolling, pull-to-refresh, or action callbacks.
- [x] Existing `DirectoryBrowserPresentationTest` and `DirectoryBrowserViewModelTest` execute with 100% green pass rate.
