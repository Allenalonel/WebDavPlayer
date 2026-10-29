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


## Fog
- None currently.
