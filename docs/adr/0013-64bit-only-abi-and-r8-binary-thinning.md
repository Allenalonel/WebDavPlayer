# 0013: 64-Bit Only Native ABI and R8 Binary Thinning Packaging Seam

## Context

在对发布版本二进制产物进行全面审查及跨项目对比（对比同类开源播放器 Folder-Player 7.08 MB）时，发现 WebDavPlayer 的 Release APK 高达 **56.8 MB**。通过对 APK 解包并按模块深度测算，体积过度膨胀由以下三个构建工程与打包接缝问题引起：

1. **DEX 字节码极端膨胀（44.56 MB）**：
   `app/build.gradle.kts` 中关闭了 R8 混淆（`isMinifyEnabled = false`），同时项目依赖了 `androidx.compose.material:material-icons-extended`。由于缺少代码摇树优化（Tree Shaking），数万个未引用的 Compose 矢量图标类及绘制路径全量编译入包，产生了 3 个 classes.dex 文件，总计达 44.56 MB。
2. **过时的 32 位双架构 SO 冗余（18.14 MB 未压缩 / 8.98 MB 压缩）**：
   项目为了支持 WMA 软解与受限 Range 元数据探针，打包了完整的 C++ FFmpeg 动态库套件（`libavcodec`, `libavformat`, `libavutil`, `libswresample`, `libffmpegJNI`）。在单一 APK 内同时打包了 `arm64-v8a` 与 `armeabi-v7a`。然而本项目基线为 Android 10（`minSdk = 29`），现役 Android 10+ 手机几乎 100% 为 64 位 ARMv8 架构，32 位库造成了 100% 的无意义体积冗余。
3. **未启用资源缩减（Resource Shrinking）**：
   未使用 `isShrinkResources = true`，无用样式和资源未在打包阶段剔除。

## Decision

建立严格的**构建与打包工程接缝（Packaging Seam）**，执行深度的二进制瘦身策略：

1. **强制 64 位单架构 ABI 过滤（Drop 32-Bit armeabi-v7a）**：
   - 在 `app/build.gradle.kts` 的 `defaultConfig.ndk.abiFilters` 中明确仅保留 `listOf("arm64-v8a")`，彻底剔除过时的 `armeabi-v7a`。
   - 杜绝在通用 APK 中塞入双架构，立即将 Native SO 占用降低 50%（直接省去约 9 MB 压缩体积）。
2. **剔除 `material-icons-extended` 并建立局部化 `AppIcons` 接缝**：
   - 从 `build.gradle.kts` 中删除全量扩展图标库依赖。
   - 在 `com.webdav.player.ui.theme` 中建立专用的 `AppIcons` 目录，仅集中收录应用实际使用到的 15~20 个图标矢量定义，从源码源头根绝 40+ MB 的死代码侵入。
3. **全面开启 R8 代码混淆与资源缩减**：
   - 在 `buildTypes.release` 中配置 `isMinifyEnabled = true` 与 `isShrinkResources = true`。
   - 在 `app/proguard-rules.pro` 中为 Room 实体/DAO、Media3 媒体会话、OkHttp 网络客户端以及 C++ JNI 本地桥接方法（`FfmpegAudioDecoder`、`AsfExtractor`、`ffmpeg_jni`）建立纵深保护规则，确保反射与原生接口调用零异常。
4. **设立发布包体积预算警戒线（Size Budget）**：
   - 确立 Release APK 硬性预算阈值：**总包体 <= 7.5 MB（预期目标约为 6.5 MB，降幅达 88%）**，且仅允许存在单一 `classes.dex`。

## Consequences

- **包体积断崖式下降**：Release APK 体积将直接从 **56.8 MB 锐减至 ~6.5 MB**，安装包体积降幅达到 88%，全面超越同类主流播放器的轻量化水平。
- **冷启动与类加载加速**：DEX 文件从 3 个缩减为 1 个（体积从 44.56 MB 降至 3~4 MB），极大地降低了系统 Dalvik/ART 虚拟机的类加载耗时，冷启动响应显著加快。
- **消灭 32 位维护负担**：不再受制于 32 位架构内存寻址限制，所有 Native 软解线程全额享受 64 位 NEON 寄存器与指令集优化。
- **架构纯度与局部性（Locality & Leverage）提升**：UI 模块拥有对自身图标资产的显式掌控力，消除了隐式的第三方库依赖蔓延；同时未改动底层任何音频流式回放业务代码，零功能退化风险。
