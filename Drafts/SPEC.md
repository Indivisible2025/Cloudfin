# Cloudfin 技术规格文档

> 版本：v0.2.0-draft  
> 日期：2026-04-03  
> 状态：**草案 — 待审查**

---

## 1. 项目概述

### 1.1 什么是 Cloudfin

Cloudfin 是一个**模块化的跨平台通信与同步框架**，由三层独立组件构成：

```
┌──────────────────────────────────────────────────┐
│                    UI 层（平台原生）                  │
│   Android  → Kotlin + Jetpack Compose             │
│   iOS      → Swift + SwiftUI                   │
│   Windows  → C# + WinUI3  或  C++ + Qt        │
│   macOS    → Swift + SwiftUI                   │
│   Linux    → Rust(QML) / C++ + GTK           │
└──────────────────────┬───────────────────────────┘
                       │  HTTP / IPC（本地通信）
        ┌──────────────┼──────────────┐
        │              │              │
        ▼              ▼              ▼
┌──────────────────────────────────────────────┐
│              Cloudfin Core（Rust）             │
│   无头守护进程，无 UI，纯 API + CLI            │
│   动态加载 .so/.dylib 插件                    │
│   模块管理 + 配置存储 + 路由 + 会话管理        │
└──────────────────────┬───────────────────────┘
                        │
         ┌─────────────┼─────────────┐
         │             │             │
         ▼             ▼             ▼
    ┌─────────┐ ┌─────────┐ ┌─────────┐
    │ cloudfin │ │ cloudfin │ │ cloudfin │
    │ -p2p.so │ │ -tor.so │ │ -i2p.so │
    │  (Rust) │ │  (Rust) │ │  (Rust) │
    └─────────┘ └─────────┘ └─────────┘
```

### 1.2 核心定位

Cloudfin Core = **无头守护进程框架**，类比以下工具的设计理念：

| 类比工具 | 定位 | Cloudfin 对应 |
|---------|------|--------------|
| Redis | 键值存储服务器，客户端连接 | Cloudfin Core 作为通信/同步服务器 |
| cURL | 命令行 HTTP 工具 | Cloudfin Core 作为 CLI 可编程框架 |
| 无头浏览器 | 无 GUI，程序化控制 | Cloudfin Core 无 UI，纯 API/CLI |
| mcp (Model Context Protocol) | AI工具调用协议 | Cloudfin Core 作为模块化通信框架 |

### 1.3 技术选型决策

**为什么全部使用 Rust？**

```
语言统一：Core + 所有模块 = Rust
插件形式：.so/.dylib 动态库（不是独立进程）
```

| 决策 | 理由 |
|------|------|
| **全 Rust** | 现有代码复用（Axum + Tokio 架构正确），性能最优，崩溃隔离好，技能匹配 |
| **动态库插件** | 相比独立进程方案：内存开销降低 10x，无进程间通信延迟，真正的插件系统 |
| **不混合语言** | Rust 可搞定所有层（网络/协议/加密/存储），避免 FFI 复杂度和运行时开销 |

**为什么不用独立进程方案（原方案）？**
- 原方案每个模块是独立 Axum HTTP 服务器 + 完整 Tokio 运行时
- 每个模块内存开销 ~5-10MB（Tokio 运行时）
- 4个模块 = 4个独立进程 = ~20-40MB 额外开销
- 动态库插件：单进程 + 4个 .so = 零额外进程开销

---

## 2. 安装流程

### 2.1 安装顺序

```
第一步：安装 UI
         ↓
第二步：UI 引导安装 Core
         ↓
第三步：UI 引导安装模块（模块市场）
         ↓
第四步：开始使用
```

### 2.2 各平台分发形式

| 平台 | UI 分发 | Core 分发 | 模块分发 |
|------|---------|----------|---------|
| **Android** | APK | 随 APK 内置（ARM64）| 随 APK 内置基础模块 |
| **iOS** | IPA | 随 IPA 内置 | 随 IPA 内置基础模块 |
| **Windows** | MSI/EXE | 安装时下载 | 模块市场下载 |
| **macOS** | DMG | 随 DMG 内置 | 模块市场下载 |
| **Linux** | DEB/RPM/AppImage | 安装时下载 | 模块市场下载 |

### 2.3 安装引导流程（详见 SPEC-Install-Flow.md）

---

## 3. Core 层规格

### 3.1 运行模式

- **独立守护进程**：后台长期运行
- **本地通信**：UI 与 Core 在同一设备，通过 HTTP 通信
- **无远程模式**：Core 不提供公网访问接口

### 3.2 Core API 协议

**协议**：HTTP + JSON（REST 风格）

**理由**：
- 开发/调试最简单
- 任何平台语言都有成熟 HTTP 库
- 未来扩展远程控制最容易

### 3.3 模块系统

```
一个模块 = 一个 Rust 编译的动态库（.so / .dylib / .dll）
一个模块 = 一套独立的网络或存储功能
模块可动态加载/卸载，无需重启 Core
```

**模块加载机制**：`libloading`（Rust 原生跨平台动态库加载）

