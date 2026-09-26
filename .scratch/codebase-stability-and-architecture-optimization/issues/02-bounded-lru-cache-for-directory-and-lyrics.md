# 02: Bounded LRU Cache for Directory Snapshots and Synchronized Lyrics

**What to build:** The listener experiences responsive 0ms directory navigation and fluid synced lyrics display while in-memory caches are bounded by thread-safe LRU eviction limits, preventing memory bloat during prolonged browsing and playback sessions across vast WebDAV hierarchies.

**Blocked by:** None (can start immediately)

**Status:** completed

- [x] `DirectoryRepositoryImpl` replaces the unbounded `ConcurrentHashMap` with a synchronized LRU memory cache with a max capacity of 50 directories.
- [x] When a directory is evicted from the L1 memory cache, querying it transparently falls back to the L2 Room database cache (`DirectoryCacheDao`), returning instantly without requiring a network call.
- [x] `LyricsRepositoryImpl` replaces its unbounded `ConcurrentHashMap` with a thread-safe LRU cache with a max capacity of 100 entries.
- [x] `clearCache()` and `clearMemoryCache()` cleanly evict all LRU in-memory entries.
- [x] Unit tests in `DirectoryRepositoryTest` and `LyricsRepositoryTest` verify that inserting entries past the maximum capacity evicts the least-recently-accessed entry while retaining recent ones.
- [x] Full regression suite passes without degradation.
