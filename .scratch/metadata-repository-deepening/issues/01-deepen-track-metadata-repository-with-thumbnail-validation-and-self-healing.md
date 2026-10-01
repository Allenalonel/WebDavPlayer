# 01: Deepen Track Metadata Repository with Thumbnail Validation and Self-Healing

**What to build:**
Deepen `TrackMetadataRepositoryImpl` to absorb `CoverArtStorage` validation and asynchronous self-healing behind the repository seam. Every metadata query (`getCachedMetadata`, `getMetadataFlow`, `getMetadataForPathsFlow`, `getAllMetadataFlow`) must ensure that non-null thumbnail paths correspond to physical files on disk. When a file is missing, the repository returns `coverThumbnailPath = null` and triggers background self-healing to re-fetch the thumbnail and update Room.

**Blocked by:** None (can start immediately)

**Status:** pending

- [ ] All Room entity-to-domain mapping in `TrackMetadataRepositoryImpl` validates `coverThumbnailPath` against `CoverArtStorage.isValidThumbnailFile`.
- [ ] If a thumbnail file is missing from disk, the emitted/returned domain metadata sets `coverThumbnailPath = null`.
- [ ] When missing thumbnail paths are detected, the repository launches an asynchronous background self-healing job to re-extract the thumbnail via `DefaultTrackMetadataResolver` and persist it.
- [ ] Unit tests in `TrackMetadataRepositoryTest` verify that queries return clean metadata and trigger background recovery when physical thumbnail files are deleted.
