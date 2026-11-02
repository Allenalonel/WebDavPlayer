# 03: Fold Delete Confirmation Dialog into Server List Screen

**What to build:** Co-location of server deletion confirmation directly within the server list screen module. `DeleteConfirmationDialog.kt` is folded into `ServerListScreen.kt` as a private composable function, unifying server card rendering, add/edit routing, and delete state handling into a single self-contained ~244-line screen module while deleting 1 shallow satellite file.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `DeleteConfirmationDialog.kt` is deleted
- [ ] Delete confirmation dialogue is integrated into `ServerListScreen.kt` as a private composable function
- [ ] `ServerListScreen` public signature and presentation behavior remain 100% identical
- [ ] Existing server management tests (`ServerCardPresentationTest`, `ServerManagementViewModelTest`) pass without modification
