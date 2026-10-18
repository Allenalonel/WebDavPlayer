# 03: 音频路由观察者与多设备自愈刷新 (Audio Routing Observer & Sink Flush Resynchronization)

**What to build:** 当手机连接着蓝牙音箱播放时，如果后台另一台已配对的蓝牙设备（如误触唤醒的耳机盒）突然抢占系统 A2DP 媒体音频路由，播放器顺应系统的路由分配策略将流平滑迁移到新设备，并通过 `AudioDeviceCallback` / `AudioRouting.OnRoutingChangedListener` 实时捕获发声端点迁移事件，主动向底层 `AudioSink` 触发缓冲区重置（Flush）与时间戳校准重新握手。消除不同设备缓冲延迟差异造成的硬件管道挂起与静默丢帧，确保新设备在切换后立刻发出正常声音，彻底告别静音假死。

**Blocked by:** 01 (Becoming Noisy 防意外外放保护与会话暂停), 02 (规范化音频重采样管道)

**Status:** claimed

- [ ] 在 `PlaybackSessionHost` / `Media3AudioPlayerEngine` 中接入 `AudioDeviceCallback` 或 `AudioRouting.OnRoutingChangedListener`，监听当前发声设备路由变更。
- [ ] 当活动音频输出设备发生切换（如从蓝牙设备 A 迁移到蓝牙设备 B 或有线设备）时，顺应系统路由切换，同时主动通知底层 `AudioSink` 触发缓冲区清空（Flush）与时钟同步重置。
- [ ] 确保在路由迁移与缓冲区刷新过程中，应用会话层播放状态（`PlaybackState`）与当前进度（`currentPositionMs`）保持平滑稳定，不触发多余的跳曲或假缓冲。
- [ ] 编写并执行单元测试，模拟多设备路由迁移事件，验证 `AudioSink` 刷新流程被正确调用，且播放进度与时钟不发生非预期跳变或崩溃。
