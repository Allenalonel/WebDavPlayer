# 03: 系统媒体输出切换器双轨路由感知与发声管道自愈 (Media Output Switcher Dynamic Routing Observer and AudioSink Flush Resynchronization)

**What to build:**
升级 `AudioRoutingObserver` 为双轨感知架构，结合物理硬件插拔（`AudioDeviceCallback`）与系统发声管道路由监听（捕获用户在系统控制中心/音量浮窗“媒体输出切换器”中的手动设备流转）。设备变动时毫秒级触发底层 `AudioSink.flush()`，重新握手驱动时钟，消除时钟错位、声道死锁与静音空跑；同时为 `CanonicalResamplingAudioProcessor` 增加非 16-bit 浮点 PCM 音频流的安全直通保护，杜绝解码崩溃。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] `AudioRoutingObserver` 构建双轨路由感知器，除原有的 `AudioDeviceCallback` 外，增加对活跃发声通道（`AudioRouting.OnRoutingChangedListener`）的动态路由监听，准确捕获系统“媒体输出切换器”手动流转。
- [x] 无论是物理设备增减还是系统手动切换输出端口，均触发底层 `activeAudioSink?.flush()` 与时间戳校准器重置，实现无感自愈。
- [x] 优化 `CanonicalResamplingAudioProcessor`，对非 16-bit（如 32-bit Float PCM）或高采样率流实行安全的直通与自适应降级，避免抛出未经捕获的崩溃异常。
- [x] 编写测试覆盖动态路由切换触发 AudioSink 刷新以及非常规音频格式直通保护的断言。

## Comments

### Resolution
- `AudioRoutingObserver` 升级为双轨感知架构：
  1. 硬件层：保留基于 `AudioDeviceCallback` 过滤感知物理输出设备插拔（`isSink == true`）；
  2. 路由层：增加 `AudioRouting.OnRoutingChangedListener`（API 24+）与 `MediaRouter2.TransferCallback`（API 30+），动态捕获用户在系统控制中心/媒体输出切换器中的手动发声设备迁移；
  3. 提供 `attachAudioRouting` / `detachAudioRouting` 接口与生命周期联动绑定。
- 在 `Media3AudioPlayerEngine` 中暴露 `attachAudioRouting` / `detachAudioRouting`，并在路由变更时触发 `canonicalAudioProcessor.flush()` 与 `activeAudioSink?.flush()`，清空音频处理器残留与底层缓冲区，重置驱动时钟并实现无感自愈。
- `PlaybackSessionHost` 同步透传 `attachAudioRouting` 与 `detachAudioRouting` 委派接口。
- `CanonicalResamplingAudioProcessor` 实现非破坏性弹性直通机制：对非 16-bit PCM（包括 32-bit Float PCM、24-bit PCM、32-bit int PCM）或异常格式返回 `AudioFormat.NOT_SET` 处于非活跃直通模式，并在配置 Sonic 异常时自适应降级，杜绝播放器管道崩溃抛错。
- 编写并执行单元测试，808 项单元测试全量通过：
  - `CanonicalResamplingAudioProcessorTest`：覆盖 Float PCM、24-bit PCM、32-bit PCM 直通保护与 16-bit 格式规范化重采样；
  - `AudioRoutingObserverTest`：覆盖双轨路由观察者启动、挂载/卸载、设备回调与系统媒体路由切换事件；
  - `Media3AudioPlayerEngineTest` 与 `PlaybackSessionHostTest`：覆盖路由变更刷新 AudioSink / AudioProcessor 与路由透传委派。
