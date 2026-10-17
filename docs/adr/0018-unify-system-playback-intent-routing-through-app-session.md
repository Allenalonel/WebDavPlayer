# 0018: Unify System Playback Intent Routing Through App Session

ADR-0016 确立了"底层 `AudioPlayerEngine` 为纯物理流式渲染管道，所有播控意图收归 `MusicPlayerAppSession`"的设计原则，但代码中存在三条绕过会话层、直接操作底层引擎的分裂派发路径，本决策记录如何彻底消除这些偏差并落实 ADR-0016 既定意图。

## Context

审查代码后发现，系统级播控意图存在以下三条**分裂派发路径**，与 ADR-0016 直接矛盾：

### 路径一：`WebDavMediaSessionCallback.handleMediaButtonIntent`（耳机/蓝牙物理按键）

| 按键 | 现状调用目标 | 问题 |
|------|------------|------|
| `KEYCODE_MEDIA_PLAY` | `playerEngine.play()` | 绕过会话层，冷启动后无法触发 `loadTrackIntoEngine` 恢复 |
| `KEYCODE_MEDIA_PAUSE` | `playerEngine.pause()` | 绕过会话层，不触发进度刷盘与 clean-exit 标记 |
| `KEYCODE_MEDIA_PLAY_PAUSE` | `playerEngine.play/pause()` | 同上，且状态判断基于引擎而非会话状态 |
| `KEYCODE_MEDIA_STOP` | `playerEngine.stop()` | 绕过 `MusicPlayerAppSessionImpl.stop()`，跳过 virtualTimeline 清理、watchdog 取消、periodicFlush 停止、clean-exit 刷盘 |
| `KEYCODE_MEDIA_NEXT` | `appSession?.skipToNext()` | 正确 |
| `KEYCODE_MEDIA_PREVIOUS` | `appSession?.skipToPrevious()` | 正确 |

### 路径二：`WebDavMediaService.onStartCommand`（通知栏自定义 Action）

```kotlin
ACTION_PLAY   -> host.play()                              // 直连底层，冷恢复失效
ACTION_PAUSE  -> host.pause()                             // 直连底层，不刷盘
ACTION_NEXT   -> host.mediaSessionCallback?.appSession?.skipToNext()    // 正确
ACTION_PREVIOUS -> host.mediaSessionCallback?.appSession?.skipToPrevious() // 正确
```

### 路径三：`Media3AudioPlayerEngine.setAppSession()` 幽灵反向通道

```kotlin
// WebDavApplication.onCreate()
(playerEngine as? PlaybackSessionHost)?.setAppSession(musicPlayerAppSession)
```

`PlaybackSessionHost.setAppSession()` 将 `appSession` 注入 `WebDavMediaSessionCallback.appSession`（可空 var），同时还调用 `Media3AudioPlayerEngine.setAppSession()` 对同一引用做双重冗余赋值。这条延迟注入链造成：
1. 底层引擎文件（`data` 层）import 了 `domain.session.MusicPlayerAppSession`，打破了依赖方向；
2. 在 `Application.onCreate()` 完成前存在可空窗口（尽管 Android 生命周期几乎不会触发，仍是设计噪声）；
3. `WebDavMediaSessionCallback` 同时持有 `playerEngine`（构造参数）和 `appSession`（可空 var），两套依赖指向不同层次，接口形状混乱。

### 已确认的两个 Bug

**Bug 1（冷恢复无声）**：用户从后台杀死应用后，通过通知栏或耳机点击"播放"，`host.play()` / `playerEngine.play()` 在引擎处于 `Idle` 状态时什么都不做。`MusicPlayerAppSessionImpl.play()` 含有的冷恢复逻辑（`Idle` 状态下重新 `loadTrackIntoEngine`）被完全绕过。

