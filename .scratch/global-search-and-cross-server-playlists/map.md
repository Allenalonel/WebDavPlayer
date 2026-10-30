# Map: 全局即时搜索与跨服务器自定义本地歌单

## Notes
- Feature branch: `feature/global-search-and-cross-server-playlists`
- Spec: `spec.md`
- Tasks:
  - 01: SWR 曲库即时搜索与穿梭插队回放 (`issues/01-swr-instant-search-and-ad-hoc-queue-insertion.md`) - Blocked by: None
  - 02: 跨服务器歌单持久化与底栏管理 (`issues/02-cross-server-playlist-persistence-and-management.md`) - Blocked by: None
  - 03: 歌单详情视图、拖拽重排与多维排序 (`issues/03-playlist-detail-drag-reordering-and-sorting.md`) - Blocked by: 02
  - 04: 跨服务器流式起播与失效置灰自愈 (`issues/04-cross-server-playback-and-inactive-track-fault-tolerance.md`) - Blocked by: 03
  - 05: 全链路端到端集成、弱网回归与发布包体积守门 (`issues/05-end-to-end-pipeline-and-packaging-budget-gate.md`) - Blocked by: 01, 04

## Decisions-so-far
- Created integration branch `feature/global-search-and-cross-server-playlists`.
- Identified task graph and initial frontier: [01, 02].
- [01 resolved] Implemented SWR instant search and ad-hoc queue insertion. Added `LibrarySearchRepository`, integrated M3 SearchBar in `DirectoryBrowserScreen`, and verified tests. (See `.scratch/global-search-and-cross-server-playlists/issues/01-swr-instant-search-and-ad-hoc-queue-insertion.md`)
- [02 resolved] Implemented Room migration 4->5 (`playlists` and `playlist_tracks`), `PlaylistRepository`, 3-tab navigation with "歌单" tab, `PlaylistListScreen`, and unified "添加到歌单" dialog. (See `.scratch/global-search-and-cross-server-playlists/issues/02-cross-server-playlist-persistence-and-management.md`)
- [03 resolved] Implemented `PlaylistDetailScreen` with LazyColumn drag reordering, multi-dimensional sorting, track removal, and verified tests. (See `.scratch/global-search-and-cross-server-playlists/issues/03-playlist-detail-drag-reordering-and-sorting.md`)
- [04 resolved] Implemented cross-server playlist playback (`playPlaylist`), dynamic server authentication context, 404 auto-skip self-healing, and inactive track diagnosis Toast. (See `.scratch/global-search-and-cross-server-playlists/issues/04-cross-server-playback-and-inactive-track-fault-tolerance.md`)
- [05 resolved] Added end-to-end integration test `EndToEndPlaylistAndSearchPipelineTest`, preserved playlist queue origin across session persistence and cold restart, and passed size budget gate (APK <= 7.5 MB, DEX <= 4.0 MB). (See `.scratch/global-search-and-cross-server-playlists/issues/05-end-to-end-pipeline-and-packaging-budget-gate.md`)
- [code-review P1 resolved] Added Room indices for track_metadata & directory_cache in MIGRATION_4_5, switched to indexed SQLite search, persisted search history via SearchHistoryStore, made search global across servers, and integrated auth & reachability fault-tolerance.
- [code-review 2nd round P1 resolved] Fixed CUE virtual timeline leak on standard track transition, resolved virtual track offset on single playTrack, assigned QueueOrigin.AD_HOC, and supported cross-server directory navigation.
- [spec accepted] Feature officially accepted and verified: all User Stories 1-25, design prototypes, physical gesture thresholds, and review remediations confirmed.






## Fog
- None currently.
