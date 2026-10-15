# 03: 服务宿主预热绑定与退后台内存自洁 (Service Pre-warming & TrimMemory Guard)

**What to build:** 在前台可见时预先完成媒体服务的合法绑定，消除系统后台启动限制异常；为服务声明自动重启粘性并守护多任务划除行为；在应用退后台或息屏时立即释放大图内存，将常驻内存压降 60%，消除系统 Low Memory Killer (LMK) 查杀风险。

**Blocked by:** 01: 15 分钟延迟前台服务降级状态机 (Deferred Foreground Demotion)

**Status:** ready-for-agent

- [ ] 在 `MainActivity.onStart()` 生命周期中主动调用服务预热绑定契约，确保所有服务连接在合法的系统前台窗口内建立，彻底杜绝 `Background start not allowed` 崩溃。
- [ ] 媒体播放服务 `onStartCommand()` 明确返回 `START_STICKY` 粘性标志。
- [ ] 覆盖 `onTaskRemoved()`：当处于播放中或处于 15 分钟延迟降级宽容期内时，坚决不主动调用 `stopSelf()`，保障用户在多任务列表划掉主界面卡片后音乐依然持续回放。
- [ ] 在 `MainActivity` 与 `WebDavApplication` 中实现 `onTrimMemory()`，当告警等级达到 `TRIM_MEMORY_UI_HIDDEN` 时主动清空 `ThumbnailMemoryCache`，使后台常驻内存压降至约 50MB。
- [ ] 编写测试覆盖前台可见时的服务预热、多任务划除时的守护断言、以及内存修剪信号触发时的位图清理验证。
