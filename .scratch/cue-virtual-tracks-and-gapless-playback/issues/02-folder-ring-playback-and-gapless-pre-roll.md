# 02: 文件夹环形循环（Folder Ring）与流式预卷无缝缓冲（Pre-roll Gapless）

**What to build:**
针对普通独立音频文件（如常规目录中的多首 MP3/FLAC），在纯流式、无本地磁盘缓存的约束下消灭切歌时的网络重握手与解码器重置间隙。配置 OkHttp 专用的 Keep-Alive 长连接池，并在 ExoPlayer 中调整流媒体缓冲策略，使上一首播放接近尾声时底层提前向同一服务器建立下一首的前向流式缓冲区与格式探针，实现两首曲目交界处的 0 毫秒物理级无缝衔接。同时将播放队列严格约束在当前活跃目录内，列表循环模式下播完最后一首自动无缝回环至第一首（Folder Ring 闭环），绝不跨越至兄弟或父级目录。

**Blocked by:**
None (can start immediately)

**Status:** ready-for-agent

- [ ] 在 `OkHttpWebDavClient` 与流式适配器中配置专用的流式 `ConnectionPool`，确保同服务器请求复用活跃的 TCP/TLS 链路，降低切歌握手时延至 0ms。
- [ ] 调整 `Media3AudioPlayerEngine` 的 `DefaultLoadControl`，配置 10 秒回退缓冲区与前向预卷缓冲窗口，确保当前音频播至末尾前提前触发下一音轨的数据装填。
- [ ] 在 `DefaultAudioSink` 与格式提取器保持同规格的前提下，实现跨文件连续 PCM 数据流馈送，消除声学空白停顿。
- [ ] 确立 `Folder Ring Playback` 契约：当播放模式为列表循环且队列到达末尾时，无缝衔接当前目录的第一首，严格禁止扫描兄弟目录或跨目录跳转。
- [ ] 编写测试验证：列表末尾循环回环逻辑、连接池参数与 LoadControl 缓冲阈值校验。
