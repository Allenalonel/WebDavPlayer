# WebDavPlayer

Android 平台上的 WebDAV 音频流式播放器，通过 HTTP/HTTPS 协议直接访问并播放远程 WebDAV 服务器上的音频资源。

## Language

### Remote Storage

**WebDAV Server**:
提供基于 WebDAV 协议文件访问能力的远程存储服务器配置，包含主机地址、端口、路径、认证凭证及证书信任选项。
_Avoid_: Host, Cloud, Remote Drive

**Active Server**:
用户当前选定用于浏览目录与流式获取音频资源的活跃 WebDAV Server 节点。
_Avoid_: Current Host, Selected Account, Default Server

**Remote Directory**:
WebDAV 服务器上的远程目录节点，用于组织子目录与音频文件。
_Avoid_: Folder, Remote Path

**Remote File**:
WebDAV 服务器上的文件实体，具备 URL、大小及最后修改时间等元数据。
_Avoid_: Resource, Item

**Directory Cache**:
WebDAV 远程目录在本地持久化数据库中的结构化镜像快照，包含子目录与文件元数据，用于秒级渲染并支持弱网与离线浏览。
_Avoid_: Folder Cache, File Index, Offline Copy

**Server Context**:
运行时维护的包含当前活跃 WebDAV Server 身份凭证、网络超时控制及自定义 SSL 信任套接字工厂的上下文领域对象，供底层 OkHttp 网络客户端与 MediaSource 适配器统一消费。
_Avoid_: Connection Config, Server Info, Host Context

### Playback & Library

**Audio Track**:
具有可解码音频流特性的远程音频文件（如 MP3、FLAC、WAV、WMA 等格式）。
_Avoid_: Song, Music, Audio File

**Track Metadata**:
音频曲目的描述性标签信息，包含曲目标题、艺术家、专辑名称、时长及封面图（优先提取内嵌标签，回退支持同目录外置封面）。
_Avoid_: ID3, Tag, Audio Info

**Folder Artwork**:
位于 Remote Directory 下的独立专辑封面图片实体（如 cover.jpg、folder.jpg 或同名图片），在 Audio Track 无内嵌封面时作为后备封面源。
_Avoid_: External Image, Folder Icon, Album Photo

**Playback Queue**:
当前播放会话中排队待播的有序音频曲目集合。在目录中点选曲目时，默认以当前目录的所有有效音频填充。
_Avoid_: Playlist, Track List

**Playback Mode**:
播放队列在切歌时的流转规则，包含列表循环（List Loop）、单曲循环（Single Loop）与随机播放（Shuffle）。
_Avoid_: Play Mode, Repeat Setting

**Streaming Buffer**:
播放引擎在流式回放过程中暂存在内存或临时缓冲区中的音频数据，播放结束即释放，不落持久化磁盘。
_Avoid_: Local Cache, Download File, Offline Storage

**Playback Session State**:
本地持久化保存的播放会话现场，记录活跃服务器、播放队列、当前曲目索引及毫秒级播放进度，用于冷启动无缝续播。
_Avoid_: Last Song, Resume Info, History Record

**Directory Session State**:
针对各个 WebDAV Server 独立持久化记录的最后浏览目录路径，用于在切换服务器或重启应用时无缝还原上次停留的目录层级。
_Avoid_: Last Folder, Recent Path, Navigation History

**Lyrics Source**:
与曲目关联的歌词提供方，支持从远程同目录同名 `.lrc` 文件解析或从音频内嵌标签中提取时间轴文本，具备逐行副歌展开、逐字时间戳清洗及主译双语时间对齐能力。
_Avoid_: Lyric File, Lrc Text

**Bilingual Lyric Line**:
按毫秒级时间戳对齐的结构化单行歌词模型，聚合主歌词文本与可选的译文文本，在播放器中作为单一时间节点高亮与联动跳转。
_Avoid_: Dual Lyrics, Translated Line, Subtitle Pair

**Audio Focus**:
Android 系统级音频焦点协商状态，用于响应来电暂停、挂断恢复及系统提示音时的临时音量压低（Ducking）。
_Avoid_: Sound Priority, Volume Interrupt

