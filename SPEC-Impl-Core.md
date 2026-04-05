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

## 2. Core WebSocket API

### 2.1 WebSocket端点

- **路径**：`ws://127.0.0.1:19001/ws`
- **说明**：UI 与 Core 的唯一通信通道，所有业务逻辑均通过此连接进行

### 2.2 API Actions

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

### 2.3 错误码

| 错误码 | 含义 |
|--------|------|
| `ERR_MODULE_NOT_FOUND` | 模块不存在 |
| `ERR_MODULE_ALREADY_LOADED` | 模块已加载 |
| `ERR_METHOD_NOT_FOUND` | 模块方法不存在 |
| `ERR_INVALID_PARAMS` | 参数无效 |
| `ERR_INTERNAL` | 内部错误 |
| `ERR_TIMEOUT` | 操作超时 |

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

## 5. 配置存储

### 5.1 配置文件

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

### 5.2 密钥存储

- **存储位置**：`{Core根目录}/Cloudfin/secrets/`
- **访问方式**：仅进程内存访问，**不落盘**
- **持久化策略**：由 Core 管理加密存储（方案待定），模块通过 Core API 读写

> **安全提示**：所有密钥相关的配置不得写入 JSON 配置文件，须通过 Core secrets API 操作。

### 5.3 配置变更通知

配置变更通过以下流程生效：

1. UI 调用 `config.set`
2. Core 更新内存中的配置值并持久化到 JSON 文件
3. Core 向相关模块发送 `config.changed` 事件
4. 模块收到事件后重新加载配置

---

## 6. 日志规范

### 6.1 文件规范

- **存储位置**：`{Core根目录}/Cloudfin/logs/`
- **文件名格式**：`{YYYY}-{MM}-{DD}.log`（例：`2026-04-05.log`）
- **滚动策略**：按天滚动，保留最近 **30 天**日志

### 6.2 日志级别

| 级别 | 关键字 | 使用场景 |
|------|--------|----------|
| ERROR | `ERROR` | 未捕获的异常、不可恢复的错误 |
| WARN | `WARN` | 可恢复的异常、潜在问题（如配置缺失使用默认值） |
| INFO | `INFO` | 重要业务事件（模块加载/卸载、连接建立） |
| DEBUG | `DEBUG` | 开发调试信息（请求/响应详情） |
| TRACE | `TRACE` | 最细粒度追踪（协议帧解析、中间件处理） |

### 6.3 日志格式

```
{时间戳} {级别} [{模块ID}] {消息}
```

**示例**：

```
2026-04-05T12:30:01.123Z INFO  [cloudfin.p2p] Module started, listening on /ip4/0.0.0.0/tcp/9000
2026-04-05T12:30:02.456Z WARN  [cloudfin.p2p] Peer connection degraded: /ip4/1.2.3.4/tcp/9000
2026-04-05T12:30:03.789Z ERROR [cloudfin.core] Failed to load module cloudfin.foo: library not found
```

### 6.4 模块日志隔离

每个模块的日志须在 `[ ]` 中标注模块 ID，便于过滤排查。Core 日志使用 `[cloudfin.core]`。

---

## 附录 A：快速参考

### A.1 端口分配

| 端口 | 用途 |
|------|------|
| 19001 | Core WebSocket 服务默认监听端口（UI↔Core 唯一通道） |
### A.2 目录结构

```
{CoreRoot}/Cloudfin/
├── cloudfin-core          # 可执行文件
├── config/
│   ├── core.json          # Core 主配置
│   └── modules/           # 模块配置目录
├── modules/               # 模块文件目录（.so 和 .so.json 同目录）
├── logs/
│   ├── cloudfin.log
│   └── cloudfin.log.2026-04-04
├── cloudfin.pid
└── data/                  # 模块运行时数据
```

### A.3 关键依赖版本

| 依赖 | 版本 | 用途 |
|------|------|------|
| `tokio` | ^1.0 | 异步运行时 |
| `axum` | ^0.7 | WebSocket 服务 |
| `libloading` | ^0.8 | 动态模块加载 |
| `serde` / `serde_json` | ^1.0 | 序列化/反序列化 |
| `tracing` / `tracing-subscriber` | ^0.3 | 结构化日志 |
| `anyhow` | ^1.0 | 错误处理 |
| `uuid` | ^1.0 | request_id 生成 |

---

> 本文档为 Cloudfin v1.0 实现规范，所有代码实现须严格遵循。如有变更须经评审后更新本文档。
