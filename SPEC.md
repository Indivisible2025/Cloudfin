# Cloudfin 架构规格

> 版本：v1.0  
> 日期：2026-04-04  
> 状态：**已锁定**  
> 级别：L0（宪法级）

---

## 1. 项目定位

Cloudfin 是**模块化的跨平台通信与同步框架**，由三层独立组件构成：

```
┌──────────────────────────────────────────────────┐
│                    UI 层（平台原生）                  │
│   Android → Kotlin + Jetpack Compose             │
│   iOS     → Swift + SwiftUI                     │
│   Windows → C# + WinUI3 或 C++ + Qt             │
│   macOS   → Swift + SwiftUI                     │
│   Linux   → Rust(QML) / C++ + GTK              │
└──────────────────────┬───────────────────────────┘
                       │  WebSocket + JSON（:19001）
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

**Cloudfin Core = 无头守护进程框架**，类比 Redis / cURL / 无头浏览器的设计理念——无 UI，纯 API/CLI，程序化控制。

---

## 2. 核心设计决策

### 2.1 全 Rust + 动态库插件

- **Core**：Rust，无 UI，纯守护进程
- **Modules**：各自编译为独立 `.so`（Linux）/ `.dylib`（macOS）/ `.dll`（Windows）
- **插件加载**：`libloading` crate，运行时动态加载
- **不使用 WASM**：性能优先

### 2.2 三层完全解耦

```
UI层：独立进程，平台原生 UI
Core层：跨平台通用守护进程，无 UI，纯 API + CLI
Modules层：动态可插拔模块（.so/.dylib），一个模块 = 一套 API 路由
```

**安装流程**：
```
第一步：安装 UI
         ↓
第二步：UI 引导安装 Core（检测未安装 → 提示下载）
         ↓
第三步：UI 引导安装模块（模块市场，选择需要的模块安装）
         ↓
第四步：开始使用
```

### 2.3 UI ↔ Core：纯 WebSocket

UI 与 Core 之间**只走一条 WebSocket 连接**，不复用 HTTP：

```
UI ⟷ WebSocket (:19001/ws) ⟷ Core
  - 请求：{ type: "request", action, request_id, data }
  - 响应：{ type: "response", request_id, data }
  - 推送：{ type: "status_update", data } / { type: "module_status_changed", ... }
```

- **单一连接**：OkHttp WebSocket（Android），`readTimeout=0`（长连接）
- **请求响应**：通过 `request_id` 匹配，`CompletableFuture` + 5 秒超时
- **实时推送**：Server → Client 通过 SharedFlow（Android）/ `@Published`（iOS）
- **连接状态**：StateFlow<ConnectionState>

详见：[cloudfin-core/docs/Core-API.md](cloudfin-core/docs/Core-API.md)

### 2.4 模块间通信：必须通过 Core

```
P2P 模块 ──▶ Core ──▶ modules.call ──▶ TOR 模块
                │
                ◀────────── result ────────────┘
```

模块之间**不能直接通信**，所有交互必须经过 Core 的 `modules.call` 接口。

### 2.5 Open Core 商业模式

| 组件 | 许可证 |
|------|--------|
| Core | Apache 2.0 |
| P2P 模块 | AGPL-3.0 |
| TOR 模块 | AGPL-3.0（基于 Arti） |
| I2P 模块 | **待定** |
| CRDT 模块 | AGPL-3.0 |
| Crypto 模块 | **商业授权** |
| UI 层 | AGPL-3.0 |

---

## 3. 模块系统

### 3.1 Module Trait

所有 Cloudfin 模块必须实现 `Module` trait（定义于 `cloudfin-mod/common`）：

```rust
pub trait Module: Send + Sync {
    fn id(&self) -> &str;
    fn name(&self) -> &str;
    fn version(&self) -> &str;
    fn init(&mut self, config: Value) -> anyhow::Result<()>;
    fn start(&mut self) -> anyhow::Result<()>;
    fn stop(&mut self) -> anyhow::Result<()>;
    fn status(&self) -> ModuleHealth;
}
```

### 3.2 Module State

```
Uninitialized → Initialized → Running → Stopped
                    ↓
                  Error
