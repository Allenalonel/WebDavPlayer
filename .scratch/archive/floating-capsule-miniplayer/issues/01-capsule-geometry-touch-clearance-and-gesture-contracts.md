# 01: Capsule Geometry, Touch Clearance, and Gesture Contracts

**What to build:** Define the physical geometry, touch-target separation, and gesture threshold specifications for the floating capsule mini-player, and establish unit-test coverage validating that interactive elements satisfy Material Design 3 clearance rules to prevent accidental touches.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] Capsule shape is specified as a full pill geometry (`CapsuleCornerRadius >= 28.dp`, `CircleShape`) in component defaults.
- [x] Center-to-center touch clearance between the Play/Pause button and the Queue Music button is verified to be at least 48.dp adhering to MD3 touch guidelines.
- [x] Horizontal swipe threshold constant (`SwipeSkipThreshold`) and vertical drag threshold constant are explicitly declared and verified.
- [x] Existing subtitle formatting logic and retry banner visibility rules in `MiniPlayerPresentationTest` continue to pass cleanly.

## Answer

Established component constants in `MiniPlayerDefaults` within `MiniPlayer.kt`:
- `CapsuleCornerRadius = 28.dp` providing full pill geometry (radius >= 28.dp)
- `QueueMusicButtonSize = 38.dp` (with `SkipNextButtonSize` aligned)
- `ButtonSpacing = 8.dp`, ensuring center-to-center clearance of `(42/2) + 8 + (38/2) = 48.dp`, satisfying MD3 minimum touch target separation
- `SwipeSkipThreshold = 40.dp` and `VerticalDragThreshold = -20f`
All verified via 16 passing unit tests in `MiniPlayerPresentationTest`.
