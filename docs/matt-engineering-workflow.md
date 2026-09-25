# Matt Pocock 工程技能规范（Engineering Workflow Guide）

> 本文档梳理了 Matt Pocock 技能体系的完整研发生命周期模型、各阶段 Skill 的调用方式、流程图、缺陷诊断（Bugfix）流转、中途追加 Issue 规范与工程最佳实践。供日常开发、新特性立项、冒烟测试及代码库维护查阅。

---

## 目录
- [一、生命周期全景流程图（Mermaid 图形版）](#一生命周期全景流程图mermaid-图形版)
- [二、生命周期全景流程图（纯文本/终端版）](#二生命周期全景流程图纯文本终端版)
- [三、核心生命周期阶段与 Skill 详解](#三核心生命周期阶段与-skill-详解)
  - [阶段 0：前置初始化（仅需一次）](#阶段-0前置初始化仅需一次)
  - [阶段 1：需求研讨与审问（Idea & Exploration）](#阶段-1需求研讨与审问idea--exploration)
  - [阶段 2：规格沉淀与切片拆解（Spec & Tickets）](#阶段-2规格沉淀与切片拆解spec--tickets)
  - [阶段 3：工单流水线实施（Implementation Loop）](#阶段-3工单流水线实施implementation-loop)
  - [阶段 4：特性交付、冒烟验收与缺陷处理（Feature Close-out & Defect Loop）](#阶段-4特性交付冒烟验收与缺陷处理feature-close-out--defect-loop)
  - [阶段 5：架构体检与日常运维（Upkeep & Bugfix）](#阶段-5架构体检与日常运维upkeep--bugfix)
- [四、缺陷诊断纪律（/diagnosing-bugs 6 步闭环法）](#四缺陷诊断纪律diagnosing-bugs-6-步闭环法)
- [五、中途追加 Issue / 缺陷立项的规范流程（On-ramp Guide）](#五中途追加-issue--缺陷立项的规范流程on-ramp-guide)
  - [1. 为什么中途加工单不需要重新走 `/to-spec`？](#1-为什么中途加工单不需要重新走-to-spec)
  - [2. 中途立项的标准流程与 `/triage` 的使用](#2-中途立项的标准流程与-triage-的使用)
  - [3. 工单编写契约（遵循 AGENT-BRIEF.md）](#3-工单编写契约遵循-agent-briefmd)
- [六、真机冒烟测试与 ADB 联调最佳实践](#六真机冒烟测试与-adb-联调最佳实践)
  - [1. 安装包选择：为什么冒烟优先用 Debug？](#1-安装包选择为什么冒烟优先用-debug)
  - [2. 纯 Wi-Fi 原生无线调试连接指引](#2-纯-wi-fi-原生无线调试连接指引)
  - [3. 日志抓取与排错规范（非阻塞 Dump）](#3-日志抓取与排错规范非阻塞-dump)
- [七、上下文卫生法则（Context Hygiene）](#七上下文卫生法则context-hygiene)
- [八、Spec 验收与交付检查清单（Checklist）](#八spec-验收与交付检查清单checklist)

---

## 一、生命周期全景流程图（Mermaid 图形版）

```mermaid
flowchart TD
    %% 阶段 0
    subgraph S0 ["【阶段 0：工程规范初始化】"]
        Init["/setup-matt-pocock-skills<br/>配置 Issue Tracker、Triage 标签、领域文档模式"]
    end

    %% 任务切入口分流 (On-ramps)
    Init --> Router{任务类型分类}
    Router -->|新功能 / 清晰特性主线| S1_Grill
    Router -->|超大迷雾项目 Greenfield| R_Wayfinder["【切入口 2：超大迷雾】<br/>/wayfinder<br/>建立决策拓扑地图 (map.md)"]
    Router -->|外部 Issue / 零散排队需求| R_Triage["【切入口 3：分流立项】<br/>/triage<br/>状态机评估，编写 Agent Brief"]
    Router -->|运行中偶发疑难 Bug| R_Diag["【切入口 4：疑难排障】<br/>/diagnosing-bugs<br/>红绿反馈闭环定位"]

    %% 阶段 1：主线研讨
    subgraph S1 ["【阶段 1：需求研讨与审问】（事实归 AI，决策归人类）"]
        S1_Grill["/grill-with-docs<br/>苏格拉底式提问对齐需求<br/>沉淀 CONTEXT.md 词汇与 ADR 决策"]
        S1_Grill --> BranchProto{设计或状态<br/>是否存疑？}
        BranchProto -->|是| S1_Proto["/prototype<br/>抛弃型代码探路验证"]
        S1_Proto -->|/handoff 跨会话带回结论| S1_Grill
        BranchProto -->|否| S2_Spec
    end

    %% 阶段 2：规格与拆解
    subgraph S2 ["【阶段 2：规格与切片拆解】"]
        S2_Spec["/to-spec &lt;slug&gt;<br/>只提炼不采访 (No interview)<br/>输出 spec.md (User Stories + Seams)"]
        S2_Spec --> S2_Tickets["/to-tickets<br/>拆解垂直切片微工单 (Tracer Bullets)<br/>声明依赖拓扑 (01-xx.md, 02-xx.md)"]
    end

    %% Wayfinder 决策收敛后汇合至 to-spec
    R_Wayfinder -->|地图清晰，折叠合并决策| S2_Spec

    %% Triage 产出的独立工单直接进入实施阶段
    R_Triage -->|产出 ready-for-agent 单工单| S3_Start

    %% 关键分界线：清理上下文
    S2_Tickets ==>|执行 /clear 刷新上下文| S3_Start

    %% 阶段 3：实施流水线
    subgraph S3 ["【阶段 3：工单流水线实施】（单工单闭环迭代）"]
        S3_Start["领取无前置依赖的前沿工单 (Frontier)"] --> S3_Impl["/implement &lt;ticket-path&gt;"]
        
        subgraph S3_TDD ["内部驱动 /tdd 循环"]
            T1["1. 确认公开测试切面 (Seam)"] --> T2["2. Red: 编写失败测试"]
            T2 --> T3["3. Green: 编写最简实现通过测试"]
            T3 --> T4["4. 重构优化"]
        end
        
        S3_Impl --> S3_TDD
        S3_TDD --> S3_Review["内部驱动 /code-review<br/>双轴审查 (Standards 轴 + Spec 轴)"]
        S3_Review --> S3_Commit["工单验收项打勾，提交 Git"]
        S3_Commit --> CheckDone{所有工单<br/>是否完成？}
        CheckDone -->|否: 执行 /clear| S3_Start
    end

    %% 阶段 4：交付与验收
    subgraph S4 ["【阶段 4：特性交付、冒烟验收与缺陷处理】"]
        CheckDone -->|是| S4_Regression["1. 全量回归测试全绿 (.\\gradlew test)"]
        S4_Regression --> S4_GlobalReview["2. 全局 /code-review 宏观复核"]
        S4_GlobalReview --> S4_Smoke["3. 真机 / 模拟器核心用户旅程冒烟"]
        
        S4_Smoke --> SmokeResult{冒烟测试是否<br/>发现 Bug / 缺陷？}
        
        %% 缺陷处理分支
        SmokeResult -->|发现局部Bug/崩溃/性能卡死| S4_Diag["【分支 A：即时缺陷】<br/>/diagnosing-bugs<br/>6 步红绿诊断闭环 (测试先行)"]
        SmokeResult -->|发现技术断层/结构缺漏/跨版本适配| S4_Triage["【分支 B：追加工单】<br/>/triage<br/>建立 Agent Brief 生成 10-xx.md"]
        
        S4_Diag -->|修复后重测| S4_Regression
        S4_Triage -->|清空上下文 /clear| S3_Start

        SmokeResult -->|否: 5 道防线全部通过| S4_Close["4. spec.md 标记 Status: completed<br/>特性正式结项归档"]
    end

    %% 独立诊断后回流
    R_Diag -->|定位修复| S4_Regression

    %% 阶段 5：架构健康
    subgraph S5 ["【阶段 5：架构体检与日常运维】"]
        S4_Close --> S5_Maintain["日常维护循环"]
        S5_Maintain --> S5_Arch["/improve-codebase-architecture<br/>扫描深化模块机会 (Deep Modules)"]
        S5_Maintain --> S5_Bugs["发现偶发/硬核 Bug"]
        S5_Bugs --> R_Diag
    end

    %% 样式美化
    style S0 fill:#f4f6f8,stroke:#b0bec5,stroke-width:1px
    style S1 fill:#e8f4fd,stroke:#90caf9,stroke-width:1px
    style S2 fill:#fef9e7,stroke:#ffe082,stroke-width:1px
    style S3 fill:#e8f8f5,stroke:#a3e4d7,stroke-width:1px
    style S4 fill:#f5eef8,stroke:#d7bde2,stroke-width:1px
    style S5 fill:#fdfefe,stroke:#cfd8dc,stroke-width:1px
    style S4_Diag fill:#fadbd8,stroke:#e74c3c,stroke-width:2px
    style S4_Triage fill:#fdebd0,stroke:#f39c12,stroke-width:2px
```

---

## 二、生命周期全景流程图（纯文本/终端版）

```text
========================================================================================
【阶段 0：前置初始化】
  /setup-matt-pocock-skills  --> 配置 Issue Tracker (本地/GitHub)、Triage 标签与领域文档模式
========================================================================================
                                     │
      ┌──────────────────────────────┼──────────────────────────────┐
      │ 【切入口 1：新特性主线】       │ 【切入口 2：超大迷雾项目】     │ 【切入口 3：外部工单/需求】
      │                              │ (Greenfield / 庞大复杂项目)  │
      │                              ▼                              ▼
      │                         /wayfinder                      /triage
      │                     (建立拓扑决策地图，逐个击破)        (状态机流转、评估与筛选)
      │                              │                              │
      ▼                              ▼                              │
【阶段 1：需求研讨与审问】           └──────────────┐               │
  /grill-with-docs (或 /grill-me)                   │               │
  • 无情提问澄清边界，事实归 AI，决策归人类         │               │
  • 自动更新 CONTEXT.md 领域词汇与 ADR 决策         │               │
      │                                             │               │
      ├──────────────────┐                          │               │
      │ (有技术/UI不确定性)│                          │               │
      ▼                  ▼                          │               │
  /prototype          /handoff (跨上下文传递)        │               │
  (写抛弃型代码探路)     │                          │               │
      └──────────────────┘                          │               │
      │                                             │               │
      ▼                                             │               │
====================================================│===============│===================
【阶段 2：规格与切片拆分】                          ▼               │
  /to-spec  <───────────────────────────────────────┘               │
  • 只提炼不采访 (No interview, just synthesis)                     │
  • 产出带 User Stories 与 Testing Seams 的 spec.md                 │
      │                                                             │
      ▼                                                             │
  /to-tickets                                                       │
  • 将 Spec 切成垂直切片微工单（Tracer-bullet）                     │
  • 声明依赖拓扑（Blocked by），输出 01-xx.md, 02-xx.md             │
      │                                                             │
      │                                                             ▼
      ├─────────────────【刷新上下文：/clear】─────────────────────>│
      │                                                             │
====================================================================│===================
【阶段 3：工单流水线实施（单工单闭环）】                             │
      ▼                                                             │
  /implement <工单路径> <───────────────────────────────────────────┘
  (领取无前置依赖的 Frontier 工单)
      │
      ├─> 驱动 /tdd (内部循环)
      │     1. 确认测试切面 (Confirm Seams)
      │     2. Red (写失败测试) → Green (最简实现) → Refactor
      │
      ├─> 驱动 /code-review (内部审查)
      │     双轴审查：Standards 轴 (代码规范) + Spec 轴 (工单验收条件)
      │
      └─> 打勾工单验收项，Git Commit
      │
      ▼
  【执行 /clear】 ──> 流转到下一个被解锁的工单，直到所有工单完毕
========================================================================================
                                     │
                                     ▼
【阶段 4：特性交付、冒烟验收与缺陷处理】
  1. 全量回归测试全绿 (.\gradlew test)
  2. 全局 /code-review 宏观复核
  3. 真机 / 模拟器核心用户旅程冒烟测试 (支持 ADB 无线调试与非阻塞 Dump)
         │
         ├───【发现异常/缺陷】────────────────────────────────────────────┐
         │                                                                 │
         │   [分支 A：代码级 Bug / 性能卡死 / 逻辑死循环]                  │
         │     --> /diagnosing-bugs                                        │
         │         (紧密红灯回路 -> 最小化 -> 探针插桩 -> 修复转绿)         │
         │         --> 修复后回流至：1. 全量回归测试                       │
         │                                                                 │
         │   [分支 B：功能遗漏 / 技术断层 / 跨平台兼容 / 需独立立项]       │
         │     --> /triage (评估并编写 Agent Brief)                        │
         │         --> 建立新工单 issues/10-xxx.md (无需 /to-spec)         │
         │         --> 执行 /clear 后回流至：阶段 3 (/implement 实施)      │
         │                                                                 │
         └───【验收无误：5 道防线全部通过】                                │
                 │                                                         │
                 ▼                                                         │
          4. 将 spec.md 标记为 Status: completed                           │
===========================================================================│============
                                     │                                     │
                                     ▼                                     │
【阶段 5：架构体检与日常运维】 <───────────────────────────────────────────┘
  • /improve-codebase-architecture : 扫描深化模块机会 (Deep Modules)
  • /diagnosing-bugs               : 日常排查偶发/难啃的生产 Bug
========================================================================================
【底层通用语汇支撑（贯穿始终）】
  • /domain-modeling : 统一名词，消除一词多义，更新 CONTEXT.md
  • /codebase-design  : 模块深度（Depth）、切面（Seam）、适配器（Adapter）设计基准
========================================================================================
```

---

## 三、核心生命周期阶段与 Skill 详解

### 阶段 0：前置初始化（仅需一次）

| 技能命令 | 适用时机 | 核心行为与输出 |
| :--- | :--- | :--- |
| **`/setup-matt-pocock-skills`** | 新项目建立或首次接入规范时 | 交互式生成：<br>1. `docs/agents/issue-tracker.md`（工单存储配置）<br>2. `docs/agents/triage-labels.md`（状态标签映射）<br>3. `docs/agents/domain.md`（单/多上下文模式） |

---

### 阶段 1：需求研讨与审问（Idea & Exploration）

*核心原则：事实归 AI 调研，决策归人类拍板。绝不带着含糊不清的假设写代码。*

| 技能命令 | 适用时机 | 核心行为与输出 |
| :--- | :--- | :--- |
| **`/grill-with-docs <想法>`** | **主线首选**：在具备代码库的目录下细化新想法 | 开启苏格拉底式提问，穷追技术边界；自动将提炼的名词更新到 `CONTEXT.md`，将重大技术选型写入 `docs/adr/`。 |
| **`/grill-me <想法>`** | 独立于代码库的轻量讨论（纯脑暴） | 无状态版的需求审问，不写文件，纯粹用于推敲概念。 |
| **`/wayfinder`** | 迷雾重重的宏大需求或冷启动项目 | 当一两个问题无法在当前会话敲定时，建立一张带拓扑关系的决策地图（`map.md`），逐个排查决策票，直到迷雾散去**直接合流至 `/to-spec`**。 |
| **`/prototype`** | 状态流转或 UI 交互必须亲眼看到才确定 | 编写可运行但随时可丢弃的最小原型代码；通过 `/handoff` 将验证结论带回主讨论线程。 |

---

### 阶段 2：规格沉淀与切片拆解（Spec & Tickets）

*核心原则：只提炼不采访（No interview, just synthesis）。*

| 技能命令 | 适用时机 | 核心行为与输出 |
| :--- | :--- | :--- |
| **`/to-spec <feature-slug>`** | 需求研讨充分对齐之后 | 停止反问，忠实提炼共识。输出包含 `Problem Statement`、`Solution`、超长 `User Stories` 列表、`Implementation Decisions`、`Testing Seams` 和 `Out of Scope` 的 `.scratch/<slug>/spec.md`。 |
| **`/to-tickets`** | `spec.md` 生成完毕后 | 将 Spec 切割为多个**垂直切片（Tracer Bullets）**工单，每个工单贯穿 UI、业务、数据与测试；显式声明 `Blocked by` 依赖关系，生成 `issues/01-xx.md` 等。 |

> ⚠️ **关键节点操作**：`to-tickets` 执行完毕后，必须在终端输入 **`/clear`** 结束规划阶段，以干净的上下文迎接编码。

---

### 阶段 3：工单流水线实施（Implementation Loop）

*核心原则：单窗口串行流水线，严禁未解锁工单并发；每次做完必清上下文。*

| 技能命令 | 适用时机 | 核心行为与输出 |
| :--- | :--- | :--- |
| **`/implement <工单路径>`** | 领取当前无阻塞的 Frontier 工单 | **自动驱动以下全套动作：**<br>1. **驱动 `/tdd`**：先向人类确认公开测试切面（Seam），编写失败测试（Red），再写实现转绿灯（Green），最后重构；<br>2. **驱动 `/code-review`**：双轴自动审查代码质量与工单验收项；<br>3. 自动将工单验收项打勾 `- [x]`，标记为 `resolved` / `completed` 并完成 Git Commit。 |

> ⚠️ **关键节点操作**：每完成一个工单并提交后，立即输入 **`/clear`**，然后再执行下一个已解锁的工单。

---

### 阶段 4：特性交付、冒烟验收与缺陷处理（Feature Close-out & Defect Loop）

*核心原则：五道防线层层验证，缺陷分流处置，人类拥有最终结项权。*

#### 1. 验证流程五道防线
- **第 1 道防线（工单验收）**：检查所有子工单是否均已打勾并处于 `resolved` / `completed` 状态。
- **第 2 道防线（全局回归）**：运行全量单元测试（如 `.\gradlew.bat testDebugUnitTest`），确保无任何代码破坏与退化。
- **第 3 道防线（全量审查）**：运行 **`/code-review`** 对照原始 `spec.md`，重点复核是否存在漏做（Missing）或越界（Scope Creep）。
- **第 4 道防线（真机冒烟）**：打出 APK 安装到真机/模拟器，人工走通核心用户主旅程（Happy Path）。
- **第 5 道防线（缺陷闭环）**：若在冒烟中发现 Bug，严格闭环排查并补齐回归测试。

#### 2. 验收期缺陷处理分流（Defect Handling）
如果在真机冒烟或回归测试中发现问题，**严禁拍脑袋盲目改代码**，按以下两种标准路径处理：

- **路径 A：即时缺陷修复（首选 `/diagnosing-bugs`）**：
  - 适用场景：局部逻辑死循环、焦点竞争、崩溃堆栈明确、性能卡死（如通知风暴、ANR、拖动进度条回弹）。
  - 执行纪律：调用 `/diagnosing-bugs <问题描述>`，执行严格的 6 步闭环法。
- **路径 B：追加工单立项（调用 `/triage`）**：
  - 适用场景：发现重大的底层技术鸿沟（Gap）、第三方库未包含所需解码器、跨大版本 OS 兼容要求（如 Android 16 下 16KB ELF 页面对齐）。
  - 执行纪律：**无需也不应该再跑 `/to-spec`**，直接通过 `/triage` 建立 `issues/10-xxx.md`，按工单流程规范推进。

#### 3. 结项归档
当所有缺陷修复完毕、5 道防线全部通过后：
- 将 `.scratch/<feature-slug>/spec.md` 顶部的 `Status: ready-for-agent` 改为 **`Status: completed`**。

---

### 阶段 5：架构体检与日常运维（Upkeep & Bugfix）

| 技能命令 | 适用时机 | 核心行为与输出 |
| :--- | :--- | :--- |
| **`/triage`** | 收集到了外部反馈或大量 Issue/PR | 状态机流转：`needs-triage` → `needs-info` → `ready-for-agent` / `ready-for-human` / `wontfix`。为 Agent 编写可独立执行的 Brief。 |
| **`/diagnosing-bugs`** | 线上日常运行中，遇到疑难崩溃或性能退化 | 不凭空猜测假设。通过构造一条必定红灯的测试指令锁定现场，最小化定位并提交回归测试。 |
| **`/improve-codebase-architecture`** | 闲暇时刻，对架构做日常体检 | 扫描代码库，识别“浅接口（Shallow interface）”和过度暴露的细节，挖掘“深模块（Deep module）”重构机会，提高后续 AI 协作效率。 |

---

## 四、缺陷诊断纪律（/diagnosing-bugs 6 步闭环法）

当调用 `/diagnosing-bugs` 时，智能体必须严格遵循以下 6 步法，跳过任何步骤都需要明确的工程理由：

```text
  Phase 1: 建立紧密反馈循环 (Build a feedback loop)
           【铁律：在能指出一条必定红灯报错的验证命令之前，严禁猜想假说！】
                        ↓
  Phase 2: 复现与最小化 (Reproduce + Minimise)
           【将诱发 Bug 的场景剔除无关干扰，裁剪到最小致病源】
                        ↓
  Phase 3: 提出可证伪假设 (Hypothesise)
           【列出 3~5 个按概率排序、带因果预测的可证伪假说，向人类确认】
                        ↓
  Phase 4: 针对性插桩排查 (Instrument)
           【单变量验证，打上 [DEBUG-xxxx] 标签日志或断点，严禁大面积漫灌日志】
                        ↓
  Phase 5: 测试先行与修复 (Fix + Regression Test)
           【先写红灯的回归测试用例，再编写最简代码修复转绿灯，全量测试自证正确】
                        ↓
  Phase 6: 现场清理与沉淀 (Cleanup)
           【移除所有调试插桩，将修复的真实原因记录在 Git Commit 历史中】
```

### 实践案例回顾（WebDavPlayer 排查实录）：
- **问题**：点选歌曲时主线程 ANR、ExoPlayer 频繁处于 Buffering 与 Paused 震荡。
- **Phase 1 & 2**：在 `MusicPlayerAppSessionTest` 中编写测试，模拟 Room 连续推送，断言 `fakeEngine.updateTrackCalls` 触发了非预期的倍数增长，成功在命令行跑出 `AssertionError`（红灯）。
- **Phase 3**：提出假设并向用户确认（假设 1：元数据未做 Diff；假设 2：引擎层无条件调用 `replaceMediaItem` 重置源；假设 3：音频焦点互相争抢）。
- **Phase 4 & 5**：增加 Diff 比对阻断无效更新，剥离纯函数副作用，测试转绿；全工程 30 个 Task 回归通过。
- **Phase 6**：清除探针日志并提交规范 Git 记录。

---

## 五、中途追加 Issue / 缺陷立项的规范流程（On-ramp Guide）

在研发过程中，经常会遇到最初拆解的工单全部执行完后，在真机冒烟时发现了**较大的技术断层（如：依赖库未包含专有解码器、系统底层机制不兼容）**。此时必须按规范中途追加工单。

### 1. 为什么中途加工单不需要重新走 `/to-spec`？

- **Spec 是宏观特性蓝图（Epic）**：
  最初制定的 `spec.md` 已经包含了该功能的业务目标、用户故事和架构决策（例如已包含了 WMA 支持与软解）。
- **这不是一个新特性，而是既定蓝图下的技术实现鸿沟（Gap）**：
  业务边界未变，因此**严禁推倒重来再写一份 spec**。
- **`/to-spec` 严格遵循“不采访”原则**：
  如果在中途用模糊的想法跑 `/to-spec`，不仅丢失前期上下文，还会重新生成冗余的文档。

---

### 2. 中途立项的标准流程与 `/triage` 的使用

当需要立项追加工单时，标准流程只需 3 步：

```text
  [发现新缺陷 / 技术鸿沟]
            ↓
  1. 调用 /triage 评估并立项
     (分析现状与目标，撰写符合规范的 Agent Brief，声明依赖与验收标准)
            ↓
  2. 自动生成工单文件并落盘
     (.scratch/<slug>/issues/<NN>-<slug>.md，标记 Status: ready-for-agent)
            ↓
  3. 执行 /clear 清空上下文
            ↓
  4. 调用 /implement 正常领取该工单
```

#### 调用示例：
在聊天框直接输入：
```bash
/triage 为 WMA 格式在 Android 16 下的 16KB 对齐与软件解码建立一个新的缺陷工单
```
或者自然语言：
> “/triage：我们在 Android 16 真机上发现当前动态库不满足 16KB 对齐要求，且现有 FFmpeg 库未编译 wmav2 解码器导致 WMA 无声。请为此新建一个工单，状态设为 ready-for-agent。”

---

### 3. 工单编写契约（遵循 AGENT-BRIEF.md）

生成的工单必须是一个**自包含、具备持久生命力的 Agent Brief**，包含以下核心字段：

```markdown
# 10: 16KB-Aligned FFmpeg Software Decoding for WMA on Android 16

**What to build:** The user can play WMA audio files with sound on Android 16 without encountering the "ELF file alignment check failed" system dialog. The app packages an updated, 16KB-aligned native decoder supporting wmav1, wmav2, and wmapro, correctly routing PCM samples to the audio sink.

**Blocked by:** 08: FFmpeg Software Decoding for WMA and Extended Formats

**Status:** ready-for-agent

- [ ] Native library is compiled with 16KB ELF alignment (p_align >= 16384), eliminating the Android 16 launch warning.
- [ ] FFmpeg native binary includes wmav1, wmav2, and wmapro decoders (ff_wmav2_decoder present).
- [ ] DecoderAudioRenderer successfully instantiates the decoder and receives non-null PCM audio buffers.
- [ ] Unit and integration tests verify WMA decoding and playback at standard and non-standard sample rates (e.g. 11025Hz, 44100Hz).
```

---

## 六、真机冒烟测试与 ADB 联调最佳实践

在阶段 4 的真实设备验收中，利用开发机与手机的直接联调能极大加速闭环。

### 1. 安装包选择：为什么冒烟优先用 Debug？
- **免配置签名**：Android 强制要求所有安装的 APK 必须签名。Gradle 打 `debug` 包时会自动使用内置公钥完成签名，可以直接双击或 `adb install`；而未签名的 `release` 包（`-unsigned.apk`）在手机上会直接报错“安装包解析错误”。
- **保留完整调试符号**：Debug 包默认 `debuggable = true`，异常堆栈可精确定位到 Kotlin 文件名和行号。

### 2. 纯 Wi-Fi 原生无线调试连接指引
在无需数据线的情况下，可直接通过 Wi-Fi 联调（适用于 Android 11+）：
1. 手机与电脑连入同一 Wi-Fi；
2. 手机打开 **开发者选项 -> 无线调试 -> 使用配对码配对设备**；
3. 执行配对与连接命令：
   ```powershell
   adb pair <IP:配对端口> <6位配对码>
   adb connect <IP:主连接端口>
   ```
4. 无线推送安装：
   ```powershell
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

### 3. 日志抓取与排错规范（非阻塞 Dump）
**严禁执行无终止条件的常驻流命令**，必须遵循非阻塞原则：
- 抓取崩溃堆栈：
  ```powershell
  adb logcat -d -b crash
  ```
- 抓取指定包名的运行日志：
  ```powershell
  adb logcat -d --pid=$(adb shell pidof com.webdav.player)
  ```
- 检索关键过滤标签：
  ```powershell
  adb logcat -d -t 500 | Select-String "ExoPlayer|AudioTrack"
  ```

---

## 七、上下文卫生法则（Context Hygiene）

整个生命周期能够高产出、低返工的核心奥秘在于**对 LLM 上下文窗口的精准控制**：

```text
[不可打断连贯区]                     [完全隔离切片区]
/grill-with-docs                  /implement Issue 01  -->  /clear
      ↓                                  ↓
  /to-spec        ==== /clear ====>  /implement Issue 02  -->  /clear
      ↓                                  ↓
 /to-tickets                         /implement Issue 03  -->  /clear
(保持同一窗口，严防语义折损)          (每次重新加载独立工单，始终处于 Smart Zone)
```

1. **不可打断连贯区（保持单会话）**：
   - 从需求研讨（`/grill-with-docs`）→ 规格生成（`/to-spec`）→ 工单拆分（`/to-tickets`），必须在**同一个会话窗口**中完成。
   - 避免中途执行 `/compact` 或 `/clear`，确保所有背景假设无损注入到工单中。
2. **完全隔离切片区（每次做完必 `/clear`）**：
   - 拆解出的工单由 `to-tickets` 或 `/triage` 保证了自包含性。
   - **完成工单提交后，立即输入 `/clear`**，让模型始终在最敏锐的“智能区（Smart Zone，通常是上下文前 150k token）”内写代码。

---

## 八、Spec 验收与交付检查清单（Checklist）

在将一个特性的 `spec.md` 标记为 `Status: completed` 之前，请逐一核对以下 5 项指标：

- [ ] **1. 工单全部勾选**：所有子工单（`issues/*.md`）中的验收项已全部勾选，状态均为 `resolved` 或 `completed`。
- [ ] **2. 回归测试全绿**：在终端运行全套测试命令（如 `.\gradlew.bat testDebugUnitTest`），确保通过率为 100%。
- [ ] **3. 双轴审查无阻断**：运行 `/code-review` 审查分支差异，确认无违反项目规范（Standards 轴），且完整满足 User Stories（Spec 轴）。
- [ ] **4. 真实环境冒烟通过**：打包并在设备上实测走通一次核心用户旅程（音频正常发声、后台播放稳定）。
- [ ] **5. 冒烟缺陷闭环**：若在第 4 步冒烟中发现 Bug，已通过 `/diagnosing-bugs` 修复，或通过 `/triage` 建立后续工单闭环推进。

---
*文档更新日期：2026-09-26*
