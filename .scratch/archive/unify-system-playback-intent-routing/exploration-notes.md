# Exploration Notes: Unify System Playback Intent Routing Through App Session

## Architecture Overview
- **Intent Dispatcher**: `WebDavMediaSessionCallback.kt` handles Android system media button events (`ACTION_MEDIA_BUTTON`, `KeyEvent`) and MediaSession commands (`Player.COMMAND_SEEK_TO_NEXT`, etc.).
- **Media Service**: `WebDavMediaService.kt` is the foreground service handling notification custom actions (`ACTION_PLAY`, `ACTION_PAUSE`, `ACTION_NEXT`, `ACTION_PREVIOUS`).
- **Session Host**: `PlaybackSessionHost.kt` coordinates the media session, service lifecycle, notification updates, and delegates player calls.
- **Audio Engine**: `Media3AudioPlayerEngine.kt` is the pure streaming audio rendering pipeline. Should not have reverse coupling to `MusicPlayerAppSession`.
- **App Session**: `MusicPlayerAppSessionImpl.kt` orchestrates queue, cold restore, clean-exit persistence, watchdog, and playback control state machine.

---

## Ticket 01: Route Media Button and Command Intents to App Session
- **Target Files**:
  - `app/src/main/java/com/webdav/player/data/service/WebDavMediaSessionCallback.kt`
  - `app/src/test/java/com/webdav/player/data/service/WebDavMediaSessionCallbackTest.kt`
- **Key Findings**:
  - `WebDavMediaSessionCallback` currently delegates `KEYCODE_MEDIA_PLAY`, `KEYCODE_MEDIA_PAUSE`, `KEYCODE_MEDIA_STOP`, and `KEYCODE_MEDIA_PLAY_PAUSE` to `playerEngine`.
  - When `appSession` is present, `handleMediaButtonIntent` should dispatch:
    - `KEYCODE_MEDIA_PLAY` -> `appSession?.play()`
    - `KEYCODE_MEDIA_PAUSE` -> `appSession?.pause()`
    - `KEYCODE_MEDIA_PLAY_PAUSE`, `KEYCODE_HEADSETHOOK` -> `appSession?.togglePlayPause()`
    - `KEYCODE_MEDIA_STOP` -> `appSession?.stop()`
    - `KEYCODE_MEDIA_NEXT` -> `appSession?.skipToNext()`
    - `KEYCODE_MEDIA_PREVIOUS` -> `appSession?.skipToPrevious()`
  - In `WebDavMediaSessionCallbackTest`, replace legacy assertions against `fakeEngine.playCount` / `pauseCount` / `stopCount` with assertions against `FakeMusicPlayerAppSession` (or `RecordingAppSession`).
  - Maintain null-safety during transitions so existing constructors and calls remain functional until Ticket 03 cleans them up.

---

## Ticket 02: Route Notification Bar Actions to App Session
- **Target Files**:
  - `app/src/main/java/com/webdav/player/data/service/WebDavMediaService.kt`
  - `app/src/test/java/com/webdav/player/data/service/WebDavMediaServiceTest.kt`
- **Key Findings**:
  - In `WebDavMediaService.onStartCommand()`:
    - Currently:
      ```kotlin
      WebDavNotificationProvider.ACTION_PLAY -> host.play()
      WebDavNotificationProvider.ACTION_PAUSE -> host.pause()
      WebDavNotificationProvider.ACTION_NEXT -> host.mediaSessionCallback?.appSession?.skipToNext()
      WebDavNotificationProvider.ACTION_PREVIOUS -> host.mediaSessionCallback?.appSession?.skipToPrevious()
      ```
    - Routing all actions uniformly through `appSession`:
      ```kotlin
      WebDavNotificationProvider.ACTION_PLAY -> host.mediaSessionCallback?.appSession?.play()
      WebDavNotificationProvider.ACTION_PAUSE -> host.mediaSessionCallback?.appSession?.pause()
      WebDavNotificationProvider.ACTION_NEXT -> host.mediaSessionCallback?.appSession?.skipToNext()
      WebDavNotificationProvider.ACTION_PREVIOUS -> host.mediaSessionCallback?.appSession?.skipToPrevious()
      ```
  - In `WebDavMediaServiceTest`:
    - Add assertions verifying `appSession.play()`, `pause()`, `skipToNext()`, and `skipToPrevious()` are triggered on receiving respective notification actions.

---

## Ticket 03: Eliminate Reverse Engine Coupling and Finalize DI Assembly
- **Target Files**:
  - `app/src/main/java/com/webdav/player/data/service/WebDavMediaSessionCallback.kt`
  - `app/src/main/java/com/webdav/player/data/service/PlaybackSessionHost.kt`
  - `app/src/main/java/com/webdav/player/data/player/Media3AudioPlayerEngine.kt`
  - `app/src/main/java/com/webdav/player/WebDavApplication.kt`
  - Test files:
    - `app/src/test/java/com/webdav/player/data/player/Media3AudioPlayerEngineTest.kt`
    - `app/src/test/java/com/webdav/player/data/service/PlaybackSessionHostTest.kt`
    - `app/src/test/java/com/webdav/player/EndToEndPlaybackPipelineIntegrationTest.kt`
    - `app/src/test/java/com/webdav/player/EndToEndMusicPlayerPipelineTest.kt`
- **Key Findings**:
  - `WebDavMediaSessionCallback` should require non-null `appSession: MusicPlayerAppSession` in primary constructor and remove `playerEngine` dependency.
  - In `PlaybackSessionHost`:
    - Remove `fun setAppSession(session: MusicPlayerAppSession?)`.
    - Provide deterministic one-time binding method, e.g. `bindAppSession(session: MusicPlayerAppSession)` or configure callback during initialization.
  - In `Media3AudioPlayerEngine`:
    - Remove `setAppSession()` and any import of `com.webdav.player.domain.session.MusicPlayerAppSession`.
    - Connect skip actions through callback or functional seam.
  - In `WebDavApplication`:
    - Wire `appSession` cleanly without ghost reverse channels.
  - Verify all unit and integration tests compile and pass.
