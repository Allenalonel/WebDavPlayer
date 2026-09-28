# Dual-Source Artwork Pipeline and Industrial LRC Normalization

## Context

在 WebDAV 远程音频流媒体场景中，原有的音乐封面与歌词处理存在两个显著缺陷：
1. **封面截断与来源单一**：原实现仅使用固定 128KB 的 HTTP Range 拉取文件头部，而高解析度内嵌封面（APIC / PICTURE）普遍介于 200KB 至 2MB 之间，导致字节被截断损坏或被解析器放弃；同时，大量 NAS 及 Hi-Fi 分轨音频库并不内嵌封面，而是依赖同目录下的 `cover.jpg` 或 `folder.jpg`。
2. **行内词级歌词重复展开**：带有卡拉OK或逐字时间戳（Enhanced LRC）的歌词行内含有多个时间标签，原解析器将行内每一个时间戳均展开为一个完整句，导致同一句歌词在上下行堆叠显示多次；且同时间戳的双语翻译（如中英对照）被当作割裂的独立行，缺乏视觉层级。

## Decision

1. **双源封面提取与自适应 512KB 分片（Dual-Source Artwork & Adaptive Range）**：
   - 针对内嵌元数据，将首包 HTTP Range 扩大至自适应 512KB（`0..524287`），覆盖 90%+ 的主流高清封面；若 ID3 标头指示 `tagSize > 512KB`，按需发起第 2 次精准 Range 补齐。
   - 实现双源回退机制：优先提取音频内嵌封面；若无内嵌封面，则在同目录检索同名图片（`${basename}.jpg`/`.png`）或标准专辑封面（`cover.jpg`/`.png`、`folder.jpg`），下载并经由 `CoverArtStorage` 压缩缓存为缩略图。
2. **工业级 LRC 规整与双语时间对齐（Industrial LRC Normalizer & Bilingual Alignment）**：
   - 遵循业界主流播放器（如 Poweramp、Symfonium）的歌词时间线处理标准：
     - **行首多时间戳**（Repeated Chorus）：保留副歌跨段落展开逻辑，按各时间戳生成对应时段的歌词项。
     - **行内/词级时间戳**（Inline / Word-level）：一律剥离清洗，提取整句单一文本，绝对不因行内时间戳产生重复歌词行。
     - **近邻去重**：对相邻行时间差低于 300ms 且文本相同的项自动去重。
   - **双语对齐模型**：将相同或相差微小（<300ms）的两行文本对齐合并为同一 `LyricLine` 的主歌词（mainText）与译文（translation），UI 采用主副双行渲染，点击时同步跳转。

## Consequences

- 彻底根除内嵌封面解码失败及重复歌词堆叠问题。
- 兼容 NAS 及流媒体导出的主流 LRC 格式（包括逐字卡拉OK歌词与双语翻译）。
- 目录级封面仅在曲目无内嵌时发起单次轻量探测并缓存，不会造成额外的网络风暴。
