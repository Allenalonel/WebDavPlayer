# 02: Manifest-Aware In-Memory Artwork Candidate Pre-Filtering

**What to build:** End-to-end elimination of blind HTTP 404 network storms when browsing albums where audio tracks lack embedded cover art. The metadata resolution pipeline receives the remote directory's file manifest (already retrieved during directory listing) and performs an in-memory case-insensitive lookup before dispatching remote requests for candidate artwork (`$basename.jpg`, `${filename}.png`, `cover.jpg`, etc.). Candidate files that do not exist on the remote server are skipped with zero network requests, reducing the remote probe volume from dozens of 404s to at most 1 successful fetch, while preserving fallback probing when directory manifests are absent.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Metadata resolution pipeline accepts an optional collection of known directory files
- [ ] Candidate artwork filenames are checked against the in-memory directory file manifest using case-insensitive comparison prior to making HTTP requests
- [ ] Non-existent artwork files generate zero network requests and zero HTTP 404 responses
- [ ] Directory repository passes retrieved directory file lists into metadata enrichment
- [ ] Standalone track metadata queries without a directory manifest retain resilient probing fallback
- [ ] Unit tests in `TrackMetadataResolverTest` and `TrackMetadataRepositoryTest` assert that directories of tracks without embedded art issue zero HTTP requests for missing files and at most 1 request for valid external covers
