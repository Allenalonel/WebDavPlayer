# 03: 彻底拔除底层引擎反向注入通道并闭环单向装配契约 (Eliminate Reverse Engine Coupling and Finalize DI Assembly)

**What to build:**
从底层音频流播放引擎与宿主服务中彻底删除对高层应用会话层的反向持有通道及延迟注入方法，将媒体会话回调组件对底层引擎的直接依赖完全拔除，确立纯意图派发器的角色边界。在应用启动初始化装配中建立一次性封闭绑定，消除运行期可空时序窗口，让底层播放引擎退化为纯粹、无外部依赖的流式音频渲染管道。

**Blocked by:** 01: 统一媒体按键与命令意图至应用会话层 (Route Media Button and Command Intents to App Session), 02: 统一系统通知栏自定义动作路由至应用会话层 (Route Notification Bar Actions to App Session)

**Status:** resolved

- [x] 媒体会话回调组件彻底移除底层播放引擎构造参数与属性引用，仅持有非空的应用会话层接缝引用
- [x] 底层媒体播放引擎与播放宿主服务中彻底删除延迟注入方法及其背后的可空会话属性
- [x] 底层媒体播放引擎源码文件中完全清除对应用会话层包的导入，恢复纯净的单向架构依赖
- [x] 应用顶层装配逻辑调整为一次性确定性装配，消除会话对象的可空窗口与反向穿透
- [x] 全工程编译通过，所有单元测试与集成测试（包括媒体回调测试）全部保持绿灯通过

## Answer

已彻底完成底层引擎与高层应用会话层的解耦及单向依赖装配：
1. `WebDavMediaSessionCallback` 构造函数仅接收非空 `val appSession: MusicPlayerAppSession`，彻底删除底层播放引擎构造参数与属性。
2. 从 `Media3AudioPlayerEngine` 与 `PlaybackSessionHost` 中彻底删除旧的 `setAppSession` 延迟注入通道。
3. `Media3AudioPlayerEngine` 完全清除对 `domain.session.MusicPlayerAppSession` 与 `data.service.WebDavMediaSessionCallback` 的包导入，通过高内聚的命令代理闭包（`onSkipToNext` / `onSkipToPrevious` 与 `sessionCallback: MediaSession.Callback?`）实现纯粹流式音频渲染管道。
4. `PlaybackSessionHost` 提供确定性一次性装配契约 `bindAppSession(session: MusicPlayerAppSession)`，在 `WebDavApplication.onCreate` 初始化序列中完成封闭绑定，消除可空时序窗口。
5. 全量单元测试和集成测试通过（761+ tests passed）。Commit `2de73fc`。
