# 01: SWR 曲库即时搜索与穿梭插队回放 (SWR Instant Search and Ad-hoc Queue Insertion)

**What to build:** 用户在曲库顶栏点击搜索图标展开 Material 3 沉浸式搜索栏，键入关键词可毫秒级实时检索本地 Room 数据库中已缓存的目录快照与音频曲目（包含 CUE 虚拟分轨）。点击检索出的目录，应用穿梭导航直达该目录并联动更新顶部面包屑路径条；点击检索出的音频文件，播放器将其作为单曲即时插队（Play Next）到当前播放队列中起播，不破坏原有队列主体。

**Blocked by:** None (can start immediately)

**Status:** completed

- [x] 曲库顶栏右侧常驻搜索图标，点击平滑展开全宽 Material 3 搜索栏（SearchBar），自动聚焦输入框并支持清空按钮与历史搜索词记录
- [x] 构建 `LibrarySearchRepository` 检索接缝，对本地已访问目录快照（`directory_cache`）与音频元数据表（`track_metadata`）进行大小写不敏感的毫秒级全文匹配
- [x] 搜索结果列表清晰区分展示“文件夹目录”与“音频曲目”，包含 CUE 虚拟分轨的单曲标题、艺术家与所属整轨大文件标签
- [x] 点击搜索结果中的“文件夹目录”，立即退出搜索并穿梭导航至该目录层级，顶部胶囊面包屑（Breadcrumb Strip）联动对齐更新
- [x] 点击搜索结果中的“音频曲目”或“虚拟分轨”，调用会话层 `playNext(track)` 执行即时插队起播，保持当前播放队列的主体结构不变
- [x] 编写针对 `LibrarySearchRepository` 的多维检索测试（大小写不敏感、中英文混合、特殊字符过滤、空查询处理）
- [x] 编写针对搜索结果分流交互与穿梭导航的端到端集成测试
