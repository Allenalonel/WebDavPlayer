# 01: Fold MediaSource Adapter into Player Engine

**What to build:**
Eliminate `WebDavMediaSourceAdapter` as an independent shallow seam. MediaSource assembly, OkHttp DataSource factory creation, format-specific extractor binding, and connection pooling are private responsibilities of `Media3AudioPlayerEngine`. Internalize `DefaultWebDavMediaSourceAdapter` as an internal collaborator `WebDavMediaSourceBuilder` (or package-private helper), simplify `PlaybackSessionHost` constructors to stop threading this adapter through, delete the `WebDavMediaSourceAdapter` interface, and migrate `WebDavMediaSourceAdapterTest` to test without downcasts.

**Blocked by:** None

**Status:** resolved

- [x] Create `internal class WebDavMediaSourceBuilder` (or fold into `Media3AudioPlayerEngine`)
- [x] Remove `WebDavMediaSourceAdapter` interface and delete the file
- [x] Update `Media3AudioPlayerEngine` to directly own its media source builder
- [x] Clean up `PlaybackSessionHost` constructor overloads by removing `mediaSourceAdapter` parameter
- [x] Migrate `WebDavMediaSourceAdapterTest` to directly verify `WebDavMediaSourceBuilder`, eliminating all 10 `as DefaultWebDavMediaSourceAdapter` downcasts
- [x] Update `WebDavApplication` wiring
- [x] Ensure all player engine tests pass cleanly

## Answer
Eliminated `WebDavMediaSourceAdapter` as an independent shallow seam:
1. Created `internal class WebDavMediaSourceBuilder` to manage streaming data sources, OkHttpClient connection pool, extractors factory binding, and MediaSource creation as an internal collaborator of the playback core.
2. Deleted `WebDavMediaSourceAdapter.kt`.
3. Updated `Media3AudioPlayerEngine` to directly own `WebDavMediaSourceBuilder`.
4. Cleaned up `PlaybackSessionHost` constructors and companion `getInstance`, removing the `mediaSourceAdapter` parameter.
5. Replaced `WebDavMediaSourceAdapterTest` with `WebDavMediaSourceBuilderTest`, eliminating all 10 `as DefaultWebDavMediaSourceAdapter` test downcasts.
6. Updated `WebDavApplication` and integration tests. All tests pass cleanly and modified files adhere to the 600-line budget.
