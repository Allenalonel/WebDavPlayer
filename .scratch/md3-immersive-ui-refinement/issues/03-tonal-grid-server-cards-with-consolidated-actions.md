# 03: Tonal Grid Server Cards with Consolidated Actions

**What to build:** Modernize WebDAV server cards by replacing arbitrary hardcoded offsets with a flexible, clean grid alignment. Eliminate harsh static borders in favor of Material 3 tonal elevation and subtle surface container tinting. Consolidate trailing action buttons into a primary connection test trigger and an overflow menu, preventing horizontal button compression on narrow devices.

**Blocked by:** 01 (Immersive Edge-to-Edge System Chrome and Top Bar Harmonization)

**Status:** resolved

- [x] The server card layout employs natural column alignment for metadata tags, completely removing the hardcoded 52dp start offset.
- [x] Active servers display with `surfaceContainerHigh` tonal container styling and an elegant primary tone highlight, replacing the thick 1.5dp border.
- [x] Inactive servers display with clean `surfaceContainerLow` tonal elevation.
- [x] Trailing actions are consolidated into a dedicated connection test button and a more-options overflow menu (edit, delete).
- [x] Connection test feedback (loading indicator, success chip, or failure code) displays clearly without wrapping or compressing action buttons.