**First-Frame Service Elevation**:
遵循 Android 8.0+ 及更高版本前台服务契约（5 秒限制）的生命周期规范：在 `WebDavMediaService` 创建的首帧同步发起前台服务提升并展示系统通知，彻底解耦网络延迟与流媒体缓冲状态，消除进程被系统杀死的风险。
_Avoid_: Background Player, Async Notification, Late Elevation

**Authenticated MediaSource**:
携带活跃 WebDAV Server 凭据头（Basic Authentication）与自定义 SSL 证书配置的流媒体源实例，确保播放器在发生后台元数据富化、自动重试或播放切歌时身份凭证与安全连接不丢失。
_Avoid_: Naked Stream, Anonymous DataSource, Static Player URL

**Non-Disruptive Metadata Enrichment**:
针对当前正在播放曲目的元数据刷新策略：当后台按需提取到高清封面、完整时长或艺术家信息时，仅响应式广播更新应用会话状态（App Session State）与系统媒体会话（MediaSession），严禁重建底层的播放器时间线（Timeline），确保流式回放无声学卡顿与重缓冲。
_Avoid_: Timeline Rebuild, Track Reset, Player Reload

**Self-Healing Metadata Cache**:
由 `TrackMetadataRepository` 内部自主担保的自愈机制：当本地磁盘缩略图因系统清理缓存而丢失，或播放队列加载未解析音轨时，仓储接缝对外先行返回零破坏的安全元数据，并在后台异步发起受限 Range 元数据提取并反向写回本地持久化数据库与落盘，自动修补缺失的专辑标签、时长与封面缩略图，彻底向所有上层调用者屏蔽底层文件存活性校验。
_Avoid_: Manual Sync, Cold Cache, Static Database

**Virtual Track**:
由同目录 `.cue` 文件描述、映射在单一长音频物理文件内部的时间区间切片。在底层播放器内核中不作为独立的物理 `MediaItem` 重复实例化，完全依托于会话层的时间轴换算与内联元数据广播，确保纯流式下的绝对无缝体验。
_Avoid_: Split Audio, Cut Track, Clipped MediaItem

**Folder Ring Playback**:
播放队列以当前所处 Remote Directory 的曲目集合为唯一严格边界。列表循环模式下最后一首播完自动回流至当前目录首曲，严禁跨越父级或同级兄弟目录。
_Avoid_: Infinite Cross-Folder Playback, Auto Next Folder, Directory Hop

**Pre-roll Stream Buffering**:
在纯流式、无本地持久化磁盘缓存的约束下，利用长连接复用（Keep-Alive）与双轨预加载机制，在当前曲目接近尾声时提前装填下一曲目起始 PCM 音频流，实现零声学间隙无缝衔接下一首（Gapless Playback）。
_Avoid_: Local Download, Offline Cache, CacheDataSource

**Track Metadata Resolver**:
`TrackMetadataRepositoryImpl` 内部私有的深层网络与音频提取适配器，专职负责通过受限网络 HTTP Range 分片提取、自适应两阶段拼接、双源封面探测（内嵌封面优先，同目录外置封面回退并持锁）及图片缩略图本地落盘。对外不设立冗余公共接缝，调用方统一通过 `TrackMetadataRepository` 交互。
_Avoid_: Metadata Parser, Artwork Helper, Tag Downloader

**Playback Intent**:
会话层定义的一组原子播控意图，涵盖 play、pause、togglePlayPause、stop、seekTo、skipToNext、skipToPrevious，是所有外部播控来源（系统通知栏 Action、蓝牙/耳机物理按键、应用内 UI）向 `MusicPlayerAppSession` 派发操作的标准信道。任何绕过该接缝直接操作底层 `AudioPlayerEngine` 的调用均为架构违规。
_Avoid_: Engine Command, Player Action, Direct Player Call

