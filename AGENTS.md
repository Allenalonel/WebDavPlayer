# WebDavPlayer

本项目是一个 Android WebDAV 音乐播放器应用，询问我需求和意见时使用中文。

## Agent skills

### Issue tracker

Issues and specs are tracked as local markdown files under `.scratch/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Canonical triage roles mapped to standard label strings. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context domain documentation at the repository root. See `docs/agents/domain.md`.

## Architectural Guidelines

### 内部接缝规范（Internal Seam Discipline）
- **深模块不等于大文件**：深层模块对外呈现单一、窄小的接缝（Interface），但其内部实现若涉及 2 个以上的正交子领域逻辑（如持久化落盘、队列状态机、网络故障自愈），**必须建立包级私有（internal）协作者类**进行解耦，严禁堆砌在单一实现类中。
- **引力黑洞防范与单文件熔断线**：核心业务实现类严格控制在 **600 行以内**。当引入新特性被其他模块排除出来时，必须明确归属到具体的私有协作者中，严禁无节制向 `MusicPlayerAppSessionImpl` 或主 ViewModel 追加代码。

