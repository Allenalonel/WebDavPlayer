# 01: Resilient Cover Art Storage and Directory Recovery

**What to build:**
Enable cover art storage to transparently survive cache purges by Android system settings or OS storage cleanup. When thumbnail write operations occur after the cache directory has been deleted, the storage layer must automatically ensure parent directory creation, preventing `FileNotFoundException (ENOENT)` and silent write failures.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Thumbnail saving operations automatically recreate missing parent storage directories before writing image files.
- [ ] Querying thumbnail existence safely handles missing storage directories without throwing unhandled exceptions.
- [ ] Storage disk quota pruning and cache inspection logic gracefully handle non-existent storage directories.
- [ ] Unit tests verify that deleting the storage directory followed by a save operation successfully recreates the directory and writes the thumbnail.
