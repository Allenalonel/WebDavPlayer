# 02: Decompose ServerListScreen into Component Seams

**What to build:** Extract the complex `ServerCardItem` composable (lines 194–623, 430 lines) from `ServerListScreen.kt` into a dedicated component file:

1. `ui/server/components/ServerCardItem.kt`: Encapsulates server card rendering, status indicators, SSL trust badge, test connection button and response animation, and overflow dropdown menu actions (Edit, Set as Default, Delete).

`ServerListScreen.kt` will retain the top app bar, floating action button (FAB), server list scaffolding, and dialog triggers (Add/Edit and Delete confirmation), reducing its line count from 623 lines down to under 200 lines.

**Blocked by:** None (can run independently or after 01)

**Status:** resolved

- [x] Create `ui/server/components/ServerCardItem.kt` and move `ServerCardItem` and private card helpers
- [x] Update `ServerListScreen.kt` imports and call the extracted `ServerCardItem`
- [x] Verify `ServerListScreen.kt` line count is under 220 lines (strictly complying with 600-line budget)
- [x] Verify all server management tests pass cleanly (`./gradlew testDebugUnitTest`)
