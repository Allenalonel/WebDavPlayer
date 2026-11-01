# 01: 纯内存 CUE 状态机解析器与 Virtual Track 领域实体

**What to build:**
实现轻量级、无第三方库依赖的流式 CUE 文本解析器与纯领域 `VirtualTrack` 实体。能够从 WebDAV 同目录 `.cue` 文本中精确提取 `FILE`、`TRACK`、`TITLE`、`PERFORMER` 与 `INDEX 01` 时间戳，并将 CUE 标准的 `分:秒:帧`（75 帧/秒）换算为精确的毫秒时间区间。所有解析结果均瞬态保存在内存中，退出或切歌即释放，绝不向本地持久化磁盘写入任何缓存文件，严格遵守纯流式、零本地磁盘沉淀的设计准则。

**Blocked by:**
None (can start immediately)

**Status:** completed

- [x] 在 `domain/model/` 中建立纯 Kotlin 的 `VirtualTrack` 领域模型（包含轨道序号、标题、表演者、起始毫秒、结束毫秒及归属大音频路径），无 Android 框架依赖。
- [x] 实现 `CueParser` 状态机，能够稳健解析常见 CUE 变种格式（含双引号、无引号、前后空格、REM 描述行等）。
- [x] `INDEX 01` 时间戳精确换算：1 秒 = 75 帧，换算公式为 `(min * 60 + sec) * 1000 + (frames * 1000 / 75)`。
- [x] 具备完备的容错与降级机制：当 CUE 缺少 FILE 标签、时间戳错乱或格式破损时，安全返回空列表或可用子集，绝不抛出未捕获异常。
- [x] 编写纯单元测试覆盖：标准 CUE 解析、多音轨时间区间计算、边界时间精度验证及非法语法鲁棒性测试。
