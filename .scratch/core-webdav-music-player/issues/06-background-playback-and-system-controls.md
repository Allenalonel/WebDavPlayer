# 06: Background Playback, System Notification, and Audio Focus Integration

**What to build:** Audio playback continues smoothly when the app moves to the background or the screen locks. A foreground `MediaSessionService` publishes an Android `MediaStyle` notification with track details, album artwork, play/pause, seek slider, and skip actions in the notification shade and lockscreen. Media hardware buttons (Bluetooth headsets, car controls) control playback, and the player respects Android audio focus (pausing during incoming phone calls and ducking volume during system notifications).

**Blocked by:** 04: Full Player View, Seeking, and Queue Controls

**Status:** ready-for-agent

- [ ] `MediaSessionService` implementation (`WebDavMediaService`) manages background execution with proper foreground service lifecycle and notification.
- [ ] Android `MediaStyle` notification displays current track title, artist, album artwork thumbnail, play/pause action, and next/previous controls.
- [ ] Lockscreen media widget displays metadata and playback controls on Android 10+ devices.
- [ ] Media buttons on wired and Bluetooth headsets (Play, Pause, Toggle, Next, Previous) dispatch corresponding player actions.
- [ ] Audio focus requests handle incoming phone calls (pause and resume on call end) and transient loss with ducking (volume reduction during navigation/prompts).
- [ ] Runtime permission `POST_NOTIFICATIONS` is requested cleanly on Android 13+ devices.
- [ ] Tests verify MediaSession callback routing, audio focus transitions, and service lifecycle management.
