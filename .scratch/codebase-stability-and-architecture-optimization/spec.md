Status: ready-for-agent

# Feature Specification: Codebase Stability, Resource Management, and Architecture Optimization

## Problem Statement

Following rapid iterative feature additions (WebDAV client streaming, native FFmpeg WMA decoding, 16KB page alignment, MD3 navigation overhaul, SWR persistent directory cache, and cold start session resumption), the application functions correctly but exhibits structural debt and resource management vulnerabilities under prolonged, intensive real-world usage:

1. **Unbounded In-Memory Caches**: Both `DirectoryRepositoryImpl` and `LyricsRepositoryImpl` store parsed directory snapshots and synchronized lyrics in unbounded `ConcurrentHashMap` instances. Users connecting to large WebDAV libraries with thousands of folders or extensive song collections face progressive memory bloat without eviction, creating risk of memory pressure or Out-Of-Memory (OOM) failures during long background playback sessions.
2. **Orphaned Cover Art and Unmanaged Disk Storage**: `CoverArtStorageImpl` saves extracted audio cover thumbnails directly to internal persistent storage (`context.filesDir/covers/`). When a user deletes a `WebDavServer`, database records are cascade-deleted via SQLite foreign keys, but physical cover files remain orphaned on disk indefinitely. Furthermore, there is no maximum disk quota or LRU cleanup for cover files, risking silent disk consumption.
3. **Redundant Network Client Re-instantiation**: `OkHttpWebDavClient` rebuilds an `OkHttpClient` instance on every network invocation (connection testing, directory listing, range probing, text fetching), repeatedly creating interceptors, authenticators, and in self-signed TLS configurations, generating fresh `SSLContext` and `TrustManager` arrays each time.
4. **Monolithic UI Component**: `DirectoryBrowserScreen.kt` has grown into a 1,063-line monolithic file containing 15 distinct composable functions (main screen, breadcrumb strip, rows, dialogs, badges, empty/error states), violating separation of concerns and degrading maintainability.
5. **Accessibility and Sub-optimal Tab Visibility**: The tab switcher in `MainActivity.kt` offsets the inactive screen to `translationX = 99999f` with `alpha = 0f`. While preserving scroll states, the off-screen composition remains attached to the layout tree, which can cause accessibility tools (TalkBack) to traverse hidden interactive controls.
6. **Notification Icon Modernization**: The playback notification uses the legacy platform drawable `android.R.drawable.ic_media_play`, which can render as a white square or distort on various OEM ROM status bars.

## Solution

Consolidate and harden the codebase across four unified pillars without breaking existing APIs or user workflows:

1. **Bounded LRU Memory Caching**: Introduce strict LRU capacity limits for in-memory caches in `DirectoryRepository` (e.g. 50 most recent directory snapshots) and `LyricsRepository` (e.g. 100 most recent parsed lyrics), relying safely on Room L2 persistent cache for directory misses.
2. **Lifecycle-Aware Cover Art Storage**: Migrate thumbnail file storage from `filesDir` to `cacheDir/covers/` to allow Android OS reclaim under disk pressure. Integrate server deletion with physical cover cleanup (`deleteServerCovers(serverId)`), and introduce a background cleanup policy when storage exceeds an established limit (e.g., 50MB).
3. **Cached Network Clients**: Cache constructed `OkHttpClient` instances keyed by server identifier and connection credentials/TLS parameters, avoiding repetitive `SSLContext` allocation while preserving connection pooling efficiency.
4. **Decomposed Modular UI Architecture**: Refactor `DirectoryBrowserScreen.kt` by extracting self-contained composable components into dedicated, single-responsibility files under `ui/browser/components/` (`DirectoryBreadcrumbStrip.kt`, `DirectoryItemRow.kt`, `AudioTrackItemRow.kt`, `FileInfoDialog.kt`, `BrowserStateViews.kt`).
5. **Accessibility-Guarded Tab Switching**: Enhance the persistent tab container with accessibility semantics shielding (`clearAndSetSemantics`) or hidden-state disabling so off-screen elements are hidden from assistive services.
6. **Vector Notification Small Icon**: Provide a dedicated monochrome vector drawable for playback notifications, ensuring crisp rendering across all modern Android versions and vendor skins.