**Bug 2（stop 清理不完整）**：`KEYCODE_MEDIA_STOP` 直接调用 `playerEngine.stop()`，跳过了 `MusicPlayerAppSessionImpl.stop()` 中的：
- `clearPrerollIfActive()`
- `stopPeriodicFlush()`
- `virtualTimelineEngine.clear()`
- `bufferingWatchdogJobRef.getAndSet(null)?.cancel()`
- `flushSessionWithCleanExit(isCleanExit = true)`

导致虚拟时间轴残留、看门狗定时器泄漏、下次启动时 `cleanExit = false` 触发不必要的崩溃恢复流程。

## Decision

### 1. `WebDavMediaSessionCallback`：删除 `playerEngine` 依赖，改为构造注入 `appSession`

**Before：**
```kotlin
class WebDavMediaSessionCallback(
    private val playerEngine: AudioPlayerEngine,  // 删除
    var appSession: MusicPlayerAppSession? = null, // 改为构造参数，非空
) : MediaSession.Callback
```

**After：**
```kotlin
class WebDavMediaSessionCallback(
    private val appSession: MusicPlayerAppSession, // 非空，构造时注入
) : MediaSession.Callback
```

`handleMediaButtonIntent` 中全部意图统一路由至 `appSession`：

| 按键 | After |
|------|-------|
| `KEYCODE_MEDIA_PLAY` | `appSession.play()` |
| `KEYCODE_MEDIA_PAUSE` | `appSession.pause()` |
| `KEYCODE_MEDIA_PLAY_PAUSE` / `KEYCODE_HEADSETHOOK` | `appSession.togglePlayPause()` |
| `KEYCODE_MEDIA_STOP` | `appSession.stop()` |
| `KEYCODE_MEDIA_NEXT` | `appSession.skipToNext()` |
| `KEYCODE_MEDIA_PREVIOUS` | `appSession.skipToPrevious()` |

`KEYCODE_MEDIA_PLAY_PAUSE` 不再在 Callback 层读取 `playbackState.value` 自行判断，改为调用 `appSession.togglePlayPause()`，由会话层内部基于 `sessionState.value.isPlaying` 统一仲裁。

`onPlayerCommandRequest` 中的 `COMMAND_SEEK_TO_NEXT/PREVIOUS` 已正确路由至 `appSession`，无需改动。

### 2. `WebDavMediaService.onStartCommand`：通知栏 Action 全部路由至 `appSession`

**Before：**
```kotlin
ACTION_PLAY  -> host.play()
ACTION_PAUSE -> host.pause()
```

**After：**
```kotlin
ACTION_PLAY  -> host.mediaSessionCallback?.appSession?.play()   // 经由统一意图接缝
ACTION_PAUSE -> host.mediaSessionCallback?.appSession?.pause()
```

或更直接地，在 `PlaybackSessionHost` 上暴露 `appSession` 引用，由 `onStartCommand` 直接调用 `host.appSession?.play()`。

### 3. `WebDavApplication` DI：调整构造顺序，先建 `appSession`，再传入 `Callback`

**Before 构造顺序：**
```
1. 建 playerEngine (PlaybackSessionHost)
2. 建 musicPlayerAppSession (MusicPlayerAppSessionImpl)
3. playerEngine.setAppSession(musicPlayerAppSession)  // 延迟注入
```

**After 构造顺序：**
```
1. 建 playerEngine (PlaybackSessionHost，此时不含 appSession)
2. 建 musicPlayerAppSession (MusicPlayerAppSessionImpl，传入 playerEngine)
3. 建 WebDavMediaSessionCallback(appSession = musicPlayerAppSession)  // 构造时注入，非空
4. 将 Callback 传给 Media3AudioPlayerEngine / PlaybackSessionHost
```

实际上 `Media3AudioPlayerEngine` 构造时已经创建 `mediaSessionCallback`，因此需要将 Callback 的创建时机从引擎内部移至外部（由 `PlaybackSessionHost` 的次级构造函数协调），或将 `appSession` 作为参数传入引擎构造函数链。具体拓扑见下文"构造拓扑"。

