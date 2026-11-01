# 04: 测试表面接缝对齐与全量端到端回归验证

**What to build:** 调整测试套件中所有跨越错误接缝的断言，宿主测试专职断言前台服务与通知状态，引擎测试专职断言流式回放与硬件锁，并通过全量回归测试验证重构后的零断流与系统保活表现。

**Blocked by:** 03: 削除服务宿主全部跨接缝向下类型强转

**Status:** resolved

- [x] `PlaybackSessionHostTest` 测试用例净化：不再断言底层硬件锁的内部状态，全面覆盖前台服务提升、15 分钟延迟降级、通知构建与 Becoming Noisy 拦截
- [x] `Media3AudioPlayerEngineTest` 专职覆盖硬件休眠锁治理、无感元数据富化、低采样率重采样与流式加载
- [x] `EndToEndPlaybackPipelineIntegrationTest` 与 `EndToEndSystemFocusAndRouteRegressionTest` 全量执行通过
- [x] 全套项目测试构建成功（`./gradlew testDebugUnitTest`），确保无任何测试套件破损

## Comments

### Resolution
- 净化了 `FakeAudioPlayerEngine`，移除了遗留的多音轨兼容桥接属性（`playTracksCount`、`lastTracks`、`lastStartIndex`），并将 `PlaybackSessionHostTest`、`MusicPlayerAppSessionTest`、`PlaybackSessionResumptionTest`、`ProcessRestartSimulationTest` 中的全部调用对齐至纯流式管道接缝（`loadTrackCount`、`loadedTrack`）。
- 收敛了 `PlaybackSessionHost` 的主构造签名（移除仅用于旧测试绕过宿主装配的 `mediaSession: MediaSession?` 与 `mediaSessionCallback: WebDavMediaSessionCallback?` 参数），并在 `PlaybackSessionHostTest` 中补齐了跨播放状态（`Playing`、`Buffering` 弱网提示、`Paused`、`Error`）的系统通知构建断言，完整覆盖前台服务提升、15 分钟延迟降级、通知构建与 `Becoming Noisy` 拦截。
- 在 `Media3AudioPlayerEngineTest` 中消除了对 `domain.session.MusicPlayerAppSessionImpl` 的跨层引用，改为直接面向 `sessionPlayer` 与 `player` 时间线接缝验证无感元数据富化，并补充了 `CanonicalResamplingAudioProcessor` 低采样率（16000Hz 单声道 / 22050Hz 双声道 -> 44100Hz 双声道 PCM）升采样与路由切换冲刷的完整引擎级测试。
- 对齐了 `EndToEndPlaybackPipelineIntegrationTest` 与 `EndToEndSystemFocusAndRouteRegressionTest` 中 `PlaybackSessionHost` 的 `MediaSession` 装配与 `bindAppSession` 接缝，并全量执行通过 `./gradlew testDebugUnitTest`。
