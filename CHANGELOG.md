# 更新日志 (Changelog)

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 格式与 [语义化版本 (Semantic Versioning)](https://semver.org/lang/zh-CN/) 规范。

---

## [2.3] - 2026-10-07

### 🚀 新增 (Added)
- **单物理流 CUE 虚拟分轨回放 (Single Physical Stream + Logical Virtual Tracks)**：引入单物理流无缝虚拟分轨方案，针对整轨无损音频（FLAC/WAV/APE），底层播放引擎始终维持单条持久化 HTTP 长连接，不切断网络、不重建解码器，杜绝传统分轨方案的重连停顿与爆音。
- **纯内存 CUE 状态机解析器 (In-Memory CueParser Module)**：实现无第三方库依赖的纯内存流式文本状态机解析器，支持双引号/无引号、多空白字符、REM 注释等变种格式，毫秒级帧转换（75 fps 精准换算），仅在点选时瞬态驻留，严格遵循纯流式无本地磁盘缓存红线。
- **虚拟时间轴映射引擎 (Virtual Timeline Engine) 与零打扰元数据广播**：会话层高频双向映射物理时间与虚拟分轨相对时间；自然跨越分轨时响应式向 UI 与系统 MediaSession 广播分轨元数据（Non-Disruptive Metadata Enrichment），实现物理级绝对无缝回放（Bit-Perfect Gapless）。
- **待播队列内联展开虚拟分轨 (Inlined Virtual Tracks in PlaybackQueue)**：曲库目录浏览与播放队列抽屉支持将包含 CUE 的整轨大音频平滑原地展开为完整分轨项，支持点选跳转、相对进度拖拽与等化器动态跳动指示（Equalizer Track Indicator）。
- **文件夹环形闭环播放 (Folder Ring Playback)**：播放队列拓扑严格约束在当前活跃远程目录，列表循环时末曲自然回环至首曲，严禁跨越父级或同级兄弟目录。
- **专用流式长连接池与双轨预卷缓冲 (Streaming Pre-roll & Keep-Alive Connection Pool)**：OkHttp 客户端保持持久化长连接池（保活 5 分钟），结合 10 秒回退缓冲区与双轨预卷机制消灭普通独立曲目切换时的声学空隙。
- **会话现场持久化扩展 (Playback Session State Schema)**：DataStore 扩展记录 `cuePath` 与分轨序号/相对偏移量，支持冷启动与进程重建下的无缝精确续播。
- **架构决策记录 (ADR)**：归档落地 [ADR 0014: CUE 虚拟分轨流内映射与目录闭环流式播放](docs/adr/0014-cue-virtual-track-and-folder-ring-gapless-streaming.md)。

---

## [2.2] - 2026-10-05

### 🚀 新增 (Added)
- **局部化 `AppIcons` 资产接缝 (Localized AppIcons Seam)**：集中显式收录应用实际引用的矢量与基础图标，彻底移除对庞大的 `androidx.compose.material:material-icons-extended` 依赖，从源码源头切断 40+ MB 冗余矢量图标死代码的侵入。
- **发布包体积预算自动化守门门禁 (Size Budget Enforcement)**：在 Gradle 构建流中引入 `verifyReleasePackaging` 自动化门禁任务与 `PackagingSeamVerificationTest` 单元测试，强制约束 Release APK 硬性指标：**总包体 <= 7.5 MB**（实测达到 6.57 MB，较 2.1 版本的 56.8 MB 缩减达 88%）、单 `classes.dex` 字节码 <= 4.0 MB，且仅包含 `arm64-v8a` 架构。
- **架构决策记录 (ADR)**：归档落地 [ADR 0013: 64 位单架构 Native ABI、R8 二进制瘦身与打包接缝](docs/adr/0013-64bit-only-abi-and-r8-binary-thinning.md)。

### ⚡ 优化与工程变更 (Changed)
- **全面开启 R8 代码混淆与资源缩减 (R8 Minification & Resource Shrinking)**：在 Release 构建配置中开启 `isMinifyEnabled = true` 与 `isShrinkResources = true`，自动剔除无用类与未引用的布局/资源。
- **构建纵深网络与反射混淆保护**：在 `app/proguard-rules.pro` 中为 Room 实体/DAO、Media3 Session 回调、ExtractorInput 原生接口、JNI 本地方法、OkHttp `Interceptor`/`Authenticator` 及 WebSocket 监听器建立精确的 keep 规则，杜绝混淆导致的运行时反射或通讯异常。
- **强制 64 位单架构 Native ABI 过滤 (Drop 32-Bit armeabi-v7a)**：彻底剥离过时的 32 位 `armeabi-v7a` 原生 FFmpeg 动态库套件，专注 64 位 `arm64-v8a` 现代 Android 10+ 架构，Native 依赖体积直降 50%（消除约 9 MB 压缩体积）。
- **优化独立分发 APK 字节码压缩 (`dex.useLegacyPackaging = true`)**：显式配置 DEX 打包策略以 DEFLATE 算法压缩 `classes.dex`（由 4.09 MB 压缩至 1.90 MB），直接消除 AGP 默认未压缩模式带来的 2.19 MB 传输体积膨胀，为移动数据下载提供极致轻量化体验。
- **大幅加速冷启动与类加载**：DEX 文件由 3 个整合精简为单 `classes.dex`（代码量从 44.56 MB 降至 3.91 MB），显著降低系统 ART 虚拟机的类加载时间与内存占用。

---

## [2.1] - 2026-10-03

### 🚀 新增 (Added)
- **主流流式单向驱动模式 (Stream-Only Demuxing Mode)**：参考行业主流开源流媒体播放器（如 VLC、Symfonium）的远程流式规范，将 Native AVIO 回调置为纯单向消费模式，避免对不可后退的网络管道发起伪随机 seek。
- **32 位时间戳下溢归一化 (Preroll Underflow Normalization)**：在 Native 解复用层与 Kotlin Extractor 层建立双重纵深拦截，对超过阈值的负时间戳进行合法区间纠偏，确保时间戳从 0 稳定单调递增。
- **架构决策记录 (ADR)**：归档落地 [ADR 0012: 主流流式单向驱动模式与 ASF 时间戳下溢归一化防护](docs/adr/0012-stream-only-demuxing-and-timestamp-underflow-protection.md)。

### 🐛 修复 (Fixed)
- **修复 WMA 格式点击播放初始无声音缺陷**：消除了因首包时间戳溢出为未来帧导致的音频渲染器静音等待，实现点击即刻起播发声。
- **修复长音频播放异常连环跳集 (Auto-Skip Loop)**：彻底根除流式解包过程中因缓冲区伪随机回退失败返回 `I/O error (-5)` 误触发流结束的问题，杜绝曲目播放中途异常秒切下一首。
- **修复进度条显示 `1193:07:07` 的巨大时间异常**：阻断 ASF 预卷（Preroll）在 32 位无符号减法下的下溢陷阱（$0 - 3100\text{ms} \to 4,294,964,196\text{ms}$），彻底恢复正常的分秒计时显示。
- **修复跳转进度条后可能卡死在缓冲中 (STATE_BUFFERING)**：消除解码器块对齐（`block_align`）参数被起始残包动态污染的隐患，保证无论开播还是拖拽进度条后音频帧均能正常解码出声。
- **修复 Extractor 缓冲区裕量卡死问题**：解除 `format.maxInputSize` 对微小块大小的硬绑定，确保输入帧不被异常截断。

### 🔄 变更 (Changed)
- **解耦主动跳转与底层读管道**：用户手动拖拽进度条完全交由 ExoPlayer 发起动态 HTTP Range 请求重新定位，配合 Native `nativeSeek` 主动刷新解复用状态机，兼顾流式稳定性与拖拽灵敏度。
- **平稳流结束处理**：移除 Extractor 中易引发连环切歌的强制 `EOFException` 抛出逻辑，恢复 ExoPlayer 标准的数据源错误处理机制。
- **主文档精简**：重构并精简根目录 `README.md`，聚焦产品核心技术与平台特性，去除冗余的补丁日志细节。

---

## [2.0.0] - 2026-10-02

### 🚀 新增 (Added)
- **Material You 动态主题图标**：重新设计自适应超椭圆（Squircle）流体云图标，全面支持 Android 13+ 壁纸色彩提取（Themed Icons）。
- **首帧同步前台服务提升 (First-Frame Service Elevation)**：在服务启动首帧立即同步提升前台并展示通知，彻底根除 Android 8.0+ `RemoteServiceException` 风险。
- **动态凭据化媒体源 (Dynamic Authenticated MediaSource)**：ExoPlayer 底层请求全生命周期透传 Server 上下文与鉴权头，消除 HTTP 401 裸请求。
- **自愈式元数据仓储 (Self-Healing Metadata Cache)**：仓储层统一接管缩略图存活性校验，支持被系统清理后的本地缓存静默自动自愈。

### 🔄 变更 (Changed)
- **深层领域仓储抽象**：折叠内部元数据解析接缝，消除外部多头注入，净化全局播放会话逻辑。
- **歌词引擎升级**：强化行内词级卡拉OK时间戳清洗与微小时间差（<300ms）主译双语平行排版。

---

## [1.0.0] - 2026-09-26

### 🚀 初始版本 (Initial Release)
- **WebDAV 远程节点挂载**：支持标准 HTTP/HTTPS WebDAV 服务器配置与自签名 SSL 证书信任。
- **SWR 目录缓存**：基于 Room 数据库实现远程曲库毫秒级秒开浏览与断网离线可见。
- **纯流式回放引擎**：基于 AndroidX Media3 (ExoPlayer) + FFmpeg 原生 JNI 扩展，支持 MP3, AAC, FLAC, WAV, WMA, APE 全格式在线回放。
- **现代交互组件**：Material Design 3 边到边设计，配备全局驻留悬浮 Mini-Player 与沉浸式全屏大播放器视图。
