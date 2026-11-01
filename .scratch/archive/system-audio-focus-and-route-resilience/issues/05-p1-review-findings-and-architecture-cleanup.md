# 05: 代码审查 P1 缺陷修复与架构边界治理 (Code Review P1 Findings and Architecture Cleanup)

**What to build:**
修复代码审查中识别出的 3 项 P1 级缺陷与规范偏离：
1. **架构包边界越权与废弃分支清理**：将 `AudioFocusHandler` 从底层渲染包 `com.webdav.player.data.player` 迁移至前台服务包 `com.webdav.player.data.service`（与 `WebDavMediaSessionCallback` 平级），彻底消除 `data.player` 对 `domain.session` 的逆向依赖；删除已废弃的 Option B `playerEngine` 次级构造函数与回退逻辑，强制通过非空 `MusicPlayerAppSession` 进行单向意图仲裁。
2. **路由感知契约澄清与测试注解标记**：澄清系统“媒体输出切换器”在生产环境中通过 API 30+ 原生 `MediaRouter2.TransferCallback` 接入、API 29 硬件插拔通过 `AudioDeviceCallback` 感知的双轨契约；为底层私有 AudioTrack 无法暴露而仅供测试模拟的 `attachAudioRouting` / `detachAudioRouting` 显式标注 `@VisibleForTesting`。
3. **消除应用层二次音量压低（Ducking）**：彻底移除 `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` 时应用层手动衰减至 0.2f 的逻辑，严格对齐 Android 8.0+（本项目 `minSdk = 29`）系统混音层统一压低规范，杜绝应用层与系统层双重压低导致的音量湮灭。

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] 将 `AudioFocusHandler` 迁移至 `com.webdav.player.data.service` 包，清理 `@Deprecated` 构造函数与 Option B 备用回退分支。
- [x] 更新 `PlaybackSessionHost`、DI 注入链及所有单元测试中的 `AudioFocusHandler` 引用与 package import。
- [x] 在 `AudioRoutingObserver`、`Media3AudioPlayerEngine` 及 `PlaybackSessionHost` 中为 `attachAudioRouting` 与 `detachAudioRouting` 标注 `@VisibleForTesting`，并在文档中澄清系统切换器 API 30+ 契约。
- [x] 移除 `AudioFocusHandler` 中针对 `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` 的应用层二次压低代码，交由系统混音层统一管理，更新单测断言。
- [x] 运行全量单元测试与回归测试验证闭环。

## Comments

### Resolution
- **包位置与架构单向治理**：
  - 将 `AudioFocusHandler.kt` 和 `AudioFocusHandlerTest.kt` 通过 `git mv` 迁入 `com.webdav.player.data.service` 包，与前台服务宿主及 `WebDavMediaSessionCallback` 对齐。
  - 从 `AudioFocusHandler` 中彻底删除了 `@Deprecated constructor(context, playerEngine)`、`legacyPlayerEngineRef` 以及底层备用分支判断，强制通过 `MusicPlayerAppSession` 意图接缝仲裁焦点。
  - `resumeOnFocusGain` 属性直接委托至 `appSession.pausedByTransientAudioFocus`，消除内部状态分叉。
- **系统音量压低规范化**：
  - 彻底移除了 `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` 下的应用层音量衰减回调（`onVolumeChanged?.invoke(duckVolume)`），完全交由 Android 8.0+ 硬件混音层自动衰减与平滑恢复，杜绝了应用层叠加系统层的双重压低问题。
- **动态路由感知契约澄清**：
  - 在 `AudioRoutingObserver`、`Media3AudioPlayerEngine` 与 `PlaybackSessionHost` 中，将未在生产运行时暴露的 `attachAudioRouting` / `detachAudioRouting` 明确标注 `@VisibleForTesting`。
  - 在 `spec.md` 中补充澄清了系统媒体输出切换器流转在生产环境通过 API 30+ `MediaRouter2.TransferCallback` 监听、物理硬件插拔通过 `AudioDeviceCallback` 协同运作的运行契约。
- **测试闭环**：
  - 更新了 `AudioFocusHandlerTest`、`PlaybackSessionHostTest`、`Media3AudioPlayerEngineTest`、`EndToEndPlaybackPipelineIntegrationTest` 以及 `EndToEndSystemFocusAndRouteRegressionTest`。
  - 全量运行 815 项单元测试与集成测试，全部绿色通过（`BUILD SUCCESSFUL`）。
