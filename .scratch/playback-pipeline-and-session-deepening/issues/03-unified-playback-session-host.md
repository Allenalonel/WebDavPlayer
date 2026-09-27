# 03: Unified Playback Session Host and Resilient Background Service

**What to build:** A self-contained playback session host that unifies the Android background media service, audio player engine lifecycle, media notification provider, and system audio focus coordination. The background service assumes full internal ownership of the media session and player engine. External UI and domain callers interact solely through the standard audio player engine interface, eliminating static service launcher methods and unsafe reverse singleton downcasting. The service proactively coordinates foreground elevation with audio playback states (playing, paused, buffering, idle, error), ensuring Android's ActivityManager does not forcibly terminate background playback with "App Idle" shutdowns.

**Blocked by:** 01: Deep WebDAV MediaSource Adapter and Resilient Streaming

**Status:** ready-for-agent

## Acceptance criteria

- [ ] The background playback service acts as the cohesive host for the audio player engine and Media3 MediaSession, eliminating reverse application singleton downcasts.
- [ ] Callers control playback exclusively through the clean audio player engine interface without manually invoking foreground service start or stop intents.
- [ ] Foreground service status and ongoing system media notifications are autonomously elevated and managed in direct synchronization with playback state transitions.
- [ ] When playback enters an error state or is paused in the background, the service gracefully synchronizes its foreground state and notification to prevent operating system App Idle terminations.
- [ ] Android system audio focus transitions (transient ducking for navigation prompts, pauses for incoming calls, and volume restoration on completion) are negotiated seamlessly within the host.
- [ ] Hardware media buttons (play, pause, next, previous) on headphones and Bluetooth devices dispatch directly to the media session host reliably.
- [ ] Unit and component tests verify service lifecycle transitions, notification dispatch, and audio focus handling under Robolectric without leaking background state.
