# 05: 弱网感知状态呈现与超时手动重试体验 (Weak-Network Status Presentation & Manual Retry Action)

**What to build:** 完善弱网与长时缓冲状态下的用户界面及系统交互感知。当网络因弱网或 NAS 机械硬盘起转导致缓冲等待超过 15 秒时，MiniPlayer、全屏大播放器与系统前台通知栏副标题直观显示“网络受阻，正在重连...”状态；当缓冲等待持续超过 30 秒时，播控区域提供显式的“重试”按钮供用户主动发起刷新重连；网络恢复后无缝恢复原状态，并保证蓝牙车机屏幕与系统媒体会话状态同步无误。

**Blocked by:** 04: 弱网双轨预热动态抑制与用户操作即时抢占 (Adaptive Pre-roll Stream Buffering & User Preemption Priority)

**Status:** completed

- [x] 播放会话向外暴露包含重试中与弱网感知的结构化状态流。
- [x] 缓冲重试持续超过 15 秒时，Docked Mini-Player、Full Player Sheet 与系统通知栏呈现“网络受阻，正在重连...”副标题与指示。
- [x] 缓冲重试持续超过 30 秒时，播控交互区域提供直观的“重试”操作，用户点击即可立即触发原位重新连接。
- [x] 弱网重连成功恢复播放后，系统 MediaSession 与蓝牙车机屏幕无缝回退为正常播放状态，无报错闪退或断连。
- [x] 自动化测试验证长时缓冲状态下弱网文字与重试交互动作的派发逻辑。
