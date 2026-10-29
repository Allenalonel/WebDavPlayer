# 03: 歌单详情视图、拖拽重排与多维排序 (Playlist Detail, Drag Reordering and Sorting)

**What to build:** 点击歌单卡片平滑进入歌单详情页（`PlaylistDetailScreen`），展示歌单元数据与完整歌曲列表。基于 Jetpack Compose LazyColumn 提供流畅的长按上下拖拽重排交互手势，手势释放时调用仓储原子更新排序索引（`sortOrder`）并实时持久化；提供排序菜单支持按“添加时间正序/倒序”、“歌曲标题”、“艺术家”一键多维排序；支持单曲通过侧滑或菜单从歌单中安全移出，不破坏远程 NAS 上的物理音频文件。

**Blocked by:** 02 (Cross-Server Playlist Persistence and Management)

**Status:** claimed

- [ ] 构建歌单详情界面（`PlaylistDetailScreen`），展示歌单标题、创建时间、曲目总数及完整的音轨条目列表
- [ ] 基于 LazyColumn 实现流畅的长按上下拖拽重排交互手势，附带触感反馈与平滑位移动画
- [ ] 拖拽释放时，由 `PlaylistRepository` 原子更新受影响音轨条目的 `sortOrder`，确保即使进程重启顺序依然保持
- [ ] 顶栏操作区提供排序弹窗菜单，支持一键按“添加时间（最新优先/最早优先）”、“歌曲标题（A-Z）”、“艺术家（A-Z）”进行排序，并同步写回持久层
- [ ] 歌单曲目条目支持单曲从歌单中移除（通过向左滑动或点击三点菜单中的“从歌单移除”），且仅删除歌单关联记录，严格保留远程物理文件
- [ ] 编写针对拖拽换位索引更新、多维排序稳定性及单曲移除逻辑的自动化单元与 UI 测试
