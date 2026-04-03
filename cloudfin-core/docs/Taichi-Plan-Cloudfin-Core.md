# Cloudfin Core 开发方案

> 太极架构集中式开发  
> 版本：v1.0  
> 日期：2026-04-03  
> 状态：**草案 — 待审查**

---

## 1. 架构概述

### 1.1 太极架构·集中式模式

```
                          ┌─────────────────────┐
                          │   皇帝 Emperor       │
                          │   年余（你）         │
                          │   统筹·决策·审查     │
                          └──────────┬──────────┘
                                     │ 下达任务
                    ┌────────────────┼────────────────┐
                    │                │                 │
             ┌──────▼──────┐ ┌──────▼──────┐ ┌───────▼──────┐
             │  尚书 Shangshu│ │ 中书 Zhongshu│ │  门下 Menxia │
             │   项目规划     │ │   架构设计    │ │   审查·质量   │
             │   任务分解     │ │   接口定义    │ │   验收·反馈   │
             └──────┬──────┘ └──────┬──────┘ └───────┬──────┘
                    │                │                 │
           ┌────────┴────────┐       │                 │
           │                   │       │                 │
    ┌──────▼──────┐ ┌───────▼────┐ ┌▼─────────────▼─┐
    │ Agent-1     │ │ Agent-2     │ │ Agent-N        │
    │ 执行：Core框架│ │ 执行：模块接口│ │ 执行：CI-CD/文档│
    └──────────────┘ └─────────────┘ └────────────────┘
```

### 1.2 子Agent职责划分

| Agent | 角色 | 负责内容 | 主要 Skill |
|-------|------|---------|-----------|
| **Agent-1** | Core框架 | 项目脚手架、模块加载、HTTP API、配置、日志 | rust, rust-patterns |
| **Agent-2** | 模块接口 | Module Trait、生命周期、Manifest、通信协议 | rust |
| **Agent-3** | CLI工具 | cloudfinctl 命令行、交互模式 | rust, bash |
| **Agent-4** | CI-CD | GitHub Actions、多平台构建 | github-all, bash |
| **Agent-5** | 文档 | SPEC-Core/API/Plugin/Config/Install-Flow | github-all |
| **Agent-6~N** | 模块实现 | p2p / tor / i2p / crdt / crypto / storage | rust |

---

## 2. 开发阶段

### 阶段一：设计（集中式）

```
尚书产出：任务分解表
  ├── A1: SPEC-Core.md 任务分解
  ├── A2: SPEC-Core-API.md 任务分解
  ├── A3: SPEC-Core-Plugin-Interface.md 任务分解
  └── A4: SPEC-Install-Flow.md 任务分解

中书产出：架构设计
  ├── Core 架构设计（模块加载/生命周期/API路由/配置）
  ├── HTTP API 路由定义
  ├── Plugin Interface（Module Trait）
  └── 安装引导流程

门下审查：
  └── 对以上内容提出问题，确认无问题后锁定
```

### 阶段二：并行实现

```
Agent-1（Core框架）
  ├── S1: 创建 Cargo workspace 项目
  ├── S2: 实现模块加载系统（libloading）
  ├── S3: 实现 HTTP API 服务器（Axum）
  ├── S4: 实现配置管理系统
  └── S5: 实现日志系统

Agent-2（模块接口）
  ├── S1: 定义 Module Trait
  ├── S2: 定义 ModuleManifest 格式
  ├── S3: 实现模块生命周期管理
  └── S4: 实现模块间通信协议

Agent-3（CLI工具）
  ├── S1: cloudfinctl 基本命令
  └── S2: 交互模式（REPL）

Agent-4（CI-CD）
  ├── S1: GitHub Actions 配置
  └── S2: 多平台交叉编译

Agent-5（文档）
  ├── S1: SPEC-Core.md
  ├── S2: Core-API.md
  ├── S3: Core-Plugin-Interface.md
  └── S4: Core-Config.md
```

### 阶段三：模块实现（接口锁定后）

```
Agent-6: cloudfin-p2p.so（基于 libp2p）
Agent-7: cloudfin-tor.so（基于 arti）
Agent-8: cloudfin-i2p.so（基于 i2p crate）
Agent-9: cloudfin-crdt.so（基于 crdts）
Agent-10: cloudfin-storage.so
```

---

## 3. 任务分解详细表

### Agent-1：Core 框架

```
任务编号：A1
输出：Cargo workspace + 可运行的 Core 二进制

S1: 项目脚手架
  ├── cloudfin-core/Cargo.toml（workspace 根）
  ├── core/Cargo.toml（主 crate）
  ├── plugins/common/Cargo.toml（共享接口）
  ├── 配置 rust-toolchain.toml
  └── 配置 .cargo/config.toml（交叉编译）

S2: 模块加载系统
  ├── lib.rs: PluginManager struct
  ├── lib.rs: load_module(path)
  ├── lib.rs: unload_module(id)
  ├── lib.rs: list_modules()
  └── 集成 libloading crate

S3: HTTP API 服务器
  ├── main.rs: Axum router
  ├── api/core.rs: /api/core/status
  ├── api/config.rs: GET/PATCH /api/config
  ├── api/modules.rs: CRUD /api/modules
  └── 集成 tower-http（tracing/logging）

S4: 配置管理系统
  ├── Config struct（JSON）
  ├── ConfigManager: load/save/validate
  └── 路径：各平台标准配置目录

S5: 日志系统
  ├── Tracing subscriber 配置
  ├── EnvFilter + fmt layer
  └── JSON 格式化（生产环境）
```

### Agent-2：模块接口

