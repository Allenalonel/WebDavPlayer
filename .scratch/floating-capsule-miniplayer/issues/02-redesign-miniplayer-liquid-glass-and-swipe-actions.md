# 02: Redesign MiniPlayer with Adaptive Liquid Glass and Swipe Gestures

**What to build:** As a listener, I want the MiniPlayer to render as an adaptive liquid glass pill that blurs underlying content on modern Android versions, allows skipping tracks via horizontal swipe gestures with haptic feedback, and provides direct access to the playback queue via a dedicated button.

**Blocked by:** 01: Capsule Geometry, Touch Clearance, and Gesture Contracts

**Status:** ready-for-agent

- [ ] On Android 12+ (API 31+), the capsule surface renders with hardware-accelerated RenderEffect blur, translucent background, and a subtle crystalline border highlight.
- [ ] On Android 10/11 (API 29/30), the capsule surface gracefully falls back to a high-opacity solid MD3 surface container tint with tonal elevation shadow, avoiding graphics errors and dropped frames.
- [ ] Swiping horizontally across the capsule triggers skip to next track (left swipe) or skip to previous track (right swipe) once the threshold is crossed, accompanied by a haptic feedback tick.
- [ ] The redundant "Next" button on the right is replaced with a "Playback Queue" icon button (`QueueMusic`) that triggers the queue callback.
- [ ] Tapping the card body or dragging upward continues to trigger the full player sheet expansion.
- [ ] Title marquee scrolling, micro progress bar along the capsule curve, and network error retry banner are preserved and visually aligned.