```

### 3.3 模块分发格式

模块以 ZIP 包形式发布，ZIP 文件名格式：
`CloudFin-Mod-{平台}-{模块名}-{版本}.zip`

解压后放入 `{Core根目录}/Cloudfin/modules/` 目录：

```
CloudFin-Mod-{平台}-{模块名}-{版本}.zip
├── CloudFin-Mod-{平台}-{模块名}-{版本}.so
└── CloudFin-Mod-{平台}-{模块名}-{版本}.so.json
```

平台值：`Linux`、`Android`、`Windows`、`macOS`、`iOS`

Linux 与 Android 共用同一份二进制时，平台字段写作 `Linux-Android`。

示例：
```
CloudFin-Mod-Linux-Android-P2P-2026-04-04-1.zip
├── CloudFin-Mod-Linux-Android-P2P-2026-04-04-1.so
└── CloudFin-Mod-Linux-Android-P2P-2026-04-04-1.so.json
```

### 3.4 Module Manifest

每个模块必须包含 `manifest.json`：

```json
{
  "id": "cloudfin.p2p",
  "name": "P2P Networking",
  "version": "0.1.0",
  "description": "P2P networking module using libp2p v0.54",
  "author": "Cloudfin Team",
  "platform": ["android", "linux"],
  "permissions": ["Network"]
}
```

**platform 字段说明：**

| 值 | 适用平台 |
|----|---------|
| `["android"]` | Android |
| `["linux"]` | Linux |
| `["android", "linux"]` | Android 与 Linux 共用同一二进制 |
| `["windows"]` | Windows |
| `["macos"]` | macOS |
| `["ios"]` | iOS |
| `["macos", "ios"]` | macOS 与 iOS 共用同一二进制 |

### 3.5 模块 UI 声明

模块通过 `manifest.json` 向 Core 声明自己的 UI 结构和功能，Core 透传给 UI，UI 动态渲染。

**结构：**

```json
{
  "id": "cloudfin.p2p",
  "name": "P2P Networking",
  "version": "0.1.0",
  "category": "Network",
  "icon": "🌐",
  "description": "P2P 网络连接管理",
  "cards": {
    "info": [
      {
        "title": "连接状态",
        "items": [
          { "key": "peers", "label": "已连接节点", "style": "text" },
          { "key": "addr", "label": "监听地址", "style": "text" }
        ]
      }
    ],
    "actions": [
      {
        "title": "网络操作",
        "items": [
          { "key": "dial", "label": "连接节点", "action": "dial_peer" },
          { "key": "disconnect", "label": "断开连接", "action": "disconnect_peer" }
        ]
      }
    ],
    "settings": [
      {
        "title": "连接设置",
        "items": [
          { "key": "listen_port", "label": "监听端口", "type": "number", "default": 9000 },
          { "key": "enable_relay", "label": "启用中继", "type": "toggle", "default": true }
        ]
      }
    ]
  }
}
```

**字段说明：**

| 字段 | 含义 | 示例 |
|------|------|------|
| `id` | 模块唯一标识 | `cloudfin.p2p` |
| `name` | 显示名称 | `P2P Networking` |
| `version` | 版本 | `0.1.0` |
| `category` | 分类（决定 UI 归属哪一类卡片） | `Network` |
| `icon` | emoji 图标 | `🌐` |
| `description` | 模块描述 | `P2P 网络连接管理` |
| `cards.info` | 信息展示卡片（只读） | 状态、数字等 |
| `cards.actions` | 操作按钮卡片 | 点击触发 Core 调用 |
| `cards.settings` | 可配置项卡片 | 端口、开关等 |

**卡片 items 通用字段：**

| 字段 | 含义 |
|------|------|
| `key` | 字段标识，Core 取值/设值用 |
| `label` | UI 显示的名称 |
| `action` | 操作标识（仅 actions 用） |

**info item 的 `style`：**

| 值 | 含义 |
|------|------|
| `text` | 普通文本 |
| `number` | 数字 |
| `duration` | 时长（秒） |
| `status` | 状态色（green/yellow/red） |
| `progress` | 进度条（0-100） |

**settings item 的 `type`：**

| 值 | 含义 | 附加字段 |
|------|------|----------|
| `text` | 文本输入 | — |
| `number` | 数字输入 | `min`, `max` |
| `toggle` | 开关 | — |
| `select` | 下拉选择 | `options: []` |
| `password` | 密码输入 | — |

### 3.6 模块列表

| ID | 名称 | 技术栈 | 状态 |
|----|------|--------|------|
| `p2p` | P2P 网络 | libp2p v0.54 | 已实现 |
| `crdt` | 文档同步 | yrs 0.22 | 已实现 |
| `tor` | TOR 匿名网络 | arti | 待实现 |
| `i2p` | I2P 隧道 | 待定 | 待实现 |
| `crypto` | 加密管道 | 待定 | 商业授权 |
| `storage` | 存储管理 | 待定 | 待实现 |

---

## 4. 技术栈

### 4.1 Core

| 组件 | 技术 |
|------|------|
| 语言 | Rust 1.94.1 |
| HTTP Server | Axum 0.7 |
| WebSocket | axum::extract::ws |
| Async Runtime | Tokio |
| Dynamic Loading | libloading |
| Serialization | serde + serde_json |
| Logging | tracing + tracing-subscriber |
| Broadcast | tokio::sync::broadcast |

### 4.2 Modules

| 模块 | 技术 |
|------|------|
| p2p | libp2p v0.54 |
| crdt | yrs 0.22 |
| tor | arti |
| i2p | **待定** |

### 4.3 UI

| 平台 | 技术 |
|------|------|
| Android | Kotlin + Jetpack Compose |
| iOS | Swift + SwiftUI |
| Windows | C# + WinUI3 或 C++ + Qt |
| macOS | Swift + SwiftUI |
| Linux | Rust(QML) / C++ + GTK |

### 4.4 Android UI 通信

| 组件 | 技术 |
|------|------|
| HTTP Client | OkHttp WebSocket |
| Coroutines | Kotlin Coroutines |
| Reactive | StateFlow + SharedFlow |
| JSON | org.json.JSONObject |

---

## 5. 目录结构

```
Cloudfin/
├── SPEC.md                        ← 本文件（宪法级）
├── README.md                      ← 项目总览
│
├── cloudfin-core/                 ← Core 守护进程
│   ├── docs/
│   │   ├── SPEC-Core.md           ← Core 技术规格（L1）
│   │   ├── Core-API.md            ← WebSocket API 规格（L1）
│   │   └── Taichi-Plan-Cloudfin-Core.md
│   ├── core/                      ← Core crate
│   │   ├── src/main.rs            ← 入口 + HTTP + WebSocket server
│   │   ├── src/lib.rs
│   │   └── Cargo.toml
│   └── Cargo.toml                 ← workspace member
│
├── cloudfin-mod/                   ← 模块插件
│   ├── common/                    ← Module trait + manifest
│   │   ├── src/lib.rs
│   │   └── Cargo.toml
│   ├── p2p/                       ← P2P 模块
│   │   └── README.md
│   └── crdt/                      ← CRDT 模块
│       └── README.md
│
└── cloudfin-ui/                    ← UI 应用
    ├── docs/
    │   ├── SPEC-UI-Mobile.md       ← 移动端 UI 规格（L3）
    │   └── SPEC-UI-Desktop.md      ← 桌面端 UI 规格（L3）
    └── Mobile/
        └── android/
            └── CloudfinApp/      ← Android 原生应用
