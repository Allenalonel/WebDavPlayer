# 更新日志 (Changelog)

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) 格式与 [语义化版本 (Semantic Versioning)](https://semver.org/lang/zh-CN/) 规范。

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
