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
- **Concurrency ceiling**: Run up to **2–3 subagents in parallel** in isolated worktrees (`worktree: true`) to balance execution speed and Gradle daemon memory footprint.
- **Targeted verification**: Subagents in their worktrees must run targeted module tests (`--tests "*<TestClass>*"`), reserving full `./gradlew testDebugUnitTest` and release packaging verification (`verifyReleasePackaging`) for the final integration branch gate.


