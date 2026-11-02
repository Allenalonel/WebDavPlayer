# 02: Redesign MiniPlayer with MD3 Elevated Solid Island and Swipe Gestures

**What to build:** As a listener, I want the MiniPlayer to render as an elevated solid MD3 pill island with pure surface tone and ambient elevation shadows, allowing skipping tracks via horizontal swipe gestures with haptic feedback, and providing direct access to the playback queue via a dedicated button.

**Blocked by:** 01: Capsule Geometry, Touch Clearance, and Gesture Contracts

**Status:** resolved

- [x] The capsule surface renders with pure solid MD3 `surfaceContainerHigh` tint, 6dp tonal elevation, and 8dp ambient shadow, completely eliminating underlying text collision and bleed-through.
- [x] Across all API levels (API 29–34+), the capsule renders smoothly with 1dp subtle border contour, zero graphics artifacts, and consistent Google Pixel design language.
- [x] Swiping horizontally across the capsule triggers skip to next track (left swipe) or skip to previous track (right swipe) once the threshold is crossed, accompanied by a haptic feedback tick.
- [x] The redundant "Next" button on the right is replaced with a "Playback Queue" icon button (`QueueMusic`) that triggers the queue callback.
- [x] Tapping the card body or dragging upward continues to trigger the full player sheet expansion.
- [x] Title marquee scrolling, inset micro-track progress bar (14dp margin, 3dp height), and network error retry banner are preserved and visually aligned.

## Answer

Implemented MD3 elevated solid island material and swipe gesture mechanics in `MiniPlayer.kt`:
1. **Material Design 3 Elevated Solid Island**:
   - Following real-device validation, converged to 100% solid `surfaceContainerHigh` surface tint with `tonalElevation = 6.dp`, `shadowElevation = 8.dp`, and 1dp `outlineVariant` (25% alpha) border contour.
   - Completely eliminates underlying text collision and bleed-through while preserving true 3D spatial separation above penetrative scrolling content.
   - Upgraded progress bar to an inset floating micro-track (14dp lateral insets, 3dp height, fully rounded pill) aligned with prototype Option 2.
2. **Gesture Mechanics & Tactile Confirmation**:
   - `detectDragGestures` with `resolveMiniPlayerGesture`:
     - Left swipe (`< -SwipeSkipThreshold`): dispatches `skipToNext()` with `HapticFeedbackType.LongPress` tick.
     - Right swipe (`> SwipeSkipThreshold`): dispatches `skipToPrevious()` with `HapticFeedbackType.LongPress` tick.
     - Upward drag (`< VerticalDragThreshold`): invokes `onMiniPlayerClick()` to expand full player.
     - Tapping capsule: triggers `onMiniPlayerClick()`.
3. **Queue Access**:
   - Replaced redundant "Next" icon button with `AppIcons.QueueMusic` button invoking `onOpenQueue`.
All unit tests in `MiniPlayerPresentationTest` pass cleanly.
