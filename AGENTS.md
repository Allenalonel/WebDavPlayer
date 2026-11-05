# Aoffee Player

本项目是一个 Android WebDAV 高保真流式音乐播放器应用（原 WebDAVPlayer），询问我需求和意见时使用中文。

## Agent skills

### Issue tracker

Issues and specs are tracked as local markdown files under `.scratch/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Canonical triage roles mapped to standard label strings. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context domain documentation at the repository root (`GLOSSARY.md` and `docs/adr/`). See `docs/agents/domain.md`.

### Coding standards

Architectural and code-review standards live in `CODING_STANDARDS.md`.

### Subagent execution & concurrency

When running `implement-spec` or multi-agent workflows with implementer subagents:

- **Concurrency ceiling**: Run up to **2–4 subagents in parallel** in isolated worktrees (`worktree: true`) to balance execution speed and Gradle daemon memory footprint.
- **Targeted verification**: Subagents in their worktrees must run targeted module tests (`--tests "*<TestClass>*"`), reserving full `./gradlew testDebugUnitTest` and release packaging verification (`verifyReleasePackaging`) for the final integration branch gate.
- **Shared resource scaffolding & file boundaries (公共资源脚手架先行与文件边界隔离)**: 在派发并行 implementer subagents 之前，若多个工单依赖共同的新建公共类库（如 `MotionTransitions.kt`、新增公共数据模型或集中配置），主智能体必须预先在集成分支创建好基础骨架文件与符号桩代码，或者按业务领域将代码垂直拆分到不同文件，严禁多个并发代理向同一个全新空文件自由发挥写入未协调的代码，杜绝跨工单合并冲突。

### Interaction & execution boundary (讨论与执行边界契约)

- **严格区分“讨论态”与“执行态”**：当指令或提示词中包含“讨论”、“方案”、“评估”、“分析”、“对比”、“Grill”、“思考”等探索/协商性词汇时，智能体处于只读思考与交流模式，**严禁调用任何写入工具（`edit` / `write`）修改业务代码**。
- **强制等待明确授权**：只有在用户明确发出指令（如“开始实施”、“执行”、“确认修改”、“开写”）后，方可进入执行态并执行文件变更。

### UI visual sanity & interaction smoke check (UI 真机视觉与交互冒烟自检铁律)

- **主动截图审查与动态冒烟交互闭环**：凡涉及核心布局、导航底栏、悬浮播放器、全屏播放器、搜索展开或歌词等表现层或交互特性的改动，在交付用户前，智能体必须完成两阶段闭环自检：
  1. **静态像素级审查**：主动执行 `adb exec-out screencap -p > .tmp/screen.png` 截屏并使用 `read` 工具读取图片进行像素级自查（验证居中度、内外边距、字体对比度及手势避让）；
  2. **核心动态冒烟巡检**：通过 `adb shell input tap/swipe` 对本次修改涉及的关键交互链路（如搜索展开、子目录穿梭、播放器展开折叠、模式切换）执行至少 1 次真机动态触发，并主动检查 `adb logcat -d -b crash` 确保 0 崩溃，自证动态交互无白屏、无抖动、无残影后方可交付。

### Empirical runtime debugging (真机与运行时实证排查铁律)

- **拒绝无实证的理论假设**：当排查涉及跨组件状态机、数据库持久化脱节、自愈未生效或离线置灰等问题时，严禁仅凭静态代码走读盲目假设故障原因，更严禁在未获真机实证前宣称“已修复”。
- **真实环境先导诊断（ADB/DB First）**：已连接无线调试（ADB）时，必须优先通过 `adb logcat`、导出或查询 SQLite 数据库（`webdav_player.db`）等工具获取第一手运行时状态。以真实落盘数据和运行时日志为唯一定位标尺，彻底规避因 UI 错误文案模糊聚合而引发的脱靶推断。

### Release automation (版本发布工具契约)

- **优先使用官方 GitHub CLI**：系统已安装官方 `gh` CLI（v2.103.3+）。后续发布版本时，统一使用 `gh release create <tag> <apk_path> --notes "..."`（必要时注入环境变量 `GH_TOKEN` 与 `HTTPS_PROXY`），杜绝即兴编写易受平台差异干扰的临时脚本。
