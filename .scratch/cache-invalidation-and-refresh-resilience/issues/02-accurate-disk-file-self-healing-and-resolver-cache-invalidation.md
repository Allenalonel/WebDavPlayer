# 02: Accurate Disk-File Self-Healing and Resolver Cache Invalidation

**What to build:**
Correct the self-healing metadata cache evaluation and resolver cache invalidation so that missing cover files on disk are detected with 100% precision. When an audio track has a cached metadata record in the database whose referenced thumbnail file is absent from disk, the repository immediately marks it for re-resolution. When resolving or on explicit refresh, the resolver clears in-memory negative cache records and verifies physical disk existence rather than reusing stale dead paths.

**Blocked by:** 01: Resilient Cover Art Storage and Directory Recovery

**Status:** ready-for-agent

- [ ] Metadata repository checks the physical existence of the referenced thumbnail file path rather than synthesizing path hashes.
- [ ] Tracks with missing thumbnail files on disk are scheduled for background self-healing resolution during directory browsing.
- [ ] Tracks with existing thumbnail files on disk are skipped during incremental resolution, preserving network bandwidth.
- [ ] Track metadata resolver exposes a cache clearing seam that purges in-memory folder artwork resolution mappings and negative cache sentinels.
- [ ] User-initiated force-refresh triggers cache invalidation on the resolver prior to network probing.
- [ ] Unit tests verify that missing physical files trigger self-healing while existing files are preserved, and cache invalidation clears negative cache sentinels.