```

---

## 6. 开发方法论

**强制流程**（任何子项目都适用）：

```
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

**无审查通过的 SPEC.md 不得实现任何代码。**

### 审查层级

| 级别 | 文档 | 说明 |
|------|------|------|
| L0（宪法） | SPEC.md | 总架构，所有其他文档的根源 |
| L1（法律） | SPEC-Core.md, Core-API.md, SPEC-Plugin-Interface.md | Core 和模块接口 |
| L2（法规） | MOD-*.md | 各模块规格 |
| L3（地方法规） | SPEC-UI-*.md, SPEC-Install-Flow.md | UI 和安装流程 |

---

## 7. 文档体系

```
SPEC.md                          ← 宪法级总架构（本文）
├── cloudfin-core/docs/
│   ├── SPEC-Core.md            ← Core 技术规格
│   └── Core-API.md             ← WebSocket API 规格
├── cloudfin-mod/
│   ├── common/                  ← Module trait
│   ├── p2p/README.md           ← P2P 模块
│   └── crdt/README.md          ← CRDT 模块
└── cloudfin-ui/docs/
    ├── SPEC-UI-Mobile.md        ← 移动端 UI 规格
    └── SPEC-UI-Desktop.md      ← 桌面端 UI 规格
```

---

## 8. GitHub 仓库

**唯一仓库**：https://github.com/Indivisible2025/Cloudfin

- 分支策略：`main` 为唯一稳定分支
- 所有版本、代码、Rleases 均以 GitHub 为准
- Token：（已配置于本地 git credential store）

---

## 9. 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-04-04 | 重建 SPEC，锁定架构决策 |
| v0.1.0-draft | 2026-04-03 | 初始架构草案 |

---

*最后更新：2026-04-04*