**Cold Restore**:
应用进程被系统终止后重新冷启动时，`MusicPlayerAppSession` 从持久化 `Playback Session State` 还原播放队列、曲目索引与毫秒级进度，并在底层引擎处于 `Idle` 状态时重新调用 `loadTrackIntoEngine` 完成物理流装载的完整恢复流程。外部 Playback Intent（如通知栏播放键）到达时若绕过会话层直接调用底层引擎，该流程将被静默跳过，导致用户点击播放后无声。
_Avoid_: App Resume, Session Restore, Player Reload

**Session-Managed Queue Progression**:
由应用会话层集中管控与仲裁的播放队列流转机制。底层媒体播放引擎仅作为当前曲目或双轨预热的流式渲染容器，丧失自主跨曲跳过权。
_Avoid_: Autonomous Player Transition, Engine Skip, Internal Playlist Advancement

**Streaming Playback Pipeline**:
专职负责当前物理音频流与预卷流在播放内核中的解码、PCM 数据拼接、自适应弱网缓冲与状态广播的底层渲染管道。彻底剥离任何播放列表管理、洗牌置换与跨曲调度职责，以纯单流/双流模型向上层提供确定性的音频流回放能力。
_Avoid_: Playback Queue Controller, Multi-Track Engine, Autonomous Player

**Premature EOF Guard**:
在纯流式无磁盘缓存回放过程中，由底层数据源（传输字节计数比对）与上层会话层（播放进度与音轨实际时长比对）协同构成的双重熔断防线，杜绝因网络闪断或连接静默关闭被误判为曲目正常播放结束而诱发意外跳集。
_Avoid_: End-of-Stream Hack, Fake Completion, Silent Disconnect

**Transparent Range Reconnection**:
底层流式数据源在遭遇读取超时或网络抖动时，在数据传输层记录字节偏移量并以退避重试自动发起 HTTP Range 请求接续流数据的自愈机制。对音频解码器及播放器状态机完全透明，不触发解码管道销毁，不发生进度跳变。
_Avoid_: Player Re-prepare, Seek Reload, Fatal Source Error

**Deferred Foreground Demotion**:
在音频播放暂停时延迟注销前台服务身份与系统通知的宽容退避机制。在设定的宽容窗口期内维持前台通知栏与高优先级进程状态，超时未恢复才释放前台身份，消除短暂停顿后被系统快速判定为闲置杀死的风险。
_Avoid_: Instant Demotion, Service Timeout, Kill Protection

**Dual Streaming Hardware Locks**:
在纯在线流式音频回放过程中由 CPU 唤醒锁（WakeLock）与 Wi-Fi 高性能锁（WifiLock）构成的硬件级休眠防御体系。回放与数据缓冲时共同加锁防止系统息屏后 CPU 停步及 Wi-Fi 芯片降频节能，暂停时立即释放以兼顾设备能耗。
_Avoid_: Keep Alive Lock, Power Lock, Network Guard

**OEM Kill Detection & Whitelist Banner**:
在冷启动时比对上次持久化会话异常终止状态以识别系统强制查杀，并在播放界面展示可跳转至系统电池优化及厂商自启动/神隐模式配置的交互横幅。
_Avoid_: Battery Dialog, Permission Pop-up, Kill Warning

**Dual-Track Battery Whitelist Flow**:
在全屏播放器常驻横幅中集成的“原生弹窗快速授权 + 直达应用信息电池策略设置”双轨保活交互机制。既允许用户一键调起 AOSP 原生忽略电池优化弹窗完成 Doze 白名单加入，又面向所有设备提供直达本应用详情页入口与操作指引，引导用户将系统电池策略彻底切换为「无限制（Unrestricted）」，对标主流播放器实践，彻底免疫锁屏降频与进程强杀。
_Avoid_: Single Popup Flow, Xiaomi-Only Settings

**Becoming Noisy Protection**:
当音频输出设备（蓝牙音箱/耳机、有线耳机）由于断连、拔出或掉电导致输出流即将不可预期地切换至手机内置扬声器时，应用层主动拦截 `ACTION_AUDIO_BECOMING_NOISY` 广播并触发安全暂停（`appSession.pause()`）的系统级防护机制。
_Avoid_: Unplug Pause, Disconnect Guard, Mute Event

