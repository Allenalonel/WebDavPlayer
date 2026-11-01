# 05: Accessibility Semantics Guard and Vector Notification Icon

**What to build:** Assistive accessibility services (e.g. TalkBack) cleanly ignore off-screen tabs without spurious focus traps, and system status bar playback notifications render with a sharp, standard monochrome Material Design 3 vector icon.

**Blocked by:** 01: Decompose Directory Browser Composable Components

**Status:** completed

- [x] In `MainActivity.kt`, guard inactive tab containers with `Modifier.clearAndSetSemantics { }` (or `invisibleToUser()`) so off-screen UI elements are hidden from accessibility focus while retaining their internal scroll/composition state.
- [x] Add `res/drawable/ic_notification_playback.xml` containing a clean, compliant 24dp monochrome vector icon.
- [x] Update `WebDavNotificationProvider` to use `R.drawable.ic_notification_playback` as its small icon instead of `android.R.drawable.ic_media_play`.
- [x] Ensure notifications correctly display across Android 10 through Android 15/16.
- [x] UI and unit tests verify notification construction and navigation accessibility semantics.
