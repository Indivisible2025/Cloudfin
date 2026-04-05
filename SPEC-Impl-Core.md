# Cloudfin 实现文档

**版本：** v1.0
**日期：** 2026-04-05
**状态：** Draft（待评审）
**级别：** L3（实现级）

> 本文档记录 Core 最终技术决策（殊途同归的"归"），是代码实现的直接依据。

---

| 版本 | 日期 | 作者 | 变更说明 |
|------|------|------|----------|
| v1.0 | 2026-04-05 | — | 初始版本 |

---

## 1. 决策记录

### 1.1 Core框架

- **选定**：Axum 0.7
- **理由**：`async/await` 原生支持、与 Tokio 深度集成、代码简洁、tower/WebSocket 中间件生态完善

### 1.2 动态加载

- **选定**：libloading
- **理由**：纯 Rust 实现、无外部 C 依赖、跨平台（Linux/Android/macOS/Windows/iOS）稳定、API 直观、支持符号延迟解析

### 1.3 通信协议

- **选定**：WebSocket + JSON
- **理由**：双平台通用（Android / iOS / Desktop）、调试友好、支持全双工持久连接、方案成熟

### 1.4 P2P库

- **选定**：libp2p v0.54
- **理由**：模块化设计、支持多种传输协议（TCP/QUIC/WebRTC）、社区活跃、文档完善、与 yrs 协同工作良好

### 1.5 CRDT库

- **选定**：yrs 0.22
- **理由**：Rust 原生实现、兼容 Yjs 协议、性能优秀、支持共享数据类型（Map/List/Text）、与 libp2p 结合使用案例丰富

---

## 2. Core HTTP/WebSocket API

### 2.1 HTTP端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查 |
| GET | `/api/v1/status` | 系统状态（返回 Core 版本、已加载模块列表、运行时间） |

### 2.2 WebSocket端点

- **路径**：`ws://127.0.0.1:19001/ws`
- **说明**：UI 与 Core 的唯一通信通道，所有业务逻辑均通过此连接进行

### 2.3 API Actions

所有请求均为 JSON-RPC 风格，采用 `type: "request"` / `type: "response"` / `type: "error"` / `type: "event"` 四种消息类型。

#### module.list — 列出所有已加载模块

```json
{
  "type": "request",
  "action": "module.list",
  "request_id": "uuid-v4",
  "data": {}
}
```

**响应示例**：

```json
{
  "type": "response",
  "request_id": "uuid-v4",
  "data": {
    "modules": [
      {
        "id": "cloudfin.p2p",
        "name": "P2P Networking",
        "version": "0.1.0",
        "health": "Healthy"
      }
    ]
  }
}
```

#### module.load — 加载模块

```json
{
  "type": "request",
  "action": "module.load",
  "request_id": "uuid-v4",
  "data": {
    "path": "/path/to/module.so"
  }
}
```

**响应示例**（成功）：

```json
{
  "type": "response",
  "request_id": "uuid-v4",
  "data": {
    "id": "cloudfin.p2p",
    "name": "P2P Networking",
    "version": "0.1.0"
  }
}
```

#### module.unload — 卸载模块

```json
{
  "type": "request",
  "action": "module.unload",
  "request_id": "uuid-v4",
  "data": {
    "id": "cloudfin.p2p"
  }
}
```

#### module.call — 调用模块方法

```json
{
  "type": "request",
  "action": "module.call",
  "request_id": "uuid-v4",
  "data": {
    "id": "cloudfin.p2p",
    "method": "dial",
    "params": {
      "addr": "/ip4/x.x.x.x/tcp/9000"
    }
  }
}
```

#### module.status — 获取模块状态

```json
{
  "type": "request",
  "action": "module.status",
  "request_id": "uuid-v4",
  "data": {
    "id": "cloudfin.p2p"
  }
}
```

#### config.get — 获取配置

