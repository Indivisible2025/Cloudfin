# Cloudfin 🌙

**模块化跨平台通信与同步框架**

---

## 仓库结构

Cloudfin 项目分为三个独立仓库：

| 仓库 | 内容 |
|------|------|
| [Cloudfin-Core](https://github.com/Indivisible2025/Cloudfin-Core) | 核心守护进程 + Android Service |
| [Cloudfin-UI](https://github.com/Indivisible2025/Cloudfin-UI) | 跨平台桌面/移动 UI |
| [Cloudfin-Modules](https://github.com/Indivisible2025/Cloudfin-Modules) | P2P / CRDT 插件模块 |
| **本仓库** | 技术文档与架构规格 |

---

## 技术规格文档

```
Cloudfin/
├── SPEC.md                        ← 总架构文档（宪法级）
├── README.md                      ← 本文件
└── Cloudfin-Icon.png              ← 项目图标
```

详细规格文档位于各子仓库的 `docs/` 目录中。

---

## 核心架构

```
┌──────────────────────────────────────────────────┐
│                    UI 层（平台原生）                  │
│   Android → Kotlin + Jetpack Compose             │
│   iOS     → Swift + SwiftUI                     │
│   Windows → C# + WinUI3 / C++ + Qt             │
│   macOS   → Swift + SwiftUI                     │
│   Linux   → Rust(QML) / C++ + GTK             │
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
        └────────┘ └────────┘ └─────────┘ └────────┘
                              ┌───▼───┐
                           │  CRDT  │
                           └────────┘
```

### 核心定位

Cloudfin Core = **无头守护进程框架**，类比 Redis / cURL / 无头浏览器的设计理念——无 UI，纯 API/CLI，程序化控制。

---

## 开源协议

| 组件 | 许可证 | 说明 |
|------|--------|------|
| **Core** | AGPL-3.0 | 开源免费用；修改后必须开源 |
| **P2P / CRDT 模块** | AGPL-3.0 | 开源免费用；修改后必须开源 |
| **Crypto 模块** | 商业授权 | 需付费使用或授权 |
| **UI 层** | MIT | 开源免费用 |

---

## 开发方法论

任何子项目都必须遵循：

```
需求定义 → 技术文档（SPEC.md）→ 交互逻辑设计 → 🔍审查 → ✅锁定 → 实现 → 代码审查
```

---

*Cloudfin - 模块化通信与同步框架 🌙*
