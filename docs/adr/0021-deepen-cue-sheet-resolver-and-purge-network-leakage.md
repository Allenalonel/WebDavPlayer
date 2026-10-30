# 0021: 深层 CUE 解析器下沉与消除网络客户端泄漏 (Deepen CUE Sheet Resolver and Purge Network Leakage)

## Context

在 WebDavPlayer 中，对 CUE 虚拟分轨（ADR-0014）的支持对于纯流式播放多曲目长音频（如整轨 FLAC/WAV）至关重要。然而，在此前的实现中，CUE 相关逻辑碎片化地分散在三个浅层、静态工具模块中：
1. `CueTextCache`：全局静态 `ConcurrentHashMap`，无条目上限与淘汰策略，不仅存在长期内存泄漏风险，且容易引发跨单元测试的状态污染；
2. `CueAssociationHelper`：静态关联匹配工具；
3. `CueParser`：静态 CUE 词法与时间轴解析器。

由于缺乏统一深度的领域封装（Deep Domain Seam）：
- **UI 层网络穿透**：`DirectoryBrowserViewModel` 直接持有了底层网络客户端 `WebDavClient`，在 ViewModel 内部手动发起网络请求拉取 `.cue` 文本、直接读写 `CueTextCache` 并自行调度 `CueAssociationHelper` 与 `CueParser`；
- **回放会话职责越界**：`MusicPlayerAppSessionImpl` 同样持有了 `WebDavClient`，仅为了在播放与恢复分轨时发起网络请求拉取 CUE 文本；
- **曲库搜索逻辑重复**：`LibrarySearchRepositoryImpl` 为了在离线镜像中搜索虚拟分轨，被迫在构造函数接收 `cueTextProvider: ((Long, String) -> String?)` 函数参数，并重复拷贝了 CUE 指令关联与分轨解析流程。

修改或排查任何 CUE 处理缺陷都需要横跨 UI、会话与存储三套体系，严重违背信息隐藏与高内聚设计原则。

## Decision

恪守纯流式零磁盘持久化占用（ADR-0002）与发布包体积预算的前提下，确立以下架构演进：

### 1. 建立深层领域抽象 `CueSheetResolver`
- 在 `domain/cue/CueSheetResolver.kt` 建立统一领域接口：
  - `suspend fun resolveDirectoryCueAlbums(server: WebDavServer, directory: RemoteDirectory, parentDurationsByPath: Map<String, Long> = emptyMap()): List<CueAlbumItem>`：负责远程目录内 CUE 文件的自动探测、网络拉取、启发式物理音频关联匹配及分轨解析；
  - `suspend fun resolveVirtualTracks(server: WebDavServer, cuePath: String, parentAudioPath: String, totalDurationMs: Long? = null): List<VirtualTrack>`：按需解析指定 CUE 与母轨对应的虚拟音轨列表；
  - `fun getCachedVirtualTracks(server: WebDavServer, cuePath: String, parentAudioPath: String): List<VirtualTrack>?`：提供同步无阻塞的内存瞬态快照探测，避免在同步调用栈中引发线程死锁；
  - `fun clearCache()`：供会话与测试清理缓存。
- 在 `data/cue/DefaultCueSheetResolver.kt` 提供生产实现，接收 `WebDavClient` 与协程调度器，将底层 HTTP 传输与 AST 解析完全收拢为私有实现细节。

### 2. 内化有界内存 LRU 缓存
- 在 `DefaultCueSheetResolver` 内部建立容量为 50 条的线程安全内存 LRU 缓存（基于 `LinkedHashMap(accessOrder = true)`），自动淘汰超期或超额的 CUE 文本；
- 严格遵循 ADR-0002 约束，CUE 文本绝不写入持久化闪存盘，仅驻留于临时受限内存；
- 正式废弃全局静态单例 `CueTextCache`，彻底消除测试间的交叉状态污染。

### 3. 上层调用方全面解耦与网络客户端泄漏清除
- **`DirectoryBrowserViewModel`**：移除 `webDavClient` 构造参数，全面改由注入 `CueSheetResolver`，`processDirectoryCueAlbums` 简化为单行委托调用；
- **`MusicPlayerAppSessionImpl`**：移除 `webDavClient` 构造参数，改由注入 `CueSheetResolver`，会话恢复与分轨偏移换算统一委托给领域解析器；
- **`LibrarySearchRepositoryImpl`**：移除 `cueTextProvider` lambda 穿透，改由注入 `CueSheetResolver`，不再重复编写文件关联与分轨解析算法；
- **依赖注入**：在 `WebDavApplication` 与 `DirectoryBrowserViewModelFactory` 中统一定义单例 `DefaultCueSheetResolver` 并完成向各调用方的装配注入。

## Consequences

- **架构局部性与高内聚**：所有 CUE 相关的网络传输、文本缓存、物理音频匹配与词法解析全部聚合在 `DefaultCueSheetResolver` 内部，上层各模块不再感知网络细节或解析流程；
- **零跨测试状态泄露**：消除全局静态缓存，单元测试通过 Fake/Mock `CueSheetResolver` 即可进行完全隔离的确定性验证；
- **主线程安全与零死锁**：彻底摒弃在回放初始化流程中使用 `runBlocking` 的反模式，通过非阻塞内存快照与挂起协程确保高并发下的系统流畅性；
- **100% 测试覆盖与零体积膨胀**：完整单元测试套件全部通过，发布 APK 依然严格保持在 7.5 MB 预算红线内。
