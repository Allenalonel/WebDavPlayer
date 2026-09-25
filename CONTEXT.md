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

### Playback & Library

**Audio Track**:
具有可解码音频流特性的远程音频文件（如 MP3、FLAC、WAV、WMA 等格式）。
_Avoid_: Song, Music, Audio File

**Track Metadata**:
音频曲目的描述性标签信息，包含曲目标题、艺术家、专辑名称、时长及内嵌封面图。
_Avoid_: ID3, Tag, Audio Info

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

**Lyrics Source**:
与曲目关联的歌词提供方，支持从远程同目录同名 `.lrc` 文件解析或从音频内嵌标签中提取时间轴文本。
_Avoid_: Lyric File, Lrc Text

**Audio Focus**:
Android 系统级音频焦点协商状态，用于响应来电暂停、挂断恢复及系统提示音时的临时音量压低（Ducking）。
_Avoid_: Sound Priority, Volume Interrupt
