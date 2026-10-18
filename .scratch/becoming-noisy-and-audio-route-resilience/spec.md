# Spec: 音频防意外外放保护、路由自愈与规范化重采样 (Becoming Noisy Protection, Audio Route Resilience & Canonical Resampling)

Status: ready-for-agent

## Problem Statement

作为在 Android 移动端使用 WebDavPlayer 欣赏远程音乐与有声书的用户，在日常连接蓝牙音箱、车载音响或有线耳机收听时，经常遇到以下三类严重的声学与播放体验问题：

1. **异常断开意外大声外放（Becoming Noisy 保护缺失）**：
   当正在播放的蓝牙音箱超出连接范围、电量耗尽突发关机，或有线耳机被意外拔出时，播放器没有及时暂停，而是盲目跟随系统输出重定向到手机内置扬声器继续大声外放。在办公室、图书馆或深夜等安静环境中，这一行为会造成极大的尴尬与隐私困扰。
2. **多蓝牙设备抢占引发静音空跑（Phantom Progress Bar & Silent Playback）**：
   当手机连接蓝牙音箱播放时，如果后台另一台已配对的蓝牙设备（例如收纳盒触点不良闪连的真无线耳机）意外回连，系统底层 A2DP 会将音频流转移。此时原有音箱瞬间无声，但播放界面上的进度条却依然正常向前空跑，甚至在静音状态下一路走完整集音频，并触发下一曲自动连播外放，导致用户漏听内容且难以找回进度。
3. **低采样率音频（如 22050Hz 评书）在蓝牙重混音时诱发硬件假死（HAL Underrun Stalling）**：
   在收听早期评书、相声、有声书等特殊采样率（如 22050Hz、16000Hz）的音频时，蓝牙 A2DP 协议栈强制要求 44100Hz/48000Hz 标准规格。当蓝牙设备发生连接抖动或路由迁移时，Android 厂商底层（如高通平台）软重采样器容易发生时钟对齐错位，导致硬件发声管道挂起静音，而应用层 AudioTrack 消费指针继续递增，造成持续无声假死。

## Solution

在应用层与流式渲染管道建立三位一体的防御性音频保护体系，完全对齐网易云音乐、Spotify 等主流商业播放器的工程标准与 ADR-0018 补遗规范：

1. **Becoming Noisy 防意外外放系统级拦截**：
   - 在前台播放宿主（`PlaybackSessionHost`）中，仅在播放处于活跃状态（Playing）时动态注册 `AudioManager.ACTION_AUDIO_BECOMING_NOISY` 广播监听器，在暂停或空闲时立即注销。同时开启底层播放引擎的 `.setHandleAudioBecomingNoisy(true)` 保底。
   - 监听到意外断开事件时，严格遵循 ADR-0018 的单向意图架构，向会话层派发 `appSession.pause()`，停止进度时钟、取消弱网看门狗、持久化保存毫秒级播放进度。
   - **保守安全重连策略**：蓝牙音箱重新连回后，保持暂停状态，绝不自动发声，必须由用户主动点击播放。
2. **多设备路由切换跟随与 AudioSink 缓冲区自愈**：
   - 顺应系统的多设备 A2DP 仲裁决策，接入 `AudioDeviceCallback` / `AudioRouting.OnRoutingChangedListener` 感知输出设备变动。
   - 检测到发声设备迁移时，主动向底层 `AudioSink` 触发缓冲区刷新（Flush & Resynchronize），迫使驱动层重新握手时钟，确保切换后新设备立刻正常发声，消除挂起死锁。
3. **应用层规范化重采样（Canonical Sample Rate Resampling）**：
   - 在 `Media3AudioPlayerEngine` 的音频渲染前置管道中构建重采样处理器（基于 `SonicAudioProcessor` 或标准音频处理链）。
   - 凡采样率低于 `44,100 Hz` 的音频流（如 22050Hz 评书），在进入系统 `AudioTrack` 之前，由应用层在内存中无损超采样规范化为标准 `44,100 Hz` 双声道 PCM 流；标准音频（>= 44.1kHz）则 100% 直通保持原汁原味。
   - 彻底绕过手机厂商底层驱动脆弱的插值重采样器，从源头消灭高通 HAL 蓝牙重混音卡死的前提条件。

## User Stories

