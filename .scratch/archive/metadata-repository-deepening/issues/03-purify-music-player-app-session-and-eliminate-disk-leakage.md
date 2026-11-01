# 03: Purify Music Player App Session and Eliminate Disk Leakage

**What to build:**
Remove `CoverArtStorage` from `MusicPlayerAppSessionImpl`, eliminate `isValidThumbnailFile`, `sanitizeTrackWithCoverValidation`, and the duplicate `latestMetadataCache`, and remove manual `RemoteFile` self-healing loops from the session layer.

**Blocked by:** 01-deepen-track-metadata-repository-with-thumbnail-validation-and-self-healing

**Status:** completed

- [x] Remove `CoverArtStorage` parameter from `MusicPlayerAppSessionImpl` constructor and factory.
- [x] Remove `isValidThumbnailFile` and `sanitizeTrackWithCoverValidation` methods.
- [x] Delete `latestMetadataCache` and rely solely on `TrackMetadataRepository` queries and flows.
- [x] Clean up `restoreSession` and track playback methods to remove manual disk guards and `RemoteFile` reconstruction.
- [x] Update `WebDavApplication` session wiring.
- [x] Update `MusicPlayerAppSessionTest`, `PlaybackSessionResumptionTest`, and `ProcessRestartSimulationTest`.
