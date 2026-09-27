# 02: Deepen DirectoryRepository SWR Stream

**What to build:** A self-contained, reactive directory observation stream in `DirectoryRepository` that encapsulates L1 memory cache, L2 Room database persistent cache, and remote WebDAV PROPFIND revalidation. Callers observe a single stream that emits cached directory snapshots instantly (0ms) and asynchronously checks the remote server in the background, emitting updates if remote contents changed, fully fulfilling ADR-0008 at the repository seam.

**Blocked by:** None (can start immediately)

**Status:** completed

- [x] `DirectoryRepository` interface declares a reactive directory stream method (`observeDirectory(server: WebDavServer, path: String, forceRefresh: Boolean): Flow<ListDirectoryResult>`).
- [x] When `forceRefresh = false` and a cache entry exists in L1 memory or L2 database, the repository immediately emits the cached snapshot with 0ms delay.
- [x] The repository executes an asynchronous background PROPFIND to revalidate remote contents; if remote contents differ from the cached snapshot, it persists updates to L1 and L2 caches and emits the updated directory.
- [x] Transient network errors during background revalidation do not suppress or crash already-rendered cached data.
- [x] When `forceRefresh = true`, cache reading is bypassed, directly querying the remote WebDAV server and updating caches upon success.
- [x] Bounded LRU memory limits (50 entries) and server cache clearing mechanisms remain fully functional.
- [x] Comprehensive unit tests in `DirectoryRepositoryTest` verify immediate cache emission, background revalidation, error resilience, and forced refresh.
- [x] Full suite unit tests pass via `./gradlew.bat testDebugUnitTest`.
