# Cloudfin 🌙

**模块化跨平台通信与同步框架**

---

## 仓库结构

Cloudfin 项目分为三个独立仓库：

| 仓库 | 内容 |
|------|------|
| [Cloudfin](https://github.com/Indivisible2025/Cloudfin) | **本仓库**，技术文档 |
| [Cloudfin-Core](https://github.com/Indivisible2025/Cloudfin-Core) | 核心守护进程 + Android Service |
| [Cloudfin-UI](https://github.com/Indivisible2025/Cloudfin-UI) | 跨平台桌面/移动 UI |
| [Cloudfin-Modules](https://github.com/Indivisible2025/Cloudfin-Modules) | P2P / CRDT 插件模块 |

---

## 文档体系

```
Cloudfin/
├── SPEC-Design.md        ← L0 纲领级（设计总纲）
├── SPEC-Tech-Core.md     ← L1 路线级（Core 技术选型）
├── SPEC-Tech-UI.md      ← L2 路线级（UI 技术选型）
├── SPEC-Tech-Modules.md  ← L2 路线级（Modules 技术选型）
├── SPEC-Impl-Core.md    ← L3 实现级（Core 最终决策）
├── SPEC-Impl-UI.md     ← L4 实现级（UI 最终决策，待编写）
├── SPEC-Impl-Modules.md ← L4 实现级（Modules 最终决策，待编写）
├── README.md             ← 本文件
└── Cloudfin-Icon.png    ← 项目图标
```

---

## 核心架构（五层）

```
┌──────────────────────────────────────────────────────┐
│  第一层：UI 层（平台原生，用户体验优先）                   │
│  Android / iOS / Windows / macOS / Linux             │
└──────────────────────────┬───────────────────────────┘
                           │ WebSocket
┌──────────────────────────▼───────────────────────────┐
│  第二层：Core 层（Rust，无 UI，纯逻辑）                   │
│  连接管理 / 路由 / 模块调度 / 配置                       │
└──────────────────────────┬───────────────────────────┘
                           │ Core API
┌──────────────────────────▼───────────────────────────┐
│  第三层：通信层 Modules（地基/桥梁）                      │
│  p2p / tor / i2p                                   │
└──────────────────────────────────────────────────────┘
                           ↑
┌──────────────────────────▼───────────────────────────┐
│  第四层：加密层 Modules（装修/安全设施）                  │
│  crypto                                            │
└──────────────────────────────────────────────────────┘
                           ↑
┌──────────────────────────▼───────────────────────────┐
│  第五层：同步层 Modules（人工作/车通行）                  │
│  crdt / storage                                    │
└──────────────────────────────────────────────────────┘
```

三层 Modules 都只和 Core 通信，彼此不直接通信。

---

## 开发方法论

任何子项目都必须遵循：

```
需求定义 → L0 设计 → L1/L2 技术选型 → 🔍审查 → ✅锁定 → L3/L4 实现 → 代码审查
```

---

*Cloudfin - 模块化通信与同步框架 🌙*