1. 作为使用蓝牙音箱收听有声书的用户，当我带手机走出蓝牙信号范围导致音箱异常断开时，我希望播放器立即自动暂停，以便音频不会突然从手机扬声器大声外放。
2. 作为在图书馆使用有线耳机的用户，当耳机插头被意外拽出时，我希望播放器在毫秒级内自动停止播放，以便不会打扰周围的人。
3. 作为收听评书长音频的用户，当播放因蓝牙断开而暂停时，我希望进度条精确停留在断开时的毫秒位置，以便我下次收听时能够无缝接续。
4. 作为在安静办公室工作的用户，当我的蓝牙耳机因低电量突发关机时，我希望播放器自动拦截外放并将状态切换为暂停，以便避免公共场合的外放尴尬。
5. 作为收听断开后走回房间的用户，当蓝牙音箱重新自动连接上手机时，我希望播放器保持暂停状态而不是自顾自播放，以便我能在准备好之后再手动开始收听。
6. 作为拥有多台已配对设备的用户，当正在通过音箱播放时另一副耳机意外连上，我希望播放器能够跟随系统将声音平滑传给新设备，以便新设备能够立刻正常出声。
7. 作为收听 22050Hz 老旧相声音频的用户，当我通过蓝牙耳机播放时，我希望声音清晰稳定且不卡死，以便能享受正常流畅的回放体验。
8. 作为收听低采样率有声书的用户，在蓝牙音箱发生断连或重连时，我希望播放器不会陷入“进度条空跑、实际静音”的假播放状态，以便我不会错失几十秒的内容。
9. 作为锁屏收听的用户，当蓝牙设备断开触发 Becoming Noisy 暂停时，我希望锁屏播控卡片和通知栏立即同步更新为暂停图标，以便 UI 状态与实际发声严格一致。
10. 作为电量敏感型用户，当蓝牙意外断开触发暂停时，我希望 CPU 唤醒锁与 Wi-Fi 锁立即释放，以便手机不会在后台持续消耗电量。
11. 作为关注数据安全的用户，当耳机拔出触发自动暂停时，我希望播放器立即异步将当前位置与会话状态刷盘至本地持久化数据库，以便即便进程后续被系统杀除进度也不会丢失。
12. 作为系统性能敏感型用户，我希望 Becoming Noisy 广播接收器仅在音乐实际播放时处于注册状态，以便应用在暂停或空闲时不接收和处理多余的系统广播。
13. 作为无损音乐爱好者，当播放 96kHz 或 192kHz 高解析度 FLAC 时，我希望规范化重采样器保持直通不降采样，以便无损母带的高保真音质得以完整保留。
14. 作为使用蓝牙耳机按键的用户，当播放器因 Becoming Noisy 自动暂停后，我希望单击耳机按键能够正常恢复播放，以便操作逻辑符合直觉。
15. 作为拥有接触不良耳机盒的用户，当耳机在盒子里发生数秒内的连续闪连闪断时，我希望播放器安全保持暂停状态，以便手机不会在扬声器和耳机之间来回抽搐外放。
16. 作为目录循环收听整套评书的用户，当一集结束自然顺延到下一集时，我希望即使音频采样率是非标准值也能连续平稳播放，以便免除频繁手动操作的烦恼。
17. 作为弱网环境下的流媒体用户，当蓝牙断连与网络重试重叠发生时，我希望音频设备断连暂停优先仲裁，以便数据源停止不必要的高频网络拉取。
18. 作为使用车载蓝牙收听的用户，当车辆熄火导致蓝牙音频断开时，我希望播放器能够立即挂起，以便下一次点火时能从相同位置继续。
19. 作为在后台长时间收听的用户，当蓝牙断开触发暂停后，我希望前台服务优雅启动延迟退避定时器（Deferred Demotion），以便通知栏在合理时间内保持常驻供我恢复。
20. 作为夜间定时收听的用户，当蓝牙音箱因定时关机自动断电时，我希望播放器不会在凌晨突然使用扬声器外放惊醒家人，以便提供最安心的使用体验。

## Implementation Decisions

