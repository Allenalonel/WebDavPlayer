# 02: Redesign MiniPlayer with Adaptive Liquid Glass and Swipe Gestures

**What to build:** As a listener, I want the MiniPlayer to render as an adaptive liquid glass pill that blurs underlying content on modern Android versions, allows skipping tracks via horizontal swipe gestures with haptic feedback, and provides direct access to the playback queue via a dedicated button.

**Blocked by:** 01: Capsule Geometry, Touch Clearance, and Gesture Contracts

**Status:** resolved

- [x] On Android 12+ (API 31+), the capsule surface renders with hardware-accelerated RenderEffect blur, translucent background, and a subtle crystalline border highlight.
- [x] On Android 10/11 (API 29/30), the capsule surface gracefully falls back to a high-opacity solid MD3 surface container tint with tonal elevation shadow, avoiding graphics errors and dropped frames.
- [x] Swiping horizontally across the capsule triggers skip to next track (left swipe) or skip to previous track (right swipe) once the threshold is crossed, accompanied by a haptic feedback tick.
- [x] The redundant "Next" button on the right is replaced with a "Playback Queue" icon button (`QueueMusic`) that triggers the queue callback.
- [x] Tapping the card body or dragging upward continues to trigger the full player sheet expansion.
- [x] Title marquee scrolling, micro progress bar along the capsule curve, and network error retry banner are preserved and visually aligned.

## Answer

Implemented adaptive liquid glass material and swipe gesture mechanics in `MiniPlayer.kt`:
1. **Adaptive Liquid Glass Surface**:
   - Android 12+ (API 31+): Utilizes `Modifier.glassBlurEffect` via `RenderEffect.createBlurEffect(20.dp)`, 85% translucent `surfaceContainerHighest` fill, and a 1dp crystalline border (`outlineVariant` at 35% alpha).
   - Android 10/11 (API 29/30): Gracefully falls back to 94% opacity `surfaceContainerHigh` with 6dp tonal elevation and 8dp shadow.
2. **Gesture Mechanics & Tactile Confirmation**:
   - `detectDragGestures` with `resolveMiniPlayerGesture`:
     - Left swipe (`< -SwipeSkipThreshold`): dispatches `skipToNext()` with `HapticFeedbackType.LongPress` tick.
     - Right swipe (`> SwipeSkipThreshold`): dispatches `skipToPrevious()` with `HapticFeedbackType.LongPress` tick.
     - Upward drag (`< VerticalDragThreshold`): invokes `onMiniPlayerClick()` to expand full player.
     - Tapping capsule: triggers `onMiniPlayerClick()`.
3. **Queue Access**:
   - Replaced redundant "Next" icon button with `AppIcons.QueueMusic` button invoking `onOpenQueue`.
All 19 unit tests in `MiniPlayerPresentationTest` pass cleanly.
