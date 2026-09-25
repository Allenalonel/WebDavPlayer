# Target Android 10 (API 29) Minimum SDK

## Context

设定应用的最低兼容 Android 版本（Min SDK）决定了可用 API 的现代程度、依赖库版本与设备覆盖率。过低的 Min SDK（如 API 21/24）需要大量低版本适配分支、向下兼容库与老旧后台保活代码；而 API 29+ 拥有统一的 Scoped Storage、现代通知权限模型及对 Jetpack Compose 与 Media3 完整的原生支持。

## Decision

将项目的最低支持 SDK（Min SDK）设定为 Android 10.0 (API 29)。

## Consequences

- 能够无缝利用现代 Android 平台特性（如系统的深色模式适配、现代音频路由控制与网络安全性策略）。
- 极大减少由于兼容过早版本 Android 带来的胶水代码与体积负担。
- 放弃了 Android 9 及以下（目前仅占极低市场份额）的旧机型，符合现代独立应用轻装上阵的定位。
