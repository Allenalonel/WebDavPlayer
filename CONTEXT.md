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
播放队列加载或预加载未解析音轨时触发的自愈机制：后台异步发起受限 Range 元数据提取并反向写回本地持久化数据库与活动内存队列，自动修补缺失的专辑标签与时长。
_Avoid_: Manual Sync, Cold Cache, Static Database

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

