# 03: 削除服务宿主全部跨接缝向下类型强转

**What to build:** 清理服务宿主内部对播放引擎具体实现类的全部向下强转，将音频路由变更自愈能力通过标准化引擎接缝调用，确保服务宿主面向纯粹的流式管道抽象编程。

**Blocked by:** 02: MediaSession 宿主迁移与引擎反向回调拔除

**Status:** resolved

- [x] `PlaybackSessionHost` 中消除全部 5 处 `(playerEngine as? Media3AudioPlayerEngine)` 强转
- [x] `AudioPlayerEngine` 接口标准化定义音频路由变更通知方法 `fun onAudioRoutingChanged()`（提供默认空实现）
- [x] `PlaybackSessionHost` 仅面向 `AudioPlayerEngine` 接口调用路由刷新，不再暴露或穿透到底层 `audioRoutingObserver`
- [x] 清理宿主上仅用于测试侵入的冗余桥接方法，保证生产代码接缝纯净

## Comments

### Resolution
- 在 `AudioPlayerEngine` 接口中标准化定义了带默认空实现的 `fun onAudioRoutingChanged() {}`，并在 `Media3AudioPlayerEngine` 与 `FakeAudioPlayerEngine` 中予以实现。
- 在 `PlaybackSessionHost` 中将 `onAudioRoutingChanged()` 改为直接通过 `AudioPlayerEngine` 接口转发（`playerEngine.onAudioRoutingChanged()`），并移除了 `audioRoutingObserver`、`attachAudioRouting`、`detachAudioRouting` 等穿透具体实现的测试桥接方法，使 `PlaybackSessionHost` 内部对 `Media3AudioPlayerEngine` 的向下类型强转彻底清零（5 处强转全部消除）。
- 更新了 `PlaybackSessionHostTest`、`FakeAudioPlayerEngine` 与 `EndToEndSystemFocusAndRouteRegressionTest`，通过 `AudioPlayerEngine` 接缝直接验证音频路由变更转发，全量单元测试通过。
