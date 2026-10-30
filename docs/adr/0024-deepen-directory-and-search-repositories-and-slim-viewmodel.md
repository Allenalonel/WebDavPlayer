# 0024: 下沉目录富化与搜索历史至仓储层并轻量化视图模型 (Deepen Directory and Search Repositories and Slim ViewModel)

## Context

在 ADR-0023 成功解构领域层播放会话上帝类后，展示层与仓储层的架构接缝成为系统主要摩擦源：
1. **`DirectoryRepository` 浅层仓储缺陷**：原本的仓储仅返回原始的远程文件列表（`ListDirectoryResult`），完全不感知 CUE sheet 虚拟分轨与音轨元数据标签。
2. **`DirectoryBrowserViewModel` 包工头反模式（General Contractor）**：因为仓储缺乏富化能力，`DirectoryBrowserViewModel`（近 700 行、7 个依赖项）被迫直接编排底层网络拉取、CUE 虚拟分轨解析（`CueSheetResolver`）、异步 HTTP Range 元数据提取（`TrackMetadataRepository`）以及 3 个独立数据字典在 4 个协程 Job（`metadataObserverJob`, `metadataResolutionJob`, `cueResolutionJob`, `activeRefreshJob`）中的手动合并与并发协调。
3. **`SearchHistoryStore` 假性抽象接缝**：搜索历史持久化被拆分为一个仅有 4 个方法的独立小接口与单一 SharedPreferences 实现，导致 UI 层必须分别持有 `LibrarySearchRepository` 与 `SearchHistoryStore` 两套句柄。

## Decision

1. **内化搜索历史至 `LibrarySearchRepository`**：
   - 消除 `SearchHistoryStore` 这一浅层假性接缝，由 `LibrarySearchRepository` 与 `LibrarySearchRepositoryImpl` 原生管理搜索历史持久化。
   - 暴露显式历史管理方法：`getSearchHistory()`, `recordSearchQuery(query)`, `removeSearchHistory(query)`, `clearSearchHistory()`，严格支持 LRU 20 项上限、空白修剪与去重重排。
2. **下沉目录内容富化至 `DirectoryRepository`**：
   - 引入统一领域聚合模型 `DirectoryContent(directory, cueAlbums, metadataMap, isEnriching)`，保持 Compose UI 层组件 100% 兼容。
   - 在 `DirectoryRepository` 中引入响应式流接缝 `observeDirectoryContent(server, path, forceRefresh): Flow<DirectoryContent>`，将 `CueSheetResolver` 与 `TrackMetadataRepository` 内化为其内部协作者。
   - 规范 SWR 响应式发射生命周期：
     - 缓存命中（0ms）：立即发射缓存的目录内容；
     - 远程更新：发射更新后的远程文件树（`isEnriching = true`）；
     - 富化完成：后台自动并行完成 CUE 解析与元数据提取，完成后发射最终富化快照（`isEnriching = false`），使得 UI 刷新动画平滑自然结束；
     - 容错：后台网络重验过程中的偶发网络故障绝不冲垮已渲染的缓存目录。
3. **轻量化 `DirectoryBrowserViewModel` 为纯粹的状态投影器（Lean State Projector）**：
   - 构造参数从 7 个剥离至 4 个（`serverRepository`, `directoryRepository`, `musicPlayerAppSession`, `librarySearchRepository`），移除 `trackMetadataRepository`、`cueSheetResolver` 与 `searchHistoryStore`。
   - 彻底删除 4 个手动协程 Job 协调逻辑与 80 行的 `processDirectoryCueAlbums` 编排代码。
   - ViewModel 核心代码行数从 697 行大幅缩减至 350 行以内，纯粹作为响应式状态流的映射器。

## Consequences

- **高杠杆深模块（Deep Repository Seam）**：`DirectoryRepository.observeDirectoryContent` 成为单一、宽厚而狭窄的领域接缝，未来无论平板分栏还是目录选择器等新特性，均可零成本复用。
- **纯粹简洁的 UI 状态投影**：ViewModel 彻底摆脱多异步管道并发合并心智负担，测试时无需模拟 CUE 解析与元数据提取细节。
- **代码行数熔断红线完全达标**：`DirectoryBrowserViewModel.kt` 精简为 ~345 行，`DirectoryRepositoryImpl.kt` 仅 ~220 行，严格遵守 `AGENTS.md` 单文件 600 行红线。
