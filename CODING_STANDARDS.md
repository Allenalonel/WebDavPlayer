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
