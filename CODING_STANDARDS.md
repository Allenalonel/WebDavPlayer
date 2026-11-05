# Coding Standards

These standards are enforced during `/code-review` alongside the Fowler code-smell baseline. Mechanical rules (import directions, ProGuard rules, binary budgets) are enforced deterministically in `PackagingSeamVerificationTest`.

---

## 1. Module Depth & Orthogonal Decomposition (模块深度与正交解耦)

- **Judge by Responsibility, Not Line Count**:
  Evaluate modules by **change-axis orthogonality (Divergent Change)** and **interface depth (leverage)**, never by rigid line-count limits.
  - **When to keep together**: If a module has a narrow interface and a single cohesive responsibility (e.g., binary stream demuxing in `AsfExtractor`, CUE sheet parsing in `CueParser`, or a self-contained Compose component), keep it in one file even if it spans 400–600+ lines.
  - **When to split (Internal Seam Discipline)**: When a single implementation class accumulates **2 or more orthogonal sub-domains** (e.g., session persistence + queue topology + network fault watchdog) or exhibits high constructor fan-out (6+ unrelated dependencies), extract package-private (`internal`) collaborators while keeping the external interface narrow.
- **No Artificial Fragmentation**:
  Do not split a cohesive 150–400 line component into tiny single-caller satellite files (`*Collaborators.kt`, 30-line pill wrappers) that force readers to bounce across multiple files to understand one concept.
- **No Formatting Tricks**:
  Never chain multiple statements on a single line with semicolons (`;`) or compress parameter lists to artificially reduce line counts.

---

## 2. Seam Discipline & Zero Zombie Forwarders (接缝纪律与零僵尸转发)

- **Two Adapters Justify a Seam**:
  Do not introduce an interface if it only ever has one concrete implementation and no test stand-in. Collapse single-adapter wrappers into their owning module.
- **Delete Transitional Scaffolding (No Middle Man)**:
  When migrating callers to a new or deepened seam (such as `AppViewModelFactory`, `AudioTrackRow`, or `PlaybackActions`), delete the old pass-through factories, compatibility aliases, and `@Deprecated` shells in the same change. Never leave zero-production-caller forwarding files in the tree.

---

## 3. Test Surface Discipline (测试表面纪律)

- **The Interface Is the Test Surface**:
  Write tests against the public interface and observable behavior of a module.
- **No Implementation-Locking Assertions**:
  - Do not use reflection (`Class.forName("...Kt")`, `declaredMethods`) to assert on internal Kotlin file facades or private/internal helper names.
  - Do not hardcode file line-count assertions (`readLines().size <= X`) inside unit tests.

---

## 4. Cache Elasticity & Persistence Boundaries (缓存弹性与持久化边界)

- **No Hard SQLite Foreign Keys on Cache Tables**:
  `directory_cache` and `track_metadata` must **not** declare `FOREIGN KEY (serverId) REFERENCES webdav_servers(id) ON DELETE CASCADE`. Cross-server playlists (`ADR-0020`) and background metadata self-healing (`ADR-0022`) require reading and writing track snapshots even when a server is offline, unconfigured, or isolated in DAO tests. Cascade cleanup upon explicit server deletion is handled in `ServerRepositoryImpl.deleteServer`.

---

## 5. Mainstream Engineering Benchmark & Pragmatic Review (对标主流工业级标准与务实审查)

- **Benchmark Against Mainstream Production Engineering (对标主流工业级标准，拒绝教条化审查)**:
  The sole North Star for architecture review is **mainstream production-grade software engineering**: standard architectural patterns, industry benchmarks, framework-idiomatic conventions, and pragmatic maintainability.
  Strictly avoid "reviewing for the sake of reviewing" or applying extreme academic/theoretical dogmas that deviate from mainstream industry engineering.
- **Problem-Driven with Measurable Engineering Value (问题驱动与实质收益)**:
  Any architectural finding or refactoring proposal must address genuine engineering friction: performance bottlenecks, concurrency/lifecycle hazards, memory leaks, boundary leakage, or real maintenance impediments.
  If an existing design aligns with mainstream framework idioms, is cleanly structured, and reliably passes all quality/performance gates, it is solid engineering. Strictly prohibit inventing make-work, circular refactorings (endless splitting vs. merging), or aesthetic churn that delivers zero practical engineering value.
- **Review Stopping Discipline (终止准则与收敛边界)**:
  An architectural review must converge. When an audit identifies that a subsystem adheres to mainstream standards and exhibits no real-world defects, it must definitively conclude that the architecture is production-ready, rather than perpetually digging for artificial issues.

---

## 6. Fault Lifecycle Symmetry & Diagnostic Parity (故障生命周期对称闭环与诊断对等)

- **Symmetric Fault Lifecycle (故障对称闭环原则)**:
  凡在仓储或持久层设计了写入持久化错误、降级或置灰标记（如 `markTrackInactive`、`markServerInactive`）的接缝，必须在同一领域边界内提供对等、全自动的解除与自愈信道（如探活自愈、成功出声反哺、测试连接联动清除）。严禁只写不销、只增不减的单向状态累积，杜绝节点交互死锁。
- **Diagnostic Aggregation Parity (UI 诊断聚合与领域自愈对等原则)**:
  当表现层将多个底层故障枚举（如 `SERVER_UNREACHABLE` 与瞬态 401/403 `SERVER_CREDENTIAL_INVALID`）聚合映射为同一用户提示文案时，领域自愈层与状态清除逻辑必须以相同的广度覆盖这些枚举，彻底消除“UI 展现已聚合、底层自愈漏一半”引发的认知死角。
- **Failure Granularity Alignment (故障粒度对齐原则)**:
  针对整机/服务级异常（如网络拒绝、超时、服务重启认证不可用），故障标记与恢复必须在服务器级批量同步，严禁仅对当前报错单曲标记而留同源排队曲目高亮，杜绝同源数据展现割裂。

---

## 7. Motion & Container Morphing Architecture (动效系统与容器形变工程准则)

- **Single Living Entity Principle (单一真实实体原则，严禁过渡替身偷换)**:
  在容器连续形态蜕变（Morphing Container）与多阶界面过渡中，严禁编写仅具备部分假 UI 的过渡期“替身克隆组件（Mock Clone）”。展开或切换的目标界面必须自始至终由其真实组件充当唯一实体，通过 `graphicsLayer` 的 Alpha 与 Translation 随容器几何伸展平滑浮现并微移归位。禁止在动画终点执行“替身到真实视图”的偷换（End-State Swap），彻底从物理根源上消灭排版重算导致的界面突发抖动与组件跳跃。
- **Draw Phase Animation Rule (GPU 绘制阶段渲染原则)**:
  在 60/120fps 连续高频形变中，严禁逐帧修改 `Modifier.size()` 与 `Modifier.offset()`（这会强迫 Compose 逐帧回退至 CPU Measure & Layout 测量重排与图片重采样，引发掉帧卡顿）；必须以固定目标尺寸为基底，统一在 `Modifier.graphicsLayer { scaleX/Y = ...; translationX/Y = ...; shape = ...; clip = true }` 内通过 GPU 矩阵变换完成缩放位移，保障 CPU 零重新测算与 120Hz 硬件加速。

