# WebDavPlayer 🎵

<p align="center">
  <img src="app/src/main/res/drawable/ic_launcher_foreground.xml" width="96" height="96" alt="WebDavPlayer Logo" />
</p>

<p align="center">
  <strong>专为远程云端音乐打造的现代化 Android WebDAV 流式播放器</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_10+_(API_29+)-brightgreen?style=flat-square&logo=android" alt="Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin_|_C++-blue?style=flat-square&logo=kotlin" alt="Language" />
  <img src="https://img.shields.io/badge/UI-Jetpack_Compose_MD3-purple?style=flat-square&logo=jetpackcompose" alt="Compose" />
  <img src="https://img.shields.io/badge/Audio-Media3_ExoPlayer_+_FFmpeg-orange?style=flat-square" alt="Media3 & FFmpeg" />
  <img src="https://img.shields.io/badge/License-Apache--2.0-lightgrey?style=flat-square" alt="License" />
</p>

---

## 🌟 核心特性

- 🚀 **纯流式在线即点即播（Pure Online Streaming）**
  - 基于 HTTP Range 分块流式缓冲回放，毫秒级起播。
  - 不占用本地永久存储空间，随播随放，播放完毕自动释放缓存。

- 🎼 **全格式硬核音频解码（FFmpeg Native Extension）**
  - 基于 **Media3 (ExoPlayer)** 并深度集成 **FFmpeg JNI (C++)** 原生解码扩展。
  - 全面支持 MP3, FLAC, WAV, AAC, OGG, OPUS，以及原生 Android 难以支持的 **WMA (ASF)**, **APE** 等无损与经典音频格式。

- ⚡ **毫秒级增量元数据提取（On-demand Range Extraction）**
  - 无需下载整首曲目，仅通过 HTTP Range 拉取音频头尾部关键字节块。
  - 秒级完成 ID3v1/ID3v2、VorbisComment、ASF/WMA 标签提取，快速呈现艺术家、专辑、时长及内嵌高清封面。

- 📜 **双源智能歌词解析（Dual-Source Lyrics）**
  - **同名外部歌词**：自动检索与关联远程目录下的同名 `.lrc` 文件。
  - **内置内嵌歌词**：解析音频标签内的内嵌歌词文本。
  - 毫秒级时间轴对齐，沉浸式逐行高亮与平滑滚动。

- 💾 **SWR 目录缓存与断点状态恢复（Stale-While-Revalidate）**
  - 基于 **Room** 数据库对 WebDAV 目录树进行持久化快照，实现秒级浏览与离线/弱网加载。
  - 支持后台静默更新目录，智能合并新增与变更。
  - 自动持久化播放会话（播放队列、当前索引、毫秒级进度）以及各服务器最后浏览路径，冷启动无缝续播。

- 🎨 **Modern Material Design 3 界面**
  - **悬浮 Mini-Player 胶囊**：页面导航不中断，始终悬浮承载核心播控。
  - **沉浸式全屏播放器**：封面色彩提取动态渐变背景、黑胶唱片旋转动效、封面/歌词双面板无缝滑动切换。
  - **水平面包屑导航**：自适应 MD3 Chip 路径条，支持任意祖先目录一键瞬时回退。

- 🔒 **灵活的 WebDAV 连接与安全认证**
  - 支持多 WebDAV 节点管理与一键切换。
  - 支持 HTTP / HTTPS 协议、标准 Digest / Basic 鉴权。
  - 支持**自签名 SSL 证书信任配置**，适配家庭 NAS、局域网及私有云环境。

---

## 🏗️ 架构概览

本项目严格遵循现代 Android 单一数据流架构（UDF）与整洁架构分层原则：

```
app/src/main/
├── cpp/                           # FFmpeg JNI 原生层 (C++/CMake)
│   ├── ffmpeg_demuxer.cc          # 自定义 ASF/WMA 等格式 Demuxer
│   └── ffmpeg_jni.cc              # JNI 接口适配
├── java/com/webdav/player/
│   ├── data/
│   │   ├── client/                # WebDAV 网络客户端 (OkHttp, XML 解析, SSL 信任)
│   │   ├── database/              # Room 数据库 (目录缓存、播放会话快照)
│   │   ├── metadata/              # HTTP Range 增量元数据提取与歌词解析
│   │   ├── repository/            # 响应式数据仓库 (SWR 目录仓库、服务器仓库)
│   │   └── service/               # MediaSessionService 前台媒体服务
│   ├── ui/
│   │   ├── browser/               # 远程目录浏览器与面包屑导航
│   │   ├── player/                # 沉浸式大播放器与逐行歌词界面
│   │   ├── server/                # WebDAV 服务器配置与状态管理
│   │   └── theme/                 # Material 3 动态色彩与排版主题
│   ├── MainActivity.kt            # 单 Activity 顶层脚手架
│   └── WebDavApplication.kt      # 全局单例依赖注入容器
```

---

## 🛠️ 技术栈

| 模块 | 选型 |
| :--- | :--- |
| **编程语言** | Kotlin 1.9+, C++17 |
| **界面框架** | Jetpack Compose (Material 3) |
| **架构组件** | ViewModel, Coroutines, StateFlow, Navigation Compose |
| **媒体引擎** | AndroidX Media3 (ExoPlayer 1.2.1) + FFmpeg Native Extension |
| **网络层** | OkHttp 4.12, OkHttp Logging Interceptor, 自定义 WebDAV PROPFIND 解析 |
| **持久层** | Room 2.6 (KSP), Jetpack DataStore Preferences |
| **Native 构建** | CMake 3.22+, Android NDK 27+ |

---

## 🚀 编译与构建

### 1. 环境要求
- **Android Studio** Hedgehog (2023.1.1) 或更高版本
- **JDK**：Java 17 或 Java 21
- **Android SDK**：API 34 (Compile SDK) / Android 10+ (Min SDK 29)
- **Android NDK**：27.0.12077973+
- **CMake**：3.22.1+

### 2. 本地构建
克隆仓库后直接通过 Gradle Wrapper 编译：

```bash
# Debug 版本
./gradlew assembleDebug

# Release 版本
./gradlew assembleRelease
```

编译输出目录：`app/build/outputs/apk/`

---

## 📱 使用指南

1. **添加服务器**：点击底栏「服务器」页面，填写您的 WebDAV 主机地址、端口、路径以及账号密码（如开启 HTTPS 且为自签名证书，可打开「信任自签名证书」开关）。
2. **浏览曲目**：切换到「曲库」页面，即可像浏览本地文件一样浏览远程 NAS 或网盘目录。
3. **播放与控制**：点击任意音频曲目即刻起播，屏幕底部会浮出 Mini-Player；上滑或点击卡片可展开沉浸式全屏播放器并查看同步歌词。

---

## 📄 许可证

本项目基于 [Apache License 2.0](LICENSE) 协议开源。