```json
{
  "type": "request",
  "action": "config.get",
  "request_id": "uuid-v4",
  "data": {
    "key": "core.listen_port"
  }
}
```

#### config.set — 设置配置

```json
{
  "type": "request",
  "action": "config.set",
  "request_id": "uuid-v4",
  "data": {
    "key": "core.listen_port",
    "value": 19001
  }
}
```

> 注意：`config.set` 触发配置持久化，敏感配置（如密钥相关）应写入 `secrets/` 目录而非配置文件。

#### event — 服务端主动推送

```json
{
  "type": "event",
  "event": "module.health_changed",
  "data": {
    "id": "cloudfin.p2p",
    "health": "Degraded",
    "reason": "Peer connection lost"
  }
}
```

### 2.4 错误码

| 错误码 | 含义 | HTTP 状态码映射 |
|--------|------|----------------|
| `ERR_MODULE_NOT_FOUND` | 模块不存在 | 404 |
| `ERR_MODULE_ALREADY_LOADED` | 模块已加载 | 409 |
| `ERR_METHOD_NOT_FOUND` | 模块方法不存在 | 404 |
| `ERR_INVALID_PARAMS` | 参数无效 | 400 |
| `ERR_INTERNAL` | 内部错误 | 500 |
| `ERR_TIMEOUT` | 操作超时 | 504 |

**错误响应格式**：

```json
{
  "type": "error",
  "request_id": "uuid-v4",
  "error": {
    "code": "ERR_MODULE_NOT_FOUND",
    "message": "Module 'cloudfin.foo' is not loaded"
  }
}
```

---

## 3. Module Trait 完整定义

```rust
use serde::{Deserialize, Serialize};
use serde_json::Value;

// ──────────────────────────────────────────────
// 健康状态枚举
// ──────────────────────────────────────────────
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ModuleHealth {
    /// 模块运行正常
    Healthy,
    /// 模块可工作但存在已知问题
    Degraded(String),
    /// 模块不可用
    Unhealthy(String),
}

// ──────────────────────────────────────────────
// 模块元信息
// ──────────────────────────────────────────────
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModuleInfo {
    pub id: String,
    pub name: String,
    pub version: String,
    pub health: ModuleHealth,
}

// ──────────────────────────────────────────────
// Module Trait — 所有 Cloudfin 模块必须实现此接口
// ──────────────────────────────────────────────
#[async_trait::async_trait]
pub trait Module: Send + Sync {
    // ── 元信息 ──────────────────────────────
    /// 模块唯一标识，格式：`cloudfin.{name}`
    fn id(&self) -> &str;

    /// 模块显示名称
    fn name(&self) -> &str;

    /// 模块语义化版本
    fn version(&self) -> &str;

    // ── 生命周期 ────────────────────────────
    /// 初始化阶段：解析配置、分配资源
    async fn init(&mut self, config: Value) -> anyhow::Result<()>;

    /// 启动阶段：开始业务逻辑、监听端口、注册事件等
    async fn start(&mut self) -> anyhow::Result<()>;

    /// 停止阶段：优雅关闭连接、保存状态、释放资源
    async fn stop(&mut self) -> anyhow::Result<()>;

    // ── 状态 ────────────────────────────────
    /// 返回当前健康状态
    fn status(&self) -> ModuleHealth;

    /// 返回完整元信息（默认实现通过 id/name/version/status 构造）
    fn info(&self) -> ModuleInfo {
        ModuleInfo {
            id: self.id().to_string(),
            name: self.name().to_string(),
            version: self.version().to_string(),
            health: self.status(),
        }
    }

    // ── 调用接口 ────────────────────────────
    /// 供 Core 或其他模块调用的方法分发入口
    /// - `method`：方法名
    /// - `params`：JSON 参数
    /// - 返回：`Ok(Value)` 表示成功结果，`Err(anyhow::Error)` 表示失败
    async fn call(&self, method: &str, params: Value) -> anyhow::Result<Value>;
}
```

