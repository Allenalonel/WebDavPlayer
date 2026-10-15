# 02: 扩展流式播放管道接口与底层实现 (Expand Streaming Pipeline Interface in Engine)

**What to build:** 在底层播放引擎中扩展纯粹面向单轨/双轨预卷物理流的新接口方法，实现精准的单曲音频流装载、起播与 PCM 预卷缝合能力。同时保留旧列表签名以满足 Expand–Contract 宽重构模式下的渐进迁移，保证编译期与测试期稳定。

**Blocked by:** None (can start immediately)

**Status:** claimed

- [ ] 播放引擎接口定义纯流式加载方法（`loadTrack(server, track, startPositionMs)`）、收窄版预卷方法（`prerollTrack(server, track)`）、预卷清除方法（`clearPrerollTrack()`）及单轨元数据无打扰更新方法（`updateTrackMetadata(track)`）
- [ ] 真实播放引擎实现类完成新流式方法的适配，内部确保仅针对当前单轨建立媒体源，并支持在需要时挂载至多一个预卷媒体源
- [ ] 前台服务宿主装饰器透明对齐并透传新流式接口方法，维持首帧前台服务启动和通知栏构建的生命周期契约
- [ ] 既有旧接口方法保留并作为兼容委托，确保在此阶段未迁移的旧调用点不发生编译错误