---

## User Stories

### Memory Stability & Caching

1. As a listener browsing through hundreds of directories in a massive remote music collection, I want directory memory usage to remain bounded within an LRU quota, so that the player never crashes due to memory exhaustion.
2. As a listener navigating back to a recently visited folder, I want instant 0ms rendering from the LRU memory cache, so that back navigation remains instantaneous.
3. As a listener navigating to an older directory that was evicted from memory, I want it to load immediately from the local Room database cache, so that I experience zero perceptible latency without re-fetching from the network.
4. As a listener playing through dozens of songs with rich synced lyrics, I want parsed lyrics in memory to be managed by an LRU eviction strategy, so that lyrics memory footprint stays minimal during long playback sessions.

### Storage Cleanliness & Lifecycle

5. As a user deleting an unused WebDAV server from Server Management, I want all locally cached album art thumbnails for that server to be permanently erased from disk, so that obsolete server data does not consume device storage.
6. As a user with limited internal phone storage, I want cover thumbnails stored in the application's cache directory with a maximum size threshold, so that the OS can reclaim cache space if needed and my device never runs out of space.
7. As a listener playing songs whose covers were reclaimed by the OS, I want the player to smoothly fall back to default audio icons or transparently re-fetch thumbnails, so that playback is never interrupted.

### Network Performance & Efficiency

8. As a listener browsing folders and queueing songs, I want network requests to reuse existing OkHttp clients and connection pools for the same server, so that TLS handshakes and connection setup latencies are minimized.
9. As a user using self-signed SSL/TLS certificates on a local NAS, I want the custom SSL configuration to be initialized once and cached, so that CPU cycles are not wasted re-generating SSL contexts on every HTTP range probe.

### UI Maintainability & Accessibility

10. As a developer maintaining the codebase, I want `DirectoryBrowserScreen` divided into focused, modular composables, so that each component can be updated, previewed, and tested in isolation.
11. As a visually impaired listener using Android TalkBack, I want the inactive tab (e.g. Server List while on Media Library) to be completely ignored by accessibility focus, so that screen reader navigation is not cluttered by off-screen buttons.
12. As a listener viewing system notifications, I want the music player's notification icon in the status bar to display a crisp, modern vector icon matching the Material Design 3 style, so that system notifications look polished across all Android versions.

---

## Implementation Decisions

### Bounded Memory Cache Architecture

- `DirectoryRepositoryImpl` replaces the unbounded `ConcurrentHashMap<String, RemoteDirectory>` with a thread-safe LRU structure (e.g. an internal synchronized `LinkedHashMap` or `LruCache` with maximum capacity of 50 directories).
- Cache misses transparently fall through to `DirectoryCacheDao` (Room L2), which retains the full persistent snapshot with SWR behavior.
- `LyricsRepositoryImpl` replaces `ConcurrentHashMap<String, Lyrics>` with an LRU cache limited to 100 entries.

### Cover Art Storage & Cascade Deletion

- Change storage path from `context.filesDir/covers/` to `context.cacheDir/covers/`.
- Add `fun deleteServerCovers(serverId: Long): Unit` to `CoverArtStorage` interface and implementation, deleting all files prefixed with `cover_${serverId}_`.
- In `ServerRepositoryImpl.deleteServer(serverId)`, invoke `coverArtStorage.deleteServerCovers(serverId)` in conjunction with database row deletion.
- In `CoverArtStorageImpl`, enforce a basic disk quota check (e.g. if covers directory exceeds 50MB, prune the oldest 20% of files by last modified timestamp).

### OkHttpClient Instance Caching

