# Use AndroidX Media3 with FFmpeg Extension for Audio Playback

## Context

本项目需要支持包括 MP3、FLAC、WAV 以及 WMA 在内的主流音频格式。Android 系统的系统解码器和 Media3 (ExoPlayer) 默认并不支持 WMA 格式。主要替代方案包括采用基于 LibVLC 的完整播放内核（包体积大、与 AndroidX 体系隔离），或仅支持 Android 原生格式（牺牲了用户对 WMA 的需求）。

## Decision

选用 AndroidX Media3 (ExoPlayer) 作为音频播放引擎，并接入 FFmpeg 音频解码扩展。

## Consequences

- 深度贴合现代 Android 架构规范，原生享有 `MediaSessionService`、锁屏/通知栏集成及音频焦点管理。
- 借助 FFmpeg 软件解码模块，实现了对 WMA 及各种无损音频编码的完整解码覆盖。
- 相比 LibVLC 更轻量且更符合 Jetpack 生态，但需要配置构建针对各 ABI（arm64-v8a 等）的 FFmpeg 动态库。
