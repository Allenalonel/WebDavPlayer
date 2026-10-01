# 04: Dead Artwork URI Protection for Media Playback Session

**What to build:**
Prevent lock screen, notification shade, and MediaSession crashes when cold-starting the app after cache clearing. During session resumption, any saved track artwork URIs that no longer exist on disk are safely stripped before being sent to the system media session, presenting a clean fallback icon and initiating background track self-healing without playback interruptions or `FileNotFoundException` log spam.

**Blocked by:** 02: Accurate Disk-File Self-Healing and Resolver Cache Invalidation

**Status:** completed

- [x] Playback session restoration validates the physical existence of track artwork files on disk before setting artwork URIs on media items.
- [x] Missing artwork files on disk are safely replaced with clean fallbacks for system media session and notification controls.
- [x] Restored tracks with missing artwork files trigger non-disruptive background metadata enrichment once the artwork is re-resolved.
- [x] Unit tests verify that session restoration with missing artwork files produces no unhandled exceptions and triggers background self-healing.