- Inside `OkHttpWebDavClient`, introduce an internal thread-safe client cache `ConcurrentHashMap<String, OkHttpClient>`.
- The cache key is derived from server properties: `${server.id}:${server.endpointUrl}:${server.username}:${server.allowSelfSigned}`.
- Re-use cached `OkHttpClient` instances across calls; invalidate/evict when server settings are modified or on `clearCache()`.

### UI Component Decomposition

- Decompose `DirectoryBrowserScreen.kt` into dedicated files under `com.webdav.player.ui.browser.components`:
  - `DirectoryBreadcrumbStrip.kt`: Breadcrumb row and chips navigation.
  - `DirectoryItemRow.kt`: Folder list items with folder iconography.
  - `AudioTrackItemRow.kt`: Audio track items with cover thumbnail, title, artist, audio quality pill, and overflow menu.
  - `FileInfoDialog.kt`: File properties dialog (name, path, size, format, last modified).
  - `BrowserStateViews.kt`: Empty folder view, error retry view, and no active server view.
- `DirectoryBrowserScreen.kt` retains only the top-level Scaffold, pull-to-refresh logic, top app bar, and LazyColumn assembly.

### Accessibility Semantics Guard for Inactive Tabs

- In `MainActivity.kt`, wrap the inactive tab container with `Modifier.clearAndSetSemantics { }` when not active, or set `Modifier.semantics { invisibleToUser() }` so TalkBack completely skips the inactive view hierarchy without disrupting Composable state retention.

### Status Bar Vector Icon

- Create `res/drawable/ic_notification_playback.xml` containing a clean, monochrome 24dp vector icon.
- Update `WebDavNotificationProvider` to reference `R.drawable.ic_notification_playback` instead of `android.R.drawable.ic_media_play`.

---

## Testing Decisions

### What Makes a Good Test

Tests must verify external behavioral contracts, state transitions, and memory/disk management invariants without asserting internal private field mechanics:

1. **LRU Cache Eviction Invariant**: Inserting N + 1 distinct directory/lyrics entries beyond capacity must evict the least-recently-accessed entry from memory while preserving recently accessed entries.
2. **Room Fallback Resilience**: When an evicted directory is queried, it must seamlessly load from Room L2 without network request.
3. **Server Cover Deletion Cascading**: Deleting a server via repository must result in its corresponding cover thumbnail files being unlinked from disk, while other servers' thumbnails remain intact.
4. **Client Instance Reuse**: Successive network calls for the same server configuration must reuse the same OkHttpClient instance without re-instantiating SSL contexts.
5. **Component Render Isolation**: Unit tests for decomposed composable components ensure that folder rows, track rows, and breadcrumbs render appropriate labels, badges, and click triggers without crashing.

### Tested Modules

- `DirectoryRepositoryTest`: LRU boundary and Room fallback behavior.
- `LyricsRepositoryTest`: LRU capacity and eviction.
- `CoverArtStorageTest`: Cache directory migration, LRU quota pruning, and `deleteServerCovers`.
- `ServerRepositoryTest`: Cascade deletion of cover art on server removal.
- `WebDavClientTest`: Client instance caching verification.
- `DirectoryBrowserPresentationTest`: Verifying decomposed UI components remain functionally identical.

### Prior Art

- Existing `DirectoryRepositoryTest.kt` for mock WebDAV client and in-memory DAO setup.
- Existing `TrackMetadataRepositoryTest.kt` for cover art storage mocking.
- Existing `DirectoryBrowserPresentationTest.kt` for Compose UI node assertions.

---

## Out of Scope

- Migrating to dependency injection frameworks (Hilt / Koin) — current manual constructor injection in `WebDavApplication` remains straightforward, predictable, and well-tested for the current project scope.
- Persistent audio file caching (strictly forbidden by ADR 0002).
- Complete rewrite of Jetpack Compose navigation library — existing hoisted tab architecture in `MainActivity` with persistent state fulfills all user requirements.
- Full offline lyric database schema — lyric resolution remains dual-source on-demand as specified in ADR 0006.

---

## Further Notes

- All changes are backwards-compatible and preserve existing Room migrations (Database version 4 remains intact).
- No new third-party dependencies are required.
