# Cloudfin 🌙

**模块化的跨平台通信与同步框架**

---

## 什么是 Cloudfin

Cloudfin 是一个由三层独立组件构成的模块化框架：

```
┌──────────────────────────────────────────────────┐
│                    UI 层（平台原生）                  │
│   Android → Kotlin + Jetpack Compose             │
│   iOS     → Swift + SwiftUI                     │
│   Windows → C# + WinUI3 或 C++ + Qt             │
│   macOS   → Swift + SwiftUI                     │
│   Linux   → Rust(QML) / C++ + GTK               │
└──────────────────────┬───────────────────────────┘
                       │  WebSocket + JSON（本地通信）
                      ┌┴──────────────────────────┐
                      │      Cloudfin Core（Rust）   │
                      │  无头守护进程，无 UI         │
                      │  模块加载 + 配置 + 路由      │
                      └┬──────────────────────────┘
                       │
             ┌─────────┼──────────┬────────────┐
             │         │          │            │
        ┌────▼──┐ ┌───▼───┐ ┌───▼────┐ ┌───▼───┐
        │  P2P   │ │  Tor   │ │   I2P   │ │ Crypto │
        │(网络)  │ │(匿名)  │ │ (隧道)  │ │(加密)  │
        └────────┘ └────────┘ └─────────┘ └────────┘
                              ┌───▼───┐
                           │  CRDT  │
                           │(同步)  │
                           └────────┘
```

### 核心定位

Cloudfin Core = **无头守护进程框架**，类比 Redis / cURL / 无头浏览器的设计理念——无 UI，纯 API/CLI，程序化控制。

### 模块化

每个功能都是一个独立的 Rust 模块（`.so`/`.dylib`），可按需安装、加载、卸载。

---

## 核心特性

| 特性 | 说明 |
|------|------|
| **跨平台** | Android / iOS / Windows / macOS / Linux |
| **模块化** | 核心与功能分离，按需安装模块 |
| **无头架构** | Core 无 UI，通过 WebSocket API 控制 |
| **本地通信** | UI 与 Core 同一设备，WebSocket + JSON |
| **Open Core** | 开源基础版 + 商业高级模块 |
| **端到端加密** | Crypto 模块提供可配置的多层加密管道 |
| **文档审查** | 层级审查机制，确保文档一致性 |

---

## 开源协议

| 组件 | 许可证 | 说明 |
|------|--------|------|
| **Core** | AGPL-3.0 | 开源免费用；修改后必须开源 |
| **P2P 模块** | AGPL-3.0 | 开源免费用；修改后必须开源 |
| **CRDT 模块** | AGPL-3.0 | 开源免费用；修改后必须开源 |
| **Crypto 模块** | 商业授权 | 需付费使用或授权 |
| **Tor 模块** | AGPL-3.0 | 基于 Arti（Apache-2.0）开发；修改后必须开源 |
| **I2P 模块** | 待定 | 基于废弃 MIT 库开发；专有协议可行性研究中 |
| **UI 层** | AGPL-3.0 | 开源免费用；修改后必须开源 |

### AGPL-3.0 说明

- ✅ 免费使用
- ✅ 源码可见
- ✅ 允许修改
- ⚠️ 修改后必须开源
- ✅ 允许商用

### Crypto 商业授权

- 💰 需购买授权才能使用
- 可选：一次性买断 / 定期订阅 / 收益分成
- 商业企业使用必须购买授权

---

## 模块间通信

**原则**：模块之间不能直接通信，必须通过 Core。

```
P2P 模块 ──▶ Core ──▶ modules.call ──▶ Tor 模块
                │                              │
                ◀────────── result ────────────┘
```

所有模块通过 Core 的 `modules.call` 接口交互，返回结果也通过 Core 传回。

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

详细流程见 [Drafts/SPEC-Install-Flow.md](Drafts/SPEC-Install-Flow.md)

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
  🔍 审查逻辑（层级审查）
     ↓ 有问题？→ 修改 → 再次审查
     ↓ 无问题
  ✅ 逻辑锁定 → 实现 → 代码审查
```

---

## 文档体系

```
Cloudfin/Drafts/
├── SPEC.md                      ← 总架构文档（宪法级）
├── SPEC-Review.md               ← 文档审查机制
├── SPEC-Core.md                 ← Core 核心规格
├── SPEC-Core-API.md             ← WebSocket API 规格
├── SPEC-Plugin-Interface.md     ← 模块插件接口规格
├── MOD-p2p.md                   ← P2P 模块规格
├── MOD-tor.md                   ← Tor 模块规格
├── MOD-i2p.md                  ← I2P 模块规格
├── MOD-crdt.md                  ← CRDT 模块规格
├── MOD-crypto.md                ← Crypto 模块规格
└── SPEC-Install-Flow.md         ← 安装引导流程规格
```

**审查层级**：
- L0（宪法）：SPEC.md、SPEC-Review.md
- L1（法律）：SPEC-Core.md、SPEC-Core-API.md、SPEC-Plugin-Interface.md
- L2（法规）：MOD-*.md
- L3（地方法规）：SPEC-Install-Flow.md、SPEC-UI-*.md

详见 [Drafts/SPEC-Review.md](Drafts/SPEC-Review.md)

---

## 状态

**当前状态**：架构设计阶段（v0.1.0-draft）

所有 SPEC 文档均已审查确认，可进入实现阶段。

---

## 参与贡献

欢迎参与讨论和技术设计。提交前请阅读 [Drafts/SPEC-Review.md](Drafts/SPEC-Review.md) 了解审查机制。

---

*Cloudfin - 模块化通信与同步框架 🌙*
