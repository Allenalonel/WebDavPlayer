# 02: 规范化音频重采样管道 (Canonical Sample Rate Resampling Pipeline)

**What to build:** 为解决老旧有声书、评书、相声等非标准低采样率音频（如 22050Hz、16000Hz、11025Hz）在蓝牙 A2DP（强制 44100Hz/48000Hz）重路由时引发 Android 系统驱动重采样器死锁、导致进度条空走而硬件无声的问题，在底层播放引擎的音频渲染前置管道中构建规范化重采样器。凡采样率低于 44,100Hz 的 PCM 输入流，由应用层在内存中自动无损超采样规范化为标准 44,100Hz 双声道输出后再交付系统 `AudioTrack`；标准音频（>= 44.1kHz）则 100% 直通零损耗。从源头消除厂商驱动重混音卡死的前提条件。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] 在 `Media3AudioPlayerEngine` 中构建规范化音频处理器（基于 `AudioProcessor` / `SonicAudioProcessor` 或插入 `DefaultAudioSink` 处理链）。
- [x] 当输入 PCM 音频采样率 `< 44100 Hz`（如 22050Hz）时，自动将其规范化重采样为标准 `44100 Hz` 双声道输出。
- [x] 当输入 PCM 音频采样率 `>= 44100 Hz`（如 44.1kHz、48kHz、88.2kHz、96kHz 等无损母带）时，重采样器处于非激活直通状态，不引入任何计算开销与精度损耗。
- [x] 编写并执行单元测试，输入 22050Hz、16000Hz 音频帧断言输出格式成功转换为 44100Hz；输入 44100Hz、96000Hz 音频帧断言直通保持原采样率。

## Answer

已构建并在 `Media3AudioPlayerEngine` 中接入 `CanonicalResamplingAudioProcessor`：
1. 实现对齐 ADR-0018 规范的应用层规范化重采样器 `CanonicalResamplingAudioProcessor`，设定标准采样率下限阈值 `CANONICAL_SAMPLE_RATE_HZ = 44,100`。
2. 当输入采样率 `< 44100 Hz`（如 22050Hz、16000Hz、11025Hz、8000Hz）时，处理器激活，通过内部无损 `SonicAudioProcessor` 超采样至 44100Hz，并将单声道帧无损复制为立体声（2 通道），杜绝底层 HAL 蓝牙重混音死锁。
3. 当输入采样率 `>= 44100 Hz`（如 44.1kHz、48kHz、96kHz 等）时，`isActive()` 返回 `false`，直通直出，零额外计算开销与精度损耗。
4. 在 `Media3AudioPlayerEngine` 的 `buildAudioSink` 中将规范化重采样器注入 `DefaultAudioSink` 音频处理链。
5. 编写针对各种采样率（22050Hz 立体声、16000Hz 单声道、44100Hz 标准、96000Hz 高解析）与真实 PCM 数据处理的单元测试 `CanonicalResamplingAudioProcessorTest`，全部通过。Commit `f25d08d`。
