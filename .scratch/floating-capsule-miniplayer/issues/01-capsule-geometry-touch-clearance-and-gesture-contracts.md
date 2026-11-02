# 01: Capsule Geometry, Touch Clearance, and Gesture Contracts

**What to build:** Define the physical geometry, touch-target separation, and gesture threshold specifications for the floating capsule mini-player, and establish unit-test coverage validating that interactive elements satisfy Material Design 3 clearance rules to prevent accidental touches.

**Blocked by:** None (can start immediately)

**Status:** claimed

- [ ] Capsule shape is specified as a full pill geometry (`CapsuleCornerRadius >= 28.dp`, `CircleShape`) in component defaults.
- [ ] Center-to-center touch clearance between the Play/Pause button and the Queue Music button is verified to be at least 48.dp adhering to MD3 touch guidelines.
- [ ] Horizontal swipe threshold constant (`SwipeSkipThreshold`) and vertical drag threshold constant are explicitly declared and verified.
- [ ] Existing subtitle formatting logic and retry banner visibility rules in `MiniPlayerPresentationTest` continue to pass cleanly.
