# Cloudfin 🌙

**模块化的跨平台通信与同步框架**

---

## 什么是 Cloudfin

Cloudfin 是一个由三层独立组件构成的模块化框架：

```
┌──────────────────────────────────────────────┐
│              UI 层（平台原生）                  │
│   Android → Kotlin / Jetpack Compose          │
│   iOS     → Swift / SwiftUI                 │
│   Windows → C# / WinUI3 或 C++ / Qt        │
│   macOS   → Swift / SwiftUI                 │
│   Linux   → Rust(QML) / C++ / GTK           │
└──────────────────┬─────────────────────────┘
                    │  HTTP / IPC（本地通信）
                   ┌┴──────────────────────────┐
                   │      Cloudfin Core（Rust）  │
                   │  无头守护进程，无 UI         │
                   │  模块加载 + 配置 + 路由      │
                   └┬──────────────────────────┘
                    │
          ┌─────────┼──────────┐
          │         │          │
     ┌────▼──┐ ┌───▼───┐ ┌───▼───┐
     │  P2P   │ │ Tor/I2P │ │  CRDT  │
     │(网络)  │ │(协议)  │ │(同步)  │
     └────────┘ └────────┘ └────────┘
```

### 核心定位

Cloudfin Core = **无头守护进程框架**，类比 Redis / cURL / 无头浏览器的设计理念——无 UI，纯 API/CLI，程序化控制。

### 模块化

每个功能（P2P 网络、Tor/I2P 协议、CRDT 同步等）都是一个独立的 Rust 模块（.so/.dylib），可按需安装、加载、卸载。

---

## 核心特性

| 特性 | 说明 |
|------|------|
| **跨平台** | Android / iOS / Windows / macOS / Linux |
| **模块化** | 核心与功能分离，按需安装模块 |
| **无头架构** | Core 无 UI，通过 HTTP API 控制 |
| **本地通信** | UI 与 Core 同一设备，HTTP/IPC |
| **Open Core** | 开源基础版 + 商业高级模块 |

---

## 安装流程

```
第一步：安装 UI
         ↓
第二步：UI 引导安装 Core
         ↓
第三步：UI 引导安装模块（模块市场）
         ↓
第四步：开始使用
```

---

## 开发方法论

Cloudfin 采用**文档优先**的开发流程：

```
任何子项目都必须遵循：

  需求定义
     ↓
  技术文档编写（SPEC.md）
     ↓
  交互逻辑设计
     ↓
  🔍 审查逻辑
     ↓ 有问题？→ 修改 → 再次审查
     ↓ 无问题
  ✅ 逻辑锁定 → 实现 → 代码审查
```

详见 [Drafts/SPEC.md](Drafts/SPEC.md)

---

## 文档体系

```
Drafts/
└── SPEC.md                 ← 总架构文档（草案）
    ├── SPEC-Core.md         ← Core 核心规格
    ├── Core-API.md         ← Core HTTP API 规格
    ├── Plugin-Interface.md  ← 模块插件接口规格
    ├── MOD-*.md            ← 各模块规格
    └── SPEC-Install-Flow.md ← 安装引导规格
```

---

## 开源协议

| 组件 | 许可证 |
|------|--------|
| Core | Apache 2.0 |
| 基础模块（p2p/tor/i2p）| GPL v3 / AGPL v3 |
| 高级模块 | 商业授权 |
| UI 层 | MIT |

---

## 状态

**当前状态**：架构设计阶段（v0.1.0-draft）

所有 SPEC 文档均需审查确认后方可进入实现阶段。

---

## 参与贡献

本项目目前处于架构定义阶段，欢迎参与讨论和技术设计。

---

*Cloudfin - 模块化通信与同步框架 🌙*
