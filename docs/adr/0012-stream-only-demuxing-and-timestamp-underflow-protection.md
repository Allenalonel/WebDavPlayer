# Stream-Only Demuxing Pipeline and Timestamp Underflow Protection

## Context

在利用 AndroidX Media3 (ExoPlayer) 配合自定义 Native FFmpeg 扩展（`ffmpeg_demuxer.cc`）回放远程 WebDAV 上的 WMA (ASF 封装) 音频文件时，遇到了三个互相交织的深层架构与稳定性问题：

1. **虚假随机 Seek 破坏流式状态机**：
   在单向只读网络数据源（HTTP/WebDAV 流）上，初始实现向 `avio_alloc_context` 注册了 `seek_callback`。一旦注册了非空函数指针，FFmpeg 会将数据源误判为支持任意前滚和后退的本地随机文件（`AVIO_SEEKABLE_NORMAL`）。当解复用器尝试在已预读的缓冲区内向后调整指针时，因网络连接无法物理回退返回 `-1`，导致 FFmpeg 误报 `I/O error (-5)` 并提前宣告 EOF，引发展现为“刚开播立即静音”、“连续疯狂切歌跳曲”等问题。

2. **32 位时间戳无符号计算下溢（Preroll Underflow Glitch）**：
   ASF 规范要求第一个音频帧的时间戳应减去文件头声明的预卷时间：`PTS = SendTime - Preroll`（例如 $0 - 3100\text{ms} = -3100\text{ms}$）。然而在 32 位无符号整数环境（`uint32_t`）计算时发生下溢，产生形如 $4,294,964,196\text{ms}$（约 1193 小时）的巨大时间戳。ExoPlayer 音频渲染器将其视作未来帧而拒绝向 `AudioTrack` 写入采样，导致开播彻底静音；用户拖拽跳转后由于基准偏移依然存在，进度条赫然显示为异常的 `1193:07:07`。

3. **解码对齐尺寸污染与残片拦截副作用**：
   `FfmpegAudioDecoder` 将 `format.maxInputSize` 作为解码器的 `block_align` 传递，导致当试图扩大输入缓冲区时，解码器的块对齐参数被错误放大；若解码入口对小于对齐尺寸的包进行盲目丢弃，将导致正常音频帧全部丢失并在跳转时永久卡死在 `STATE_BUFFERING`。

## Decision

参考行业成熟开源流媒体播放器（如 VLC、Symfonium）的设计准则，重构 Native 流式管道与时间戳治理体系：

1. **主流流式单向驱动模式（Stream-Only Demuxing Pipeline）**：
   - 在 `ffmpeg_demuxer.cc` 创建 `AVIOContext` 时，**将 `seek` 回调显式置为 `nullptr`**。
   - 对外担保：向 FFmpeg 声明当前数据源为纯流式单向管道（Sequential Stream）。当解复用器跳过数据包间隔的填充（Padding）或元数据时，强制通过常规读操作（`read_packet_callback`）顺序消费并丢弃多余字节，坚决禁止调用 `avio_seek`。
   - 彻底根除因流式管道不可倒退引发的 `I/O error (-5)` 报错、开播解包中断与级联跳曲。

2. **毫秒级主动跳转与状态机对齐（Active Seek Separation）**：
   - 区分内部数据包消费与用户主动 Seek 行为。当用户在 UI 拖动进度条时，由 ExoPlayer 重新计算目标字节偏移发起全新 HTTP Range 请求，通过 `AsfExtractor.seek()` 触发 JNI `nativeSeek()`，主动执行 `avio_flush()` 并重置 `asf_o` 解复用状态机，兼顾流式稳定性与拖拽跳转响应度。

3. **32 位时间戳下溢归一化（Native + Kotlin 双层纵深防御）**：
   - **Native 第一道防线**：在 `ffmpeg_demuxer.cc` 的 `nativeReadFrame` 中，拦截所有大于 `0x80000000LL`（约 24.8 天）的时间戳，将其截断回 `int32_t` 有符号值并对开播预卷负值 clamp 归一化为 `0`，消除 1193 小时异常源头。
   - **Kotlin 第二道防线**：在 `AsfExtractor.kt` 的 `readNativeFrame` 中增加模除保护，确保传递给 ExoPlayer 的时间戳始终处在 $[0, \text{duration}]$ 的合法区间。

4. **解码器块对齐隔离与整洁流控制**：
   - 严格保证解码器拿到的 `block_align` 始终是容器原生声明的固定对齐尺寸，杜绝被外界缓冲区扩容所污染。
   - 移除 Extractor 中人为抛出 `EOFException` 的干预逻辑，恢复 ExoPlayer 标准的数据源错误处理机制。

## Consequences

- **开播即时出声**：消除 `seek_callback` 引发的 `EIO (-5)` 和时间戳下溢，点击 WMA 曲目后立即起播发声，彻底告别“开播必须手动拖拽进度条才有声音”的缺陷。
- **进度条显示精准**：时间戳从 0 稳定单调递增，彻底杜绝 `1193:xx:xx` 等跨度数千小时的格式化溢出。
- **跳转平滑无卡死**：主动 Seek 逻辑与流式解包状态机正交隔离，拖拽进度条后毫秒级定位出声，不会陷入 `STATE_BUFFERING` 死循环。
- **架构对齐工业标准**：C++ Native 流媒体交互完全契合 FFmpeg 官方建议的主流远程流式消费范式。
