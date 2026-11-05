# 06: 全局弹窗缩放与全量回归打包门禁

**What to build:** 为全局系统级弹窗（加入歌单对话框、服务器编辑弹窗、音轨详细信息属性对话框）接入 M3E 弹簧缩放弹出（`scaleIn(0.92f) + fadeIn()`）与退出规范，达成全 App 动效语言 100% 统一；执行全量测试套件（`./gradlew testDebugUnitTest`）自证全绿；触发端到端打包验证（`PackagingSeamVerificationTest`），严格确保未引入任何外部第三方动画库、维持 64 位 ABI 过滤且 Release APK 体积严格达标（$\le 7.5\text{ MB}$）。

**Blocked by:** 02: 双向 M3 共享轴向 Tab 导航与底栏药丸拉伸, 03: M3E 悬浮搜索舱形变与媒体库层级穿梭, 04: 胶囊至全屏连续形态蜕变体系, 05: 队列播放模式循环与平滑洗牌重排

**Status:** claimed

## Acceptance criteria

- [ ] 为 `AddToPlaylistDialog`、`ServerEditDialog` 与 `PlaylistTrackDetailDialog` 配置 M3E 弹簧变焦弹出（`scaleIn(0.92f) + fadeIn()`）与退出规范，杜绝系统默认弹窗生硬弹出。
- [ ] 全量运行单元测试套件（`./gradlew testDebugUnitTest`），确保所有历史测试与本次新增动效测试 100% 通过。
- [ ] 验证本次重构引入 0 个外部第三方动画依赖库（纯官方 `androidx.compose.animation` 原生 API）。
- [ ] 触发 Release 架构打包门禁（`PackagingSeamVerificationTest`），保证 64 位 ABI 过滤纯净，验证 APK 最终体积完全符合 7.5 MB 预算红线。
