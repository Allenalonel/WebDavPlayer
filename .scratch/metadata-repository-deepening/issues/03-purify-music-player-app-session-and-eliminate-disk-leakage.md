# 03: Purify Music Player App Session and Eliminate Disk Leakage

**What to build:**
Remove `CoverArtStorage` from `MusicPlayerAppSessionImpl`, eliminate `isValidThumbnailFile`, `sanitizeTrackWithCoverValidation`, and the duplicate `latestMetadataCache`, and remove manual `RemoteFile` self-healing loops from the session layer.

**Blocked by:** 01-deepen-track-metadata-repository-with-thumbnail-validation-and-self-healing

**Status:** pending

- [ ] Remove `CoverArtStorage` parameter from `MusicPlayerAppSessionImpl` constructor and factory.
- [ ] Remove `isValidThumbnailFile` and `sanitizeTrackWithCoverValidation` methods.
- [ ] Delete `latestMetadataCache` and rely solely on `TrackMetadataRepository` queries and flows.
- [ ] Clean up `restoreSession` and track playback methods to remove manual disk guards and `RemoteFile` reconstruction.
- [ ] Update `WebDavApplication` session wiring.
- [ ] Update `MusicPlayerAppSessionTest`, `PlaybackSessionResumptionTest`, and `ProcessRestartSimulationTest`.