### 1. 播放宿主层：`PlaybackSessionHost` 纳管 `BecomingNoisyReceiver` 动态生命周期
- 在 `PlaybackSessionHost` 中声明私有的 `BroadcastReceiver`，专职过滤 `AudioManager.ACTION_AUDIO_BECOMING_NOISY`。
- **动态生命周期绑定**：
  - 在 `handlePlaybackStateChanged` 转换至 `PlaybackState.Playing` 或前台服务提升（`elevateForeground`）时注册广播；
  - 在转换至 `PlaybackState.Paused`、`PlaybackState.Idle`、`PlaybackState.Ended` 或服务降级销毁（`demoteForeground`）时注销广播，杜绝内存泄漏与空闲误触。
- **意图单向派发**：广播收到回调后，100% 经由 `appSession?.pause()` 派发，执行 ADR-0018 约定的完整会话级暂停序列。

### 2. 播放引擎层：`Media3AudioPlayerEngine` 配置双重防御与路由感知
- **ExoPlayer 内核保底**：
  在 `ExoPlayer.Builder` 构建时追加 `.setHandleAudioBecomingNoisy(true)`，为引擎层提供底层设备脱离保障。
- **音频路由监听（Audio Routing Listener）**：
  通过 `DefaultAudioSink` 或在初始化时接入 `AudioDeviceCallback` / `AudioRouting.OnRoutingChangedListener`，捕获蓝牙设备增减与路由变更事件。在发声设备迁移时，主动调用 `AudioSink.flush()` / 重置时间戳校准器，迫使底层重握手，消除缓冲死锁。

### 3. 应用层规范化重采样管道（Canonical Sample Rate Processing）
- 在 `Media3AudioPlayerEngine` 中构建自定义 `AudioProcessor` 或配置 `DefaultAudioSink.DefaultAudioProcessorChain`。
- 设定规范化音频阈值 `TARGET_MIN_SAMPLE_RATE = 44100`：
  - 若输入 PCM 流采样率 `< 44100 Hz`（如 22050Hz、16000Hz、11025Hz、8000Hz），激活重采样模块，将其超采样提升至 `44100 Hz` 16-bit 立体声输出；
  - 若输入 PCM 流采样率 `>= 44100 Hz`（如 44.1kHz、48kHz、88.2kHz、96kHz、192kHz），重采样模块保持 `isActive = false`，数据 100% 直通零损耗。

## Testing Decisions

### 1. 优质测试的准则（Behavioral over Implementation Details）
- 测试必须且仅验证外部可见的交互行为与状态机跃迁，严禁断言私有字段或框架内部调用。
- 验证外部广播事件到达后会话层状态与播放进度的确定性收敛。

### 2. 选定测试接缝（The Seam）
- **会话级外放阻断接缝**：在 `PlaybackSessionHostTest` 中通过触发模拟广播 `ACTION_AUDIO_BECOMING_NOISY`，断言 `fakeSession.pause()` 被准确调用，`playbackState` 跃迁为 `PlaybackState.Paused`，硬件锁被安全释放。
- **接收器生命周期接缝**：在 `PlaybackSessionHostTest` 中验证当播放状态处于 `Paused` 或 `Idle` 时，重复发送广播不会产生多余的会话副作用。
- **规范化重采样管道接缝**：在 `Media3AudioPlayerEngineTest` / `CanonicalResamplingAudioProcessorTest` 中，输入 22050Hz PCM 音频帧，断言下游 `AudioFormat` 协商为 44100Hz；输入 44100Hz / 96000Hz 音频帧，断言保持原始采样率直通。
- **现有测试先验基准（Prior Art）**：参考 `PlaybackSessionHostTest.kt`（前台服务提升与延迟降级测试）与 `AudioFocusHandlerTest.kt`（电话打断与音频焦点跃迁测试）。

## Out of Scope

- 开发用户可见的“蓝牙重连自动恢复播放”开关设置项（默认遵循最安全的保持暂停策略，暂不引入新的设置存储键）。
- 自定义 DSP 均衡器频响曲线调整（规范化重采样专注于物理管道稳定性，不修改音效曲线）。
- 蓝牙 LE Audio Auracast 广播音频多路分发控制。

## Further Notes

- 本规格书完全继承并落实 ADR-0018 补遗（`Addendum: Audio Becoming Noisy, Device Route Observer & Canonical Resampling`）。
- 规格书中所有术语定义与 `GLOSSARY.md` 中的 `Becoming Noisy Protection`、`Audio Routing Observer`、`Canonical Sample Rate Resampling` 严格一致。
