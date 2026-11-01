# 06: Background Playback, System Notification, and Audio Focus Integration

**What to build:** Audio playback continues smoothly when the app moves to the background or the screen locks. A foreground `MediaSessionService` publishes an Android `MediaStyle` notification with track details, album artwork, play/pause, seek slider, and skip actions in the notification shade and lockscreen. Media hardware buttons (Bluetooth headsets, car controls) control playback, and the player respects Android audio focus (pausing during incoming phone calls and ducking volume during system notifications).

**Blocked by:** 04: Full Player View, Seeking, and Queue Controls

**Status:** resolved

- [x] `MediaSessionService` implementation (`WebDavMediaService`) manages background execution with proper foreground service lifecycle and notification.
- [x] Android `MediaStyle` notification displays current track title, artist, album artwork thumbnail, play/pause action, and next/previous controls.
- [x] Lockscreen media widget displays metadata and playback controls on Android 10+ devices.
- [x] Media buttons on wired and Bluetooth headsets (Play, Pause, Toggle, Next, Previous) dispatch corresponding player actions.
- [x] Audio focus requests handle incoming phone calls (pause and resume on call end) and transient loss with ducking (volume reduction during navigation/prompts).
- [x] Runtime permission `POST_NOTIFICATIONS` is requested cleanly on Android 13+ devices.
- [x] Tests verify MediaSession callback routing, audio focus transitions, and service lifecycle management.

## Comments

### Implementation Summary
1. **MediaSession & Hardware Controls**:
   - Implemented `WebDavMediaSessionCallback` handling hardware media button events (`KEYCODE_MEDIA_PLAY`, `KEYCODE_MEDIA_PAUSE`, `KEYCODE_MEDIA_PLAY_PAUSE`, `KEYCODE_HEADSETHOOK`, `KEYCODE_MEDIA_NEXT`, `KEYCODE_MEDIA_PREVIOUS`, `KEYCODE_MEDIA_STOP`).
   - Wired `MediaSession` inside `Media3AudioPlayerEngine` with unique session IDs, session activity launching `MainActivity`, and callback registration.
2. **MediaStyle Notification & Lockscreen Integration**:
   - Implemented `WebDavNotificationProvider` adhering to Media3's `MediaNotification.Provider`.
   - Created low-importance notification channel `webdav_playback_channel` ("WebDAV Playback") without sound spam.
   - Built Android `MediaStyleNotificationHelper.MediaStyle` notification with active `MediaSession`, compact action layout, previous/play-pause/next transport controls, and `VISIBILITY_PUBLIC` for Android 10+ lockscreen widget.
   - Supported album artwork thumbnail decoding from either `artworkData` bytes or `artworkUri` file pointers, plus live metadata update synchronization via `updateTrack`.
3. **Foreground Service Lifecycle**:
   - Implemented `WebDavMediaService` extending `MediaSessionService` with foreground service type `mediaPlayback`.
   - Declared foreground permissions (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`) and service intent-filters in `AndroidManifest.xml`.
   - Automatically starts background service upon playback requests, dismisses upon stop, and safely self-terminates on task removal if playback is stopped.
4. **Audio Focus Handling**:
   - Implemented `AudioFocusHandler` supporting Android O+ `AudioFocusRequest` with `USAGE_MEDIA` and `CONTENT_TYPE_MUSIC`.
   - Handled `AUDIOFOCUS_LOSS_TRANSIENT` (phone calls) by pausing playback and auto-resuming on `AUDIOFOCUS_GAIN`.
   - Handled `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` by ducking volume to 0.2f and restoring to 1.0f on `AUDIOFOCUS_GAIN`.
   - Handled `AUDIOFOCUS_LOSS` by pausing and abandoning focus.
5. **Runtime Notification Permission**:
   - Created `NotificationPermissionHelper` and `RequestNotificationPermissionEffect` in Compose.
   - Cleanly checks and requests `POST_NOTIFICATIONS` on Android 13+ (API 33+) while automatically passing on prior versions.
6. **Testing**:
   - Added `WebDavMediaSessionCallbackTest` (11 tests) verifying all key events and action filtering.
   - Added `WebDavNotificationProviderTest` (6 tests) verifying channel setup, title/artist extraction, action toggling, artwork loading, and MediaNotification wrapping.
   - Added `WebDavMediaServiceTest` (5 tests) verifying service lifecycle, session registration, and command routing.
   - Added `AudioFocusHandlerTest` (5 tests) verifying transient phone call pause/resume, ducking volume reduction/restoration, and permanent loss.
   - Added `NotificationPermissionHelperTest` (3 tests) verifying API level behavior.
   - Extended `Media3AudioPlayerEngineTest` verifying volume setting, live metadata replacement, and audio focus integration.
   - Full test suite: 106 tests passing, 0 failures.
