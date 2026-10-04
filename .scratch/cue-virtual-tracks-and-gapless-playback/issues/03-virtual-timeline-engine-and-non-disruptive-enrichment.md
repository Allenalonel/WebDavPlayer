# 03: 虚拟时间轴映射引擎（Virtual Timeline Engine）与零打扰元数据广播

**What to build:**
在播放会话层（`MusicPlayerAppSessionImpl`）构建单物理流与虚拟分轨的映射桥梁。保持整轨音频的单一 HTTP 连接持续回放，将底层播放器报告的长音频全局物理进度（`globalPositionMs`）实时换算为当前分轨的局部相对进度（`virtualPositionMs`），并向大播放器与 MiniPlayer 暴露。当播放进度跨越分轨时间点时，自动推进分轨索引并触发 `Non-Disruptive Metadata Enrichment`，将新分轨的曲目标题、表演者与音轨号推送至系统 MediaSession 与通知栏，绝不重置底层播放器时间线，达成物理级绝对无缝；接管上一曲、下一曲与进度拖拽（Seek），直接在物理流内进行目标毫秒重定位。

**Blocked by:**
01: 纯内存 CUE 状态机解析器与 Virtual Track 领域实体

**Status:** ready-for-agent

- [ ] 在播放会话层建立 `VirtualTimelineEngine`，维护当前激活分轨列表、当前分轨索引及物理流起始偏移量。
- [ ] 相对进度与时长双向映射：`virtualPositionMs = (globalPositionMs - activeTrack.startTimeMs).coerceAtLeast(0L)`，`virtualDurationMs = activeTrack.durationMs`。
- [ ] 自然分轨跨越检测（Boundary Crossing）：高频 Ticker 检测到 `globalPositionMs >= activeTrack.endTimeMs` 时，原子更新分轨索引，并向系统 MediaSession 广播分轨元数据，音频连续流不产生任何打嗝或杂音。
- [ ] 接管上一曲与下一曲控制：下一曲跳转至 `nextTrack.startTimeMs`；上一曲若已播放大于 3 秒则跳回当前分轨起始毫秒，小于等于 3 秒则跳回上一分轨起始毫秒。
- [ ] 接管进度条拖拽（Seek）：UI 拖拽的相对毫秒转换为 `activeTrack.startTimeMs + seekOffsetMs`，并钳制在当前分轨区间内。
- [ ] 编写纯逻辑单元测试：时间轴换算公式、分轨自然交界事件触发、Seek 边界保护及上下首跳转计算。
