# 02: MediaSession 宿主迁移与引擎反向回调拔除

**What to build:** 将系统媒体会话（MediaSession）的创建与管理职责完全从底层音频流渲染引擎移交至系统服务宿主。底层引擎不再反向持有会话回调或上一曲/下一曲调用，系统播控意图由宿主直接单向派发至应用会话层。

**Blocked by:** 01: 硬件锁归属内化至流式管道并消除适配器冗余传递

**Status:** resolved

- [x] `Media3AudioPlayerEngine` 剥离 `MediaSession` 构建与持有责任，删除 `var sessionCallback`、`var onSkipToNext` 与 `var onSkipToPrevious` 反向通道
- [x] `PlaybackSessionHost` 直接负责装配 `MediaSession`（基于底层核心播放器与非空 `WebDavMediaSessionCallback`），消除 `bindAppSession` 中对底层引擎的向下强转与回调注入
- [x] `WebDavMediaService` 统一经由宿主获取并注册 `MediaSession`，生命周期清晰可溯
- [x] 系统通知栏、锁屏与蓝牙线控的播控交互在无反向引擎回调的情况下保持平滑可用

## Comments

### Resolution
- 从 `Media3AudioPlayerEngine` 中彻底移除了 `customMediaSession`、`mediaSession`、`internalSessionCallback`、`var sessionCallback`、`var onSkipToNext` 与 `var onSkipToPrevious` 及其在 `release()` 中的释放调用，并将 `sessionPlayer` 的 `seekToNext*`/`seekToPrevious*` 设为空操作，保持底层流式管道纯粹无会话反向依赖。
- 在 `PlaybackSessionHost` 中直接构建、持有并在 `release()` 时释放 `MediaSession`，彻底消除了 `bindAppSession` 与 `init` 中对 `Media3AudioPlayerEngine` 的向下强转及回调反向注入。
- 在 `WebDavMediaService` 中统一通过 `PlaybackSessionHost` 获取、注册并安全解绑（`unregisterSession`）`MediaSession`，确保宿主与服务销毁时序互不冲突。
- 更新并新增了 `Media3AudioPlayerEngineTest`、`PlaybackSessionHostTest`、`WebDavMediaServiceTest` 与 `EndToEndSystemFocusAndRouteRegressionTest` 测试用例，全量单元测试通过。
