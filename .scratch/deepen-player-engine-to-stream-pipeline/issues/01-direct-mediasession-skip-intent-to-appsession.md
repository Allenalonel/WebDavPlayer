# 01: 直连媒体会话切歌意图至播放会话 (Direct MediaSession Skip Intent to AppSession)

**What to build:** 建立从系统通知栏、蓝牙耳机线控和车载系统直达应用播放会话层的切歌意图派发路径。当用户触发“下一首”或“上一首”硬件按键时，切歌事件直接由应用会话层处理并结合当前播放模式、CUE 虚拟分轨与时长守卫（Duration Guard）进行决策，彻底解除对底层播放引擎反向拦截回调的依赖。

**Blocked by:** None (can start immediately)

**Status:** completed

- [x] 系统媒体会话回调支持接收并直连会话层切歌指令（`skipToNext` / `skipToPrevious`），不再必须通过底层播放引擎反弹
- [x] 蓝牙耳机按键与车载中控发起的切歌事件能够即时驱动会话层队列推进或 CUE 虚拟分轨跳转
- [x] 在弱网状态或意外网络截断时，线控切歌指令依然能获得会话层的立即响应与正确裁决
- [x] 既有的系统媒体会话单元测试更新并验证切歌直连路径正确有效