### 4. 删除 `setAppSession()` 延迟注入方法链

- `Media3AudioPlayerEngine.setAppSession()` 方法：**删除**
- `PlaybackSessionHost.setAppSession()` 方法：**删除**
- `WebDavMediaSessionCallback.appSession` 可空 var：**删除**，改为构造时传入的 `val`
- `WebDavApplication` 中 `(playerEngine as? PlaybackSessionHost)?.setAppSession(...)` 调用：**删除**

### 5. `Media3AudioPlayerEngine` 不再 import `MusicPlayerAppSession`

修复后 `data.player` 包不再依赖 `domain.session`，依赖方向恢复为单向：
```
domain.session → domain.player (AudioPlayerEngine 接口)
data.player    → domain.player (实现接口)
data.service   → domain.session (Callback 持有 appSession)
```

## 构造拓扑（After）

```
WebDavApplication.onCreate()
│
├─ 建 playerEngine: Media3AudioPlayerEngine
│    └─ 内部创建 mediaSessionCallback (暂无 appSession，此时为占位)
│
├─ 建 musicPlayerAppSession: MusicPlayerAppSessionImpl(playerEngine = playerEngine)
│
└─ 将 appSession 注入 Callback
     └─ playerEngine.mediaSessionCallback 替换为
        WebDavMediaSessionCallback(appSession = musicPlayerAppSession)
     或
     └─ PlaybackSessionHost 提供 bindAppSession(appSession) 仅用于
        将 Callback 重建（此方法仅在 Application.onCreate 调用一次，
        非运行期可变注入，语义不同于旧的 setAppSession）
```

> **注**：若循环依赖（`playerEngine` 需要 `appSession`，`appSession` 需要 `playerEngine`）难以在构造层解决，可保留 `bindAppSession(appSession: MusicPlayerAppSession)` 作为**一次性绑定方法**（调用后即封闭，不接受二次修改），与旧的可变 `setAppSession` 在语义上明确区分。该一次性绑定不破坏局部性，因为它只在 `Application.onCreate()` 的线性初始化序列中调用一次。

## Considered Options

**Option A（本决策采用）**：全量意图路由至 `appSession`，删除 `playerEngine` 参数，构造时注入 `appSession`。

**Option B（弃用）**：保留 `playerEngine` 作为后备，当 `appSession` 为 null 时降级调用 engine。  
*弃用原因*：引入了错误的容错层次，掩盖了注入失败的问题，且维持了双向耦合。

**Option C（弃用）**：最小改动，仅把 `playerEngine.play()` 替换为 `appSession?.play()`，保留可空 var 和 `setAppSession` 链。  
*弃用原因*：修复了症状但未消除幽灵通道，`setAppSession` 延迟注入窗口和 `data.player` 对 `domain.session` 的反向依赖依然存在。

## Consequences

- **冷恢复 Bug 修复**：通知栏/耳机播放键经由 `appSession.play()` 触发冷恢复逻辑，在 `Idle` 状态下正确执行 `loadTrackIntoEngine`。
- **stop 清理 Bug 修复**：`KEYCODE_MEDIA_STOP` 经由 `appSession.stop()` 执行完整清理序列。
- **依赖方向恢复单向**：`data.player` 不再 import `domain.session`。
- **测试接缝对齐**：`WebDavMediaSessionCallbackTest` 全部旧断言（`fakeEngine.playCount` 等）删除，改为在 `fakeSession` 接缝断言，测试跨越的接缝与生产路径一致。
- **接口面积收窄**：`WebDavMediaSessionCallback` 从双参数（engine + nullable session）变为单参数（non-null session），接口复杂度降低。
- **ADR-0016 完全落实**：`AudioPlayerEngine` 成为彻底无会话依赖的纯物理流渲染管道。
