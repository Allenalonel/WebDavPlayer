# 03: Harmonized Directory Refresh Lifecycle and Job Stability

**What to build:**
Provide a smooth and reliable user refresh experience in the directory browser. Pull-to-refresh and top app bar refresh actions keep the visual refresh indicator active until both the remote directory listing and the track metadata self-healing pipeline have completed. Rapid successive refresh gestures no longer cancel active metadata extraction tasks in a destructive race condition.

**Blocked by:** 02: Accurate Disk-File Self-Healing and Resolver Cache Invalidation

**Status:** ready-for-agent

- [ ] Directory browser UI state maintains the `isRefreshing` indicator active until both directory content and initial track metadata extraction complete.
- [ ] Rapid successive pull-to-refresh gestures do not prematurely cancel in-flight metadata extraction or discard partial batch writes.
- [ ] Transient network errors during background refresh do not wipe previously rendered cached directories or cause UI flickering.
- [ ] Unit tests verify that `isRefreshing` stays active throughout the complete refresh lifecycle and successive refresh triggers sequence safely.
