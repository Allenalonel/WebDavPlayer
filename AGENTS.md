# WebDavPlayer

本项目是一个 Android WebDAV 音乐播放器应用，询问我需求和意见时使用中文。

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

### Interaction & execution boundary (讨论与执行边界契约)

- **严格区分“讨论态”与“执行态”**：当指令或提示词中包含“讨论”、“方案”、“评估”、“分析”、“对比”、“Grill”、“思考”等探索/协商性词汇时，智能体处于只读思考与交流模式，**严禁调用任何写入工具（`edit` / `write`）修改业务代码**。
- **强制等待明确授权**：只有在用户明确发出指令（如“开始实施”、“执行”、“确认修改”、“开写”）后，方可进入执行态并执行文件变更。