```
任务编号：A2
输出：Module Trait + 生命周期 + 通信协议

S1: Module Trait 定义
  pub trait Module {
      fn name(&self) -> &str
      fn version(&self) -> &str
      fn init(&mut self, config: Value) -> Result<()>
      fn start(&mut self) -> Result<()>
      fn stop(&mut self) -> Result<()>
      fn api_routes(&self) -> Vec<Route>
  }

S2: ModuleManifest 格式
  struct ModuleManifest {
      name: String,
      version: String,
      description: String,
      rust_version: String,
      dependencies: HashMap<String, Version>,
      permissions: Vec<Permission>,
      api_version: String,
  }

S3: 模块生命周期管理
  enum ModuleState { Loading, Loaded, Running, Stopped, Error }
  状态转换图
  超时和错误处理

S4: 模块间通信协议
  struct ModuleRequest { id, module, action, payload }
  struct ModuleResponse { id, status, payload }
  通信方式：mpsc channels
```

### Agent-3：CLI 工具

```
任务编号：A3
输出：cloudfinctl 命令行工具

S1: 基本命令
  cloudfinctl status
  cloudfinctl config get/set
  cloudfinctl module list/load/unload
  实现：clap 或 structopt

S2: 交互模式（REPL）
  REPL loop
  命令历史（rustyline）
  Tab 自动补全
  彩色输出
```

### Agent-4：CI-CD

```
任务编号：A4
输出：.github/workflows/

S1: CI 工作流
  ci.yml: cargo check/test/clippy/fmt
  Matrix: [linux, macos, windows]

S2: Release 构建
  release.yml: cross 工具链
  Linux: x86_64, ARM64 (musl)
  macOS: x86_64, ARM64
  Windows: x86_64 (MinGW)
  Android: NDK 交叉编译

S3: 发布流程
  GitHub Releases 自动创建
  构建产物上传
  SHA256 校验和
```

### Agent-5：文档

```
任务编号：A5
输出：SPEC-* 系列文档

S1: SPEC-Core.md
  Core 职责定义、架构图、技术选型、目录结构

S2: Core-API.md
  所有 HTTP API 路由、请求/响应格式、错误码、示例

S3: Core-Plugin-Interface.md
  Module Trait 完整定义、ModuleManifest 格式、生命周期状态机、权限系统

S4: Core-Config.md
  配置文件 JSON Schema、配置项说明、路径规范
```

---

## 4. 依赖关系图

```
阶段一（设计）
     │
     │ 尚书产出任务分解
     │ 中书产出架构设计
     │ 门下审查
     ▼
阶段二（并行实现）
     │
     ├── Agent-1 ──► S2(模块加载) ──► S3(HTTP API)
     │                      │
     │                      ▼
     │                 Agent-2 ◄──► S1(Module Trait)
     │                      │
     │                      ▼
     │                 S2(Manifest) ──► S3(生命周期)
     │
     ├── Agent-3 ◄── S3(Axum API 完成后)
     │
     ├── Agent-4 ──► S1(CI) ──► S2(Release)
     │
     └── Agent-5 ──► S1~S4(各SPEC文档)
                        │
                        │ 全部完成
                        ▼
阶段三（模块实现）
     ├── Agent-6: cloudfin-p2p.so
     ├── Agent-7: cloudfin-tor.so
     ├── Agent-8: cloudfin-i2p.so
     ├── Agent-9: cloudfin-crdt.so
     └── Agent-10: cloudfin-storage.so
```

---

## 5. GitHub 仓库结构（最终）

```
Cloudfin/
├── SPEC.md
├── README.md
├── Cargo.toml              ← Workspace 根
│
├── .github/
│   └── workflows/
│       ├── ci.yml
│       └── release.yml
│
├── core/
│   ├── Cargo.toml
│   └── src/
│       ├── main.rs
│       ├── lib.rs
│       └── api/
│           ├── mod.rs
│           ├── core.rs
│           ├── config.rs
│           └── modules.rs
│
├── plugins/
│   ├── common/
│   │   ├── Cargo.toml
│   │   └── src/
│   │       ├── lib.rs
│   │       ├── module.rs
│   │       └── manifest.rs
│   │
│   ├── p2p/
│   ├── tor/
│   ├── i2p/
│   ├── crdt/
│   ├── crypto/
│   └── storage/
│
├── cli/
│   ├── Cargo.toml
│   └── src/main.rs
│
└── docs/
    ├── SPEC-Core.md
    ├── Core-API.md
    ├── Core-Plugin-Interface.md
    └── Core-Config.md
```

---

## 6. 开始条件

**阶段二（并行实现）开始前，必须确认**：

```
□ SPEC.md（总架构）已审查并锁定
□ SPEC-Core.md 草案已产出
□ SPEC-Plugin-Interface.md 草案已产出
□ Core-API.md 草案已产出
□ 任务分解已确认
```

---

## 7. Agent 执行指令模板

```
你是一个 Rust 开发专家，负责 Cloudfin Core 框架实现。

当前任务：[具体任务]
前置条件：[依赖的任务是否完成]
输出路径：/home/nainyv/.openclaw/workspace/Cloudfin/
工作目录：Cloudfin/core/

主要依赖：rust + rust-patterns skill

请按以下步骤执行：
1. 读取 rust SKILL.md 中的相关章节
2. 读取 cloudfin rust-patterns references
3. 实现代码
4. 运行 cargo check 验证
5. 提交到 GitHub

遇到问题请汇报给皇帝（我）。
```

---

*本文档由 墨尘 🌙 整理*
*太极架构·集中式开发方案*
