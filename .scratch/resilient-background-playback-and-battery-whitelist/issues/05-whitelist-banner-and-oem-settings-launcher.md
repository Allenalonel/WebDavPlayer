# 05: 全屏播放器电池优化常驻横幅与厂商跳转 (Whitelist Banner & OEM Settings Launcher)

**What to build:** 在全屏大播放器界面中，当检测到应用尚未加入系统电池优化白名单时，常驻展示优雅的优化提示横幅；点击后一键调起原生系统权限弹窗，并针对小米（MIUI / HyperOS）等机型提供直达应用设置页的指引跳转。

**Blocked by:** 04: 会话健康追踪与冷启动强杀感知 (Session Health Tracking & Kill Detection)

**Status:** ready-for-agent

- [ ] 封装系统电源管理服务检测接口：`powerManager.isIgnoringBatteryOptimizations(packageName)`。
- [ ] 在全屏大播放器视图（`FullPlayerSheet`）顶栏下方嵌入常驻提示卡片组件；当未开启白名单时常驻展示，支持用户点击跳转或本次会话收起。
- [ ] 结合 `wasKilledBySystem` 状态感知：若上次被系统强杀，动态强化横幅文案（如提示“检测到上次锁屏被系统强行终止，建议开启后台保护”）。
- [ ] 点击横幅时，调用原生系统意图 `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 请求豁免电池优化。
- [ ] 针对小米（MIUI / HyperOS）设备，增加跳转至应用配置页的逻辑，并在界面提供明确的步骤指引（引导开启自启动与省电策略无限制）。
- [ ] 当系统检测到已在白名单中时，横幅自动平滑隐去。
- [ ] 编写 Compose 呈现与交互测试，验证横幅在不同权限状态下的显隐及点击意图分发。
