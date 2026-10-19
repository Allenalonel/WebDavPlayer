# 03: 系统媒体输出切换器双轨路由感知与发声管道自愈 (Media Output Switcher Dynamic Routing Observer and AudioSink Flush Resynchronization)

**What to build:**
升级 `AudioRoutingObserver` 为双轨感知架构，结合物理硬件插拔（`AudioDeviceCallback`）与系统发声管道路由监听（捕获用户在系统控制中心/音量浮窗“媒体输出切换器”中的手动设备流转）。设备变动时毫秒级触发底层 `AudioSink.flush()`，重新握手驱动时钟，消除时钟错位、声道死锁与静音空跑；同时为 `CanonicalResamplingAudioProcessor` 增加非 16-bit 浮点 PCM 音频流的安全直通保护，杜绝解码崩溃。

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `AudioRoutingObserver` 构建双轨路由感知器，除原有的 `AudioDeviceCallback` 外，增加对活跃发声通道（`AudioRouting.OnRoutingChangedListener`）的动态路由监听，准确捕获系统“媒体输出切换器”手动流转。
- [ ] 无论是物理设备增减还是系统手动切换输出端口，均触发底层 `activeAudioSink?.flush()` 与时间戳校准器重置，实现无感自愈。
- [ ] 优化 `CanonicalResamplingAudioProcessor`，对非 16-bit（如 32-bit Float PCM）或高采样率流实行安全的直通与自适应降级，避免抛出未经捕获的崩溃异常。
- [ ] 编写测试覆盖动态路由切换触发 AudioSink 刷新以及非常规音频格式直通保护的断言。