**Audio Routing Observer**:
通过 Android `AudioDeviceCallback` 与 `AudioRouting` 建立的应用层音频输出端口感知器，实时追踪物理发声节点并在路由发生漂移时协同底层 AudioSink 刷新缓冲区，防止非标准采样率重混音通道挂起。
_Avoid_: Bluetooth Monitor, Device Tracker, Headset Listener

**Canonical Sample Rate Resampling**:
对齐主流流媒体播放器的音频管道防御规范：在进入系统 `AudioTrack` 之前，由播放引擎对低于 `44,100 Hz` 的音频流（如 22050Hz 评书）统一升采样至标准 `44,100 Hz` 双声道 PCM，彻底规避各厂商 Android HAL/DSP 软重采样器在路由切换时的时钟错位与硬件假死缺陷。
_Avoid_: Audio Upsampling, System Resample, Driver Convert

### UI & Navigation

**Primary Navigation Bar**:
应用底部的顶层导航容器，承载「曲库浏览」（Directory Browser）与「服务器管理」（Server Management）顶层核心目的地。
_Avoid_: Bottom Bar, Tab Bar, Footer Menu

**Docked Mini-Player**:
悬浮驻留在 Primary Navigation Bar 之上（或屏幕底部安全区之上）的全局持久化浮动卡片播控胶囊，具备曲目摘要、播放控制与上滑/点击呼出全屏播放器的能力，在应用内任何页面导航切换时均不中断。
_Avoid_: Bottom Player, Small Player, Playback Bar

**Full Player Sheet**:
全屏沉浸式大播放器视图，采用极致纯净留白顶栏（轻量拖拽手柄，无冗余缩小按钮与标题文本）与自适应封面氛围渐变背景，集成大封面唱片、进度拖拽、播放模式控制与高保真音频规格详情（Hi-Res / 解码采样率 / 码率胶囊），通过轻量顶部拖拽条及下滑手势收起，并支持左右滑动无缝切换至逐行同步高亮歌词页。
_Avoid_: Player Activity, Big Player, Music Detail

**Immersive Chrome**:
基于 Android Edge-to-Edge 规范的全沉浸式系统窗口体系，应用内容完整延伸至状态栏与手势导航条后方，并根据当前主题色调与封面氛围光晕自适应反转状态栏与导航栏图标明暗反差。
_Avoid_: Transparent Bars, Fullscreen Hack

**Directory Breadcrumb Strip**:
由 Material Design 3 胶囊 Chip 构成的可水平滑动的目录路径条，直观展示自活跃服务器根目录至当前层级的继承关系，支持点选任意祖先目录瞬时跳转。
_Avoid_: Path Bar, Folder Tree, Location Bar

**Audio Quality Badge**:
音频曲目列表中标识音频编码格式（如 FLAC、MP3、WAV、WMA）及无损/高解析度层级的视觉标签胶囊。
_Avoid_: Format Icon, Audio Tag

**Equalizer Track Indicator**:
媒体库列表中标识当前回放状态曲目的动态三柱等化器跳动脉冲视觉指示，用于在切回目录浏览时秒级定位当前正在播放的音轨。
_Avoid_: Playing Icon, Waveform GIF

**Adaptive Themed Icon**:
遵循 Android Material You 规范的自适应启动图标体系，由极简黑胶唱片与云端流媒体意象前景、自适应背景及单色主题层（Monochrome）构成，自适应跟随系统壁纸动态调色板着色。
_Avoid_: App Logo, Static Icon

**Playback Queue Bottom Sheet**:
由应用底部弹出的全高/半高播放队列管理模态抽屉，支持平滑滚动定位当前播放曲目、点选秒级切歌、显示曲目来源及播放模式切换。
_Avoid_: Queue Dialog, Playlist Popup, Track Selector

**Bilingual Lyrics View**:
集成在 Full Player Sheet 内支持主译双语平行排版、单时间戳高亮对齐、逐行平滑滚动及点击精准 Seek 交互的沉浸式歌词呈现容器。
_Avoid_: Lrc View, Subtitle View, Text Reader