```rust
// Core 加载模块示例
use libloading::{Library, Symbol};

pub struct ModuleHandle {
    lib: Library,
}

impl ModuleHandle {
    pub fn load(path: &Path) -> Result<Self> {
        let lib = Library::new(path)?;
        Ok(Self { lib })
    }

    pub fn get_fn<T: FromNullPtr>(&self, name: &str) -> Result<Symbol<T>> {
        unsafe { self.lib.get(name.as_bytes()) }
            .map(|s| Symbol::new(s))
    }
}
```

### 3.4 内置模块目录结构

```
modules/
├── cloudfin-p2p.so      （P2P 网络发现+连接，Rust）
├── cloudfin-tor.so       （Tor 协议封装，Rust - 基于 arti）
├── cloudfin-i2p.so       （I2P 协议封装，Rust - 基于 i2p crate）
├── cloudfin-crdt.so     （CRDT 同步引擎，Rust - 基于 crdts）
├── cloudfin-crypto.so   （端到端加密/密钥管理，Rust）
└── cloudfin-storage.so   （本地数据持久化，Rust）
```

---

## 4. 模块规格

### 4.1 模块列表

| 模块 | 功能 | 依赖 | 许可证（规划）|
|------|------|------|-------------|
| **p2p** | P2P 节点发现与连接 | libp2p | GPL v3 |
| **tor** | Tor 协议封装 | arti | AGPL v3 |
| **i2p** | I2P 协议封装 | i2p crate | AGPL v3 |
| **crdt** | CRDT 同步引擎 | crdts | 商业授权 |
| **crypto** | 端到端加密、密钥管理 | ring / zeroize | Apache 2.0 |
| **storage** | 本地数据持久化 | sled / rocksdb | Apache 2.0 |

（详见各模块规格文档 MOD-*.md）

---

## 5. 交互逻辑审查流程

### 5.1 开发方法论

**任何项目或子项目都必须遵循**：

```
需求定义
   ↓
技术文档编写（SPEC.md）
   ↓
交互逻辑设计（所有接口定义）
   ↓
🔍 审查逻辑（开发者/团队确认）
   ↓ 有问题？
  是 → 修改文档 → 再次审查
   ↓ 无问题
✅ 逻辑锁定
   ↓
实现
   ↓
代码审查（对照SPEC）
```

### 5.2 每份 SPEC.md 必须包含的内容

```
## 概述
   本模块/组件是什么，定位

## 接口定义（最核心）
   - 所有输入/输出的格式
   - HTTP API 路由（方法+路径+请求体+响应体）
   - 插件接口函数定义（Module Trait）
   - 错误码定义

## 状态机
   - 本模块有哪些状态
   - 状态之间如何转换
   - 触发条件

## 交互流程（时序）
   - 典型使用场景的调用顺序
   - 并发情况处理

## 边界情况和错误处理
   - 异常场景
   - 超时/重试/降级

## 与其他组件的关系
   - 依赖哪些接口
   - 被哪些组件调用
```

---

## 6. 文档体系结构

```
Cloudfin/
├── SPEC.md                      ← 总架构文档（本文）
│
├── SPEC-Core.md                 ← Core 核心规格
│   ├── Core-API.md              ← Core HTTP API 规格
│   ├── Core-Plugin-Interface.md ← 模块插件接口规格
│   └── Core-Config.md            ← 配置存储格式
│
├── SPEC-Modules.md               ← 模块通用规格
│   ├── MOD-p2p.md               ← P2P 模块规格（基于 libp2p）
│   ├── MOD-tor.md               ← Tor 模块规格（基于 arti）
│   ├── MOD-i2p.md               ← I2P 模块规格（基于 i2p crate）
│   └── MOD-crdt.md             ← CRDT 模块规格
│
├── SPEC-UI-Android.md           ← Android UI 规格
├── SPEC-UI-iOS.md              ← iOS UI 规格
├── SPEC-UI-Desktop.md          ← 桌面端 UI 规格
│
└── SPEC-Install-Flow.md        ← 安装引导流程规格
```

---

## 7. 开源协议规划

| 组件 | 许可证 | 说明 |
|------|--------|------|
| Core | Apache 2.0 | 允许商业使用，吸引贡献 |
| 基础模块（p2p/tor/i2p）| GPL v3 / AGPL v3 | 确保衍生项目开源 |
| 高级模块（crdt/商业功能）| 商业授权 | Open Core 商业模式 |
| UI 层 | MIT | 基础版开源 |

---

## 8. 当前状态

| 项目 | 状态 |
|------|------|
| 架构定义 | ✅ 完成（v0.2.0）|
| 安装流程设计 | ✅ 完成 |
| 文档结构规划 | ✅ 完成 |
| SPEC-Core.md | 🔲 待编写 |
| SPEC-Core-API.md | 🔲 待编写 |
| SPEC-Core-Plugin-Interface.md | 🔲 待编写 |
| SPEC-Modules.md | 🔲 待编写 |
| 各模块规格 | 🔲 待编写 |
| 各平台 UI 规格 | 🔲 待编写 |
| 安装引导规格 | 🔲 待编写 |

---

## 9. 下一步

1. 审查本文档，确认架构和流程无误
2. 无问题 → 锁定总架构
3. 有问题 → 修改 → 再次审查
4. 锁定后开始编写 SPEC-Core.md

---

*本文档由 墨尘 🌙 和 Cloudfin 团队编写*
*最后更新：2026-04-03*