> **实现注意事项**
> - `Module` 使用 `#[async_trait]` 以支持 async 方法的 trait object 安全调用。
> - `init`/`start`/`stop` 三阶段严格有序，不得跨阶段调用。
> - `call` 方法须自行实现方法分发逻辑（推荐使用 `match method` 或 hashmap 反射）。
> - 所有耗时的 `call` 实现须内部 spawn 任务，避免阻塞 Core 事件循环。

---

## 4. Module Manifest Schema

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["id", "name", "version", "platform"],
  "additionalProperties": true,
  "properties": {
    "id": {
      "type": "string",
      "pattern": "^cloudfin\\.[a-z0-9_]+$",
      "description": "模块唯一标识，格式：cloudfin.{name}，全部小写"
    },
    "name": {
      "type": "string",
      "description": "模块显示名称"
    },
    "version": {
      "type": "string",
      "pattern": "^\\d+\\.\\d+\\.\\d+$",
      "description": "语义化版本（SemVer），格式：MAJOR.MINOR.PATCH"
    },
    "description": {
      "type": "string",
      "description": "模块功能描述"
    },
    "author": {
      "type": "string",
      "description": "模块作者"
    },
    "platform": {
      "type": "array",
      "items": {
        "type": "string",
        "enum": ["android", "linux", "windows", "macos", "ios"]
      },
      "description": "支持的运行平台列表"
    },
    "permissions": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "所需系统权限列表（如 'network'、'file_system'）"
    },
    "api_version": {
      "type": "string",
      "description": "所需 Core API 版本约束（如 '^1.0'），使用 npm-style semver range"
    },
    "ui": {
      "type": "object",
      "description": "UI 声明（详见第 5 节 UI 声明规范）"
    }
  }
}
```

---

## 5. UI 声明规范

每个模块可在 manifest 中声明自己的 UI 结构，Core 将声明透传给 UI 层，UI 根据声明动态渲染控制面板。

### 5.1 顶层字段

```json
{
  "id": "cloudfin.p2p",
  "name": "P2P Networking",
  "version": "0.1.0",
  "category": "Network",
  "icon": "🌐",
  "description": "P2P 网络连接管理",
  "cards": {
    "info": [],
    "actions": [],
    "settings": []
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | 是 | 模块 ID，须与 manifest.id 一致 |
| `name` | string | 是 | 显示名称 |
| `version` | string | 是 | 版本号 |
| `category` | string | 否 | UI 分类（如 "Network"、"Storage"、"AI"） |
| `icon` | string | 否 | Emoji 图标 |
| `description` | string | 否 | 功能描述 |
| `cards` | object | 是 | 三类卡片定义 |

### 5.2 cards.info — 信息展示卡

用于展示模块的运行指标和状态字段。

| 字段 | 类型 | 说明 |
|------|------|------|
| `key` | string | 字段唯一标识 |
| `label` | string | 显示标签 |
| `style` | enum | 展示样式 |

**`style` 取值说明**：

| 值 | 说明 |
|----|------|
| `text` | 普通文本 |
| `number` | 数字（自动千分位格式化） |
| `duration` | 时长（秒数转为 `HH:MM:SS`） |
| `status` | 状态徽章（Healthy=绿、Degraded=黄、Unhealthy=红） |
| `progress` | 进度条（值范围 0~100） |

**示例**：

```json
{
  "key": "connected_peers",
  "label": "已连接节点",
  "style": "number"
}
```

### 5.3 cards.actions — 操作按钮卡

用于在 UI 中触发模块方法调用。

| 字段 | 类型 | 说明 |
|------|------|------|
| `key` | string | 操作唯一标识 |
| `label` | string | 按钮文字 |
| `action` | string | 触发的 Core action（如 `module.call`，params 由 UI 从上下文收集） |
| `confirm` | string | 可选，确认提示文字；存在时 UI 须先请求用户确认 |

**示例**：

```json
{
  "key": "disconnect_all",
  "label": "断开所有连接",
  "action": "module.call",
  "confirm": "确定断开所有 P2P 连接？"
}
```

### 5.4 cards.settings — 设置项卡

用于展示和修改模块配置项。

| 字段 | 类型 | 说明 |
|------|------|------|
| `key` | string | 配置 key（与 config.get/set 的 key 字段对应） |
| `label` | string | 显示名称 |
| `type` | enum | 输入控件类型 |
| `default` | any | 默认值 |
| `options` | array | 可选项（仅 `select` 类型需要） |
| `description` | string | 可选，说明文字 |

**`type` 取值说明**：

| 值 | 控件类型 |
|----|----------|
| `text` | 单行文本输入框 |
| `number` | 数字输入框 |
| `toggle` | 开关 |
| `select` | 下拉选择框 |
| `password` | 密码输入框（输入被遮蔽） |

**示例**：

```json
{
  "key": "p2p.listen_port",
  "label": "监听端口",
  "type": "number",
  "default": 9000,
  "description": "P2P 节点监听端口，0 表示随机端口"
}
```

---

## 6. 模块分发格式

### 6.1 ZIP 包结构

**文件名规范**：`CloudFin-Mod-{平台}-{模块名}-{版本}.zip`

> 平台名须使用第 6.3 节表格中的标准值；模块名使用小写，多词用 `-` 连接。

**示例**：`CloudFin-Mod-Linux-Android-P2P-0.1.0.zip`

**包内结构**：

```
CloudFin-Mod-Linux-Android-P2P-0.1.0.zip
├── CloudFin-Mod-Linux-Android-P2P-0.1.0.so      # 动态链接模块
├── CloudFin-Mod-Linux-Android-P2P-0.1.0.so.json  # 模块 Manifest
└── README.md                                     # 可选，说明文档
```

> - `.so` 文件为实际运行文件。
> - `.so.json` 为模块 manifest，须与 `.so` 同名。
> - 所有文件名须包含完整平台、模块名、版本信息，确保多版本共存不冲突。

### 6.2 安装目录

```
{Core根目录}/Cloudfin/modules/
```

示例路径：

```
/opt/cloudfin/Cloudfin/modules/cloudfin.p2p/   # 已安装的 cloudfin.p2p 模块
```

### 6.3 平台命名对照表

| 平台标识 | 适用操作系统 |
|----------|--------------|
| `Linux` | Linux（x86_64 / aarch64） |
| `Android` | Android（arm64-v8a） |
| `Linux-Android` | Linux 与 Android 共用（交叉编译产物） |
| `Windows` | Windows（x86_64） |
| `macOS` | macOS（x86_64 / aarch64） |
| `iOS` | iOS（arm64） |

---

## 7. 安装引导状态机

```
                         ┌──────────────────────────────────────────────┐
                         │                    IDLE                      │
                         │  （检测 Core 是否已安装）                      │
                         └──────────────────────┬───────────────────────┘
                                                │
                                    ┌───────────▼───────────┐
                                    │    CHECKING_CORE       │
                                    │  （正在检测 Core 版本）  │
                                    └───────────┬───────────┘
                                                │
                            ┌───────────────────┴───────────────────┐
                            │                                       │
                 ┌──────────▼──────────┐              ┌────────────▼───────────┐
                 │   CORE_UP_TO_DATE    │              │   CORE_OUTDATED /      │
                 │  （Core 版本已是最新）  │              │   CORE_NOT_INSTALLED   │
                 └──────────┬──────────┘              └────────────┬───────────┘
                            │                                       │
                            │                            ┌─────────▼──────────┐
                            │                            │  DOWNLOADING_CORE   │
                            │                            │   （正在下载 Core）   │
                            │                            └─────────┬──────────┘
                            │                                       │
                            │                            ┌─────────▼──────────┐
                            │                            │  INSTALLING_CORE    │
                            │                            │   （正在安装 Core）   │
                            │                            └─────────┬──────────┘
                            │                                       │
                            └───────────────────┬───────────────────┘
                                                │
                                    ┌───────────▼───────────┐
                                    │   CHECKING_MODULES     │
                                    │ （检查已安装模块状态）    │
                                    └───────────┬───────────┘
                                                │
                                    ┌───────────▼───────────┐
                                    │  MODULES_USER_SELECT   │
                                    │  （用户选择所需模块）    │
                                    └───────────┬───────────┘
                                                │
                            ┌───────────────────┴───────────────────┐
                            │                                       │
                 ┌──────────▼──────────┐              ┌────────────▼───────────┐
                 │   MODULES_UP_TO_DATE │              │  DOWNLOADING_MODULES   │
                 │  （模块版本已是最新）   │              │   （正在下载模块）        │
                 └──────────┬──────────┘              └────────────┬───────────┘
                            │                                       │
                            │                            ┌─────────▼──────────┐
                            │                            │  INSTALLING_MODULES │
                            │                            │   （正在安装模块）    │
                            │                            └─────────┬──────────┘
                            │                                       │
                            └───────────────────┬───────────────────┘
                                                │
                                    ┌───────────▼───────────┐
                                    │       READY            │
                                    │  （Core 及模块就绪）     │
                                    └────────────────────────┘
```

### 状态说明

| 状态 | 说明 |
|------|------|
| `IDLE` | 初始状态，检测 Core 是否安装 |
| `CHECKING_CORE` | 正在检测 Core 是否安装或版本是否最新 |
| `DOWNLOADING_CORE` | 正在从分发服务器下载 Core |
| `INSTALLING_CORE` | 正在安装 Core 到目标路径 |
| `CHECKING_MODULES` | 正在检查已安装模块的版本状态 |
| `MODULES_USER_SELECT` | 等待用户在 UI 选中需要安装的模块 |
| `DOWNLOADING_MODULES` | 正在下载选中的模块包 |
| `INSTALLING_MODULES` | 正在解压并安装模块到 `modules/` 目录 |
| `READY` | 全部就绪，可进入正常运行状态 |

### 状态转换触发条件

| 源状态 | 目标状态 | 触发条件 |
|--------|----------|----------|
| `IDLE` | `CHECKING_CORE` | 系统启动或手动触发检查 |
| `CHECKING_CORE` | `CORE_UP_TO_DATE` | Core 已安装且版本 >= 最新版本 |
| `CHECKING_CORE` | `DOWNLOADING_CORE` | Core 未安装或版本低于最新版本 |
| `DOWNLOADING_CORE` | `INSTALLING_CORE` | 下载完成（校验通过） |
| `INSTALLING_CORE` | `CHECKING_MODULES` | 安装完成 |
| `CHECKING_MODULES` | `MODULES_USER_SELECT` | 存在可选模块且用户尚未选择 |
| `CHECKING_MODULES` | `READY` | 所有已安装模块已是最新，无可选更新 |
| `MODULES_USER_SELECT` | `DOWNLOADING_MODULES` | 用户确认模块选择 |
| `DOWNLOADING_MODULES` | `INSTALLING_MODULES` | 所有模块包下载完成 |
| `INSTALLING_MODULES` | `READY` | 所有模块安装完成 |

---

## 8. 配置存储

### 8.1 配置文件

- **存储位置**：`{Core根目录}/Cloudfin/config/`
- **格式**：JSON（每个模块一个文件，文件名与模块 ID 一致）
- **命名规范**：`{模块ID}.json`（如 `cloudfin.p2p.json`）

**示例** `cloudfin.p2p.json`：

```json
{
  "listen_port": 9000,
  "max_peers": 50,
  "enable_upnp": true,
  "boot_nodes": [
    "/ip4/1.2.3.4/tcp/9000/p2p/Qm..."
  ]
}
```

### 8.2 密钥存储

- **存储位置**：`{Core根目录}/Cloudfin/secrets/`
- **访问方式**：仅进程内存访问，**不落盘**
- **持久化策略**：由 Core 管理加密存储（方案待定），模块通过 Core API 读写

> **安全提示**：所有密钥相关的配置不得写入 JSON 配置文件，须通过 Core secrets API 操作。

### 8.3 配置变更通知

配置变更通过以下流程生效：

1. UI 调用 `config.set`
2. Core 更新内存中的配置值并持久化到 JSON 文件
3. Core 向相关模块发送 `config.changed` 事件
4. 模块收到事件后重新加载配置

---

## 9. 日志规范

### 9.1 文件规范

- **存储位置**：`{Core根目录}/Cloudfin/logs/`
- **文件名格式**：`{YYYY}-{MM}-{DD}.log`（例：`2026-04-05.log`）
- **滚动策略**：按天滚动，保留最近 **30 天**日志

### 9.2 日志级别

| 级别 | 关键字 | 使用场景 |
|------|--------|----------|
| ERROR | `ERROR` | 未捕获的异常、不可恢复的错误 |
| WARN | `WARN` | 可恢复的异常、潜在问题（如配置缺失使用默认值） |
| INFO | `INFO` | 重要业务事件（模块加载/卸载、连接建立） |
| DEBUG | `DEBUG` | 开发调试信息（请求/响应详情） |
| TRACE | `TRACE` | 最细粒度追踪（协议帧解析、中间件处理） |

### 9.3 日志格式

```
{时间戳} {级别} [{模块ID}] {消息}
```

**示例**：

```
2026-04-05T12:30:01.123Z INFO  [cloudfin.p2p] Module started, listening on /ip4/0.0.0.0/tcp/9000
2026-04-05T12:30:02.456Z WARN  [cloudfin.p2p] Peer connection degraded: /ip4/1.2.3.4/tcp/9000
2026-04-05T12:30:03.789Z ERROR [cloudfin.core] Failed to load module cloudfin.foo: library not found
```

### 9.4 模块日志隔离

每个模块的日志须在 `[ ]` 中标注模块 ID，便于过滤排查。Core 日志使用 `[cloudfin.core]`。

---

## 附录 A：快速参考

### A.1 端口分配

| 端口 | 用途 |
|------|------|
| 19001 | Core WebSocket 服务默认监听端口 |
| 19000 | Core HTTP 服务默认监听端口（健康检查） |

### A.2 目录结构

```
Cloudfin/
├── Cloudfin            # 可执行文件（Core）
├── config/             # 配置文件
│   ├── cloudfin.core.json
│   └── cloudfin.p2p.json
├── secrets/            # 密钥存储（内存，不落盘）
├── modules/            # 动态模块
│   ├── cloudfin.p2p.so
│   └── cloudfin.p2p.so.json
└── logs/               # 日志文件
    └── 2026-04-05.log
```

### A.3 关键依赖版本

| 依赖 | 版本 | 用途 |
|------|------|------|
| `tokio` | ^1.0 | 异步运行时 |
| `axum` | ^0.7 | HTTP/WebSocket 服务 |
| `libloading` | ^0.8 | 动态模块加载 |
| `libp2p` | ^0.54 | P2P 网络 |
| `yrs` | ^0.22 | CRDT 共享状态 |
| `serde` / `serde_json` | ^1.0 | 序列化/反序列化 |
| `tracing` / `tracing-subscriber` | ^0.3 | 结构化日志 |
| `anyhow` | ^1.0 | 错误处理 |
| `uuid` | ^1.0 | request_id 生成 |

---

> 本文档为 Cloudfin v1.0 实现规范，所有代码实现须严格遵循。如有变更须经评审后更新本文档。
