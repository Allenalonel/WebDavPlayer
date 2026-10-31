# 01: Fold MediaSource Adapter into Player Engine

**What to build:**
Eliminate `WebDavMediaSourceAdapter` as an independent shallow seam. MediaSource assembly, OkHttp DataSource factory creation, format-specific extractor binding, and connection pooling are private responsibilities of `Media3AudioPlayerEngine`. Internalize `DefaultWebDavMediaSourceAdapter` as an internal collaborator `WebDavMediaSourceBuilder` (or package-private helper), simplify `PlaybackSessionHost` constructors to stop threading this adapter through, delete the `WebDavMediaSourceAdapter` interface, and migrate `WebDavMediaSourceAdapterTest` to test without downcasts.

**Blocked by:** None

**Status:** open

- [ ] Create `internal class WebDavMediaSourceBuilder` (or fold into `Media3AudioPlayerEngine`)
- [ ] Remove `WebDavMediaSourceAdapter` interface and delete the file
- [ ] Update `Media3AudioPlayerEngine` to directly own its media source builder
- [ ] Clean up `PlaybackSessionHost` constructor overloads by removing `mediaSourceAdapter` parameter
- [ ] Migrate `WebDavMediaSourceAdapterTest` to directly verify `WebDavMediaSourceBuilder`, eliminating all 10 `as DefaultWebDavMediaSourceAdapter` downcasts
- [ ] Update `WebDavApplication` wiring
- [ ] Ensure all player engine tests pass cleanly
