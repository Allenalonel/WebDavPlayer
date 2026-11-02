# 05: Update ADR-0007 and Run Full Verification Gate

**What to build:** Record the architectural evolution from bottom-bar embedding to independent floating overlay in `docs/adr/0007-scaffold-hoisted-navigation-and-floating-miniplayer.md`, and execute the full test and verification suite to ensure all unit tests and builds pass cleanly.

**Blocked by:** 04: Penetrative Edge-to-Edge Scrolling and Static Content Padding

**Status:** ready-for-agent

- [ ] `docs/adr/0007-scaffold-hoisted-navigation-and-floating-miniplayer.md` is updated in-place to document the decoupled floating capsule overlay, penetrative list insets, and API 29/31 rendering fallback strategy.
- [ ] Targeted module unit tests (`MiniPlayerPresentationTest` and related UI tests) pass cleanly.
- [ ] Full project debug unit tests (`./gradlew testDebugUnitTest`) pass with zero regressions.
