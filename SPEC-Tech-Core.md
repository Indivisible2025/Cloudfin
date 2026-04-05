# Cloudfin Core 技术文档

**版本：** v1.0
**日期：** 2026-04-05
**状态：** Draft（待评审）
**级别：** L1（路线级）

---

## 1. 框架选型

**Core 使用 Rust**，以下框架均基于 Rust 生态。

### 1.1 WebSocket 框架对比

UI 与 Core 之间**只走 WebSocket**，不使用 HTTP。以下对三个主流候选框架进行对比分析。

| 维度 | Axum 0.7 | Actix-web 4 | Warp |
|------|----------|-------------|------|
| **运行时依赖** | 官方推荐 Tokio | 需额外配置 | 依赖 Tower/Ping Yin |
| **WS 支持** | 通过 `axum::extract::ws` 原生支持 | 需额外 crate | Filter 模式，WS 需特殊 filter |
| **类型安全** | 强（基于 Tower Service） | 强 | 中（Filter 链式组合） |
| **中间件生态** | 丰富（Tower 全套） | 较丰富（actix-middleware） | 依赖 Filter 组合 |
| **二进制体积** | 最小 | 较大 | 中 |
| **学习曲线** | 平缓 | 较陡 | 陡（Filter 概念独特） |
| **社区活跃度** | 高（2023-2024 爆发式增长） | 高（成熟稳定） | 中（维护但不活跃） |
| ** Tokio 2024 契合度** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ |

**结论：** Axum 0.7 在异步原生设计、Tower 中间件生态、Tokio 深度集成三个维度均最优。

### 1.2 Tokio Runtime 配置

Core 使用单一 Tokio 多线程运行时。所有异步任务在该运行时内并发执行。

```rust
use tokio::runtime::Builder;

fn build_runtime() -> tokio::runtime::Runtime {
    Builder::new_multi_thread()
        .worker_threads(num_cpus::get()) // 默认等于 CPU 核心数
        .thread_name("cloudfin-core")
        .thread_stack_size(3 * 1024 * 1024) // 3MB 栈空间
        .enable_io() // 启用 IO driver（网络 IO 必须）
        .enable_time() // 启用时间驱动（定时器必须）
        .build()
        .expect("Failed to build Tokio runtime")
}
```

**运行时参数说明：**

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `worker_threads` | CPU 核心数 | 可通过 `CLOUDFIN_WORKER_THREADS` 环境变量覆盖 |
| `thread_stack_size` | 3MB | 防止深层递归导致栈溢出 |
| `max_blocking_threads` | 512 | 阻塞型任务线程池上限 |

> **注意：** 不使用 `Runtime::new()`（单线程），因为 Core 需要同时处理 WebSocket 消息和模块管理等多个并发任务源。

### 1.3 广播通道（State Sharing）

各模块与 Core 之间的状态共享使用 Tokio 原生的 `broadcast` channel：

```rust
use tokio::sync::broadcast;

// 全局状态广播通道，容量 1024
static STATE_BROADCAST: std::sync::OnceLock<broadcast::Sender<CoreEvent>> =
    std::sync::OnceLock::new();

#[derive(Clone, Debug)]
pub enum CoreEvent {
    ConfigChanged { key: String },
    ModuleHealthChanged { module: String, healthy: bool },
    StatusSnapshot { modules: Vec<ModuleStatus> },
    ShutdownRequested,
}
```

- 新连接的 WebSocket 客户端自动订阅最新状态快照（`StatusSnapshot`）
- 配置变更通过 `ConfigChanged` 事件通知所有订阅者
- 模块健康状态变化通过 `ModuleHealthChanged` 分发

---

## 2. Core 版本管理

Core 版本号由 `Cargo.toml` 中的 `version` 字段定义，编译时通过 `env!("CARGO_PKG_VERSION")` 嵌入二进制。

UI 通过 `core.info` action 获取版本信息：

```json
// 请求
{ "action": "core.info", "data": {} }

// 响应
{
  "name": "cloudfin-core",
  "version": "v2026.04.05.001",
  "api_version": "1"
}
```

---

## 2. Core API 设计

### 2.1 WebSocket 端点

Core 仅通过 WebSocket 与 UI 通信，不提供任何 HTTP 端点。

| 地址 | 说明 |
|------|------|
| `ws://127.0.0.1:19001/ws` | UI 与 Core 的唯一通信通道 |

**握手请求：**

```http
GET /ws HTTP/1.1
Host: 127.0.0.1:19001
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
Sec-WebSocket-Version: 13
```

**握手响应（成功）：**

```http
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
```

**服务端推送消息格式（Server → Client）：**

所有 WebSocket 消息均为 JSON，格式如下：

```json
{
  "request_id": "req_abc123",
  "action": "status_snapshot",
  "timestamp": "2026-04-05T14:00:00Z",
  "payload": {
    "name": "cloudfin-core",
    "version": "v2026.04.05.001",
    "api_version": "1",
    "modules": [...]
  }
}
```

| action | 方向 | 说明 |
|--------|------|------|
| `subscribe` | C→S | 订阅特定事件类型 |
| `unsubscribe` | C→S | 取消订阅 |
| `ping` | C→S | 心跳（服务端回复 `pong`） |
| `status_snapshot` | S→C | 状态快照推送 |
| `config_changed` | S→C | 配置变更通知 |
| `module_health` | S→C | 模块健康状态变化 |

**pong 回复格式：**

```json
{
  "request_id": "req_client_001",
  "action": "pong",
  "timestamp": "2026-04-05T14:00:01Z"
}
```

### 2.3 API Actions 列表

| Action | 通道 | 来源 | 触发条件 |
|--------|------|------|----------|
| `status_snapshot` | WS→Client | Core | 连接时自动推送 + 定期推送（每 60s） |
| `config_changed` | WS→Client | Core | 配置热更新后广播 |
| `module_health` | WS→Client | Core | 模块健康状态变化时广播 |
| `module_event` | WS→Client | 模块 | 模块主动通过 Core 推送的消息 |

### 2.4 错误码体系

Core API 使用统一的错误码体系，所有错误响应格式如下：

```json
{
  "request_id": "req_abc123",
  "ok": false,
  "error": {
    "code": "MODULE_NOT_FOUND",
    "message": "模块 'trader-xxx' 未加载",
    "details": {}
  }
}
```

**错误码一览表：**

| 错误码 | 描述 | 说明 | 示例 |
|--------|-------------|------|------|
| `OK` | 成功 | 成功 | — |
| `INVALID_REQUEST` | 请求格式错误 | 请求格式错误 | JSON 解析失败 |
| `MODULE_NOT_FOUND` | 模块不存在 | 模块不存在 | `DELETE` 未加载的模块 |
| `MODULE_ALREADY_LOADED` | 模块已加载 | 模块已加载 | 重复加载同名模块 |
| `MODULE_INIT_FAILED` | 模块初始化失败 | 模块初始化失败 | `.so` 入口返回错误 |
| `MODULE_START_FAILED` | 模块启动失败 | 模块启动失败 | 模块内部错误 |
| `CONFIG_INVALID` | 配置项无效 | 配置项无效 | 热更新字段不存在 |
| `CONFIG_WRITE_FAILED` | 配置写入失败 | 配置写入失败 | 磁盘权限错误 |
| `INTERNAL_ERROR` | Core 内部错误 | Core 内部错误 | 未预期 panic |

---

## 3. 配置管理

### 3.1 配置文件格式

Core 的配置文件以 JSON 格式存储在 `{CoreRoot}/Cloudfin/config/` 目录下：

```
{CoreRoot}/Cloudfin/
└── config/
    ├── core.json          # Core 主配置
    └── modules/           # 模块配置目录（每个模块一个 .json）
        ├── p2p.json
        └── crdt.json
```

#### `core.json` 完整结构

```json
{
  "version": 1,
  "core": {
    "bind_host": "127.0.0.1",
    "bind_port": 19001,
    "worker_threads": null,
    "log": {
      "level": "info",
      "directory": "{CoreRoot}/Cloudfin/logs",
      "rotation": "daily",
      "retention_days": 30
    }
  },
  "modules": {
    "auto_load": true,
    "directory": "{CoreRoot}/Cloudfin/modules",
    "permit_list": []
  },
  "security": {
    "allow_external_bind": false,
    "module_permissions_strict": true
  }
}
```

**字段说明：**

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `core.bind_host` | String | `127.0.0.1` | 绑定地址（强制本地） |
| `core.bind_port` | u16 | `19001` | 监听端口 |
| `core.worker_threads` | u32 | `null`（自动） | Tokio worker 数量 |
| `core.log.level` | String | `info` | 日志级别 |
| `core.log.directory` | String | — | 日志目录（支持变量替换） |
| `core.log.rotation` | String | `daily` | 轮转策略 |
| `core.log.retention_days` | u32 | `30` | 保留天数 |
| `modules.auto_load` | bool | `true` | 启动时自动加载模块 |
| `modules.directory` | String | — | 模块 `.so` 文件目录 |
| `modules.permit_list` | Array | `[]` | 允许加载的模块名白名单（空=全部） |
| `security.allow_external_bind` | bool | `false` | 是否允许绑定非本地地址（默认 false） |
| `security.module_permissions_strict` | bool | `true` | 是否启用模块权限严格模式 |

### 3.2 热更新机制

Core 支持在运行期间修改配置，无需重启进程。

**热更新流程：**

1. UI 通过 WebSocket 发送 `config.set` action
2. Core 验证配置变更合法性（字段存在性、类型正确性）
3. 将新值写入磁盘 `{CoreRoot}/Cloudfin/config/core.json`
4. 通过 `broadcast` channel 向所有 WebSocket 客户端推送 `config_changed` 事件
5. 各订阅者（WebSocket 处理器、模块）收到通知并应用新配置

**验证规则（`config_validator.rs`）：**

```rust
pub struct ConfigValidator;

impl ConfigValidator {
    pub fn validate_change(key: &str, value: &serde_json::Value) -> Result<()> {
        match key {
            "core.log.level" => {
                let level = value.as_str().ok_or(ConfigError::InvalidType)?;
                match level {
                    "error" | "warn" | "info" | "debug" | "trace" => Ok(()),
                    _ => Err(ConfigError::InvalidValue("unknown log level".into())),
                }
            }
            "core.bind_port" => {
                let port = value.as_u64().ok_or(ConfigError::InvalidType)?;
                if (1..=65535).contains(&port) {
                    Ok(())
                } else {
                    Err(ConfigError::OutOfRange("port must be 1-65535".into()))
                }
            }
            _ => Err(ConfigError::UnknownKey(key.into())),
        }
    }
}
```

**不支持热更新的字段（需重启）：**

- `core.bind_host` — 绑定地址变更需要重新打开监听 socket
- `core.worker_threads` — Tokio runtime 线程数在创建后不可更改

### 3.3 配置变更通知

配置变更通过 WebSocket 广播，Payload 格式如下：

```json
{
  "request_id": "",
  "action": "config_changed",
  "timestamp": "2026-04-05T14:00:00Z",
  "payload": {
    "key": "core.log.level",
    "old_value": "info",
    "new_value": "debug",
    "changed_by": "ui"
  }
}
```

---

## 4. 模块管理器

### 4.1 动态加载实现

模块（Plugin）以 `.so`（Linux）/`.dylib`（macOS）动态库形式存储在 `{CoreRoot}/Cloudfin/modules/`。

**核心加载逻辑（`plugin_manager.rs`）：**

```rust
use libloading::{Library, Symbol};
use std::collections::HashMap;

pub struct PluginManager {
    libraries: HashMap<String, Library>,
    instances: HashMap<String, Box<dyn ModuleInstance>>,
}

pub trait ModuleInstance: Send + Sync {
    fn name(&self) -> &str;
    fn version(&self) -> &str;
    fn init(&self, config: serde_json::Value) -> Result<()>;
    fn start(&self) -> Result<()>;
    fn stop(&self) -> Result<()>;
    fn health_check(&self) -> HealthStatus;
}

#[repr(C)]
pub struct ModuleApi {
    pub init: unsafe extern "C" fn(config: *const u8, len: usize) -> *mut ModuleCtx,
    pub version: unsafe extern "C" fn() -> *const u8,
}
```

**加载流程：**

```
load_module(name: "trader-binance")
  → dlopen("libtrader-binance.so")        // libloading
  → dlsym("cloudfin_module_api")          // 获取导出符号
  → verify_version(CoreVersion)          // 版本校验
  → module.init(config)                   // 调用模块 init()
  → module.start()                        // 调用模块 start()
  → register_health_check(module)         // 注册健康检查
```

### 4.2 模块生命周期

```
       ┌──────────────────────────────────────────────┐
       │                  Core 启动                    │
       └──────────┬───────────────────┬────────────────┘
                  │ load config      │
                  ▼                  │
       ┌──────────────────┐           │
       │  PluginManager   │◄──────────┘
       │  ::new(config)   │
       └────────┬─────────┘
                │ load_module (for each module.json entry)
                ▼
        ┌───────────────┐
        │    INIT       │  模块加载：分配资源、读取配置、注册 handler
        └───────┬───────┘
                │ init() 成功
                ▼
        ┌───────────────┐
        │    START      │  模块启动：开始业务逻辑（连接交易所等）
        └───────┬───────┘
                │ start() 成功
                ▼
        ┌───────────────┐
        │   RUNNING     │  正常运行：接受 Core 事件，定期健康检查
        └───────┬───────┘
                │
                │ stop() / Core shutdown
                ▼
        ┌───────────────┐
        │     STOP      │  停止模块：优雅退出、释放资源
        └───────┬───────┘
                │
                ▼
        ┌───────────────┐
        │   UNLOADED    │  dlclose() 卸载动态库
        └───────────────┘
```

**生命周期状态机定义：**

```rust
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ModuleLifecycle {
    Unloaded,
    Init,
    Start,
    Running,
    Stopping,
    Stopped,
}
```

### 4.3 健康状态监控

Core 定期对已加载模块执行健康检查，间隔 30 秒。

```rust
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HealthStatus {
    Healthy,
    Degraded(String),  // 可恢复的异常，带原因
    Unhealthy(String), // 严重异常
    Unknown,
}

impl PluginManager {
    fn health_check_loop(rx: broadcast::Receiver<()>) {
        loop {
            tokio::time::sleep(Duration::from_secs(30)).await;
            if rx.try_recv().is_ok() {
                break; // 收到关闭信号
            }

            let results = self.check_all_modules();
            let changed: Vec<_> = results
                .iter()
                .filter(|(name, status)| self.last_status(name) != Some(status))
                .collect();

            if !changed.is_empty() {
                self.broadcast_health_change(changed);
            }
        }
    }
}
```

**模块向 Core 报告异常：**

```rust
impl ModuleInstance for MyModule {
    fn health_check(&self) -> HealthStatus {
        if self.connection.is_connected() {
            HealthStatus::Healthy
        } else {
            HealthStatus::Degraded("connection lost, reconnecting".into())
        }
    }
}
```

### 4.4 版本校验

Core 与模块之间的版本耦合通过语义化版本（SemVer）控制。

```rust
use semver::{Version, VersionReq};

const CORE_VERSION: &str = env!("CARGO_PKG_VERSION");

pub fn verify_module_version(module_version_str: &str) -> Result<Version> {
    let module_version = Version::parse(module_version_str)
        .map_err(|_| ModuleError::InvalidVersion)?;

    // Core 向后兼容：模块版本 <= Core 版本 + 1 个 minor
    let compatibility_constraint = format!("<={}.{}.x", CORE_VERSION.major, CORE_VERSION.minor);

    let requirement = VersionReq::parse(&compatibility_constraint)
        .map_err(|_| ModuleError::InvalidVersionRequirement)?;

    if requirement.matches(&module_version) {
        Ok(module_version)
    } else {
        Err(ModuleError::VersionIncompatible {
            core: CORE_VERSION.into(),
            module: module_version_str.into(),
            requirement: compatibility_constraint,
        })
    }
}
```

**版本兼容规则：**

- 模块 `major.minor.patch` ≤ Core `major.minor.x`
- Core 1.2.x 兼容模块 1.2.0 ~ 1.2.z
- Core 1.2.x **不兼容**模块 1.3.0（跨 minor）
- Core 1.2.x **不兼容**模块 2.0.0（跨 major）

---

## 5. 日志规范

### 5.1 日志格式

Core 使用 `tracing` crate 实现结构化日志。所有日志均为 JSON 行（line-oriented JSON）。

**日志字段定义：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `timestamp` | ISO 8601 | 精确到毫秒 |
| `level` | String | ERROR/WARN/INFO/DEBUG/TRACE |
| `message` | String | 人类可读消息 |
| `module` | String | 来源模块名（Core 或插件名） |
| `request_id` | String | 关联请求 ID（若有） |
| `target` | String | Rust target（`tracing` target） |
| `span` | Object | tracing span 上下文 |
| `fields` | Object | 额外结构化字段 |

**示例输出：**

```json
{"timestamp":"2026-04-05T14:00:00.123Z","level":"INFO","message":"Module trader-binance loaded","module":"core","request_id":null,"target":"cloudfin::plugin","module_name":"trader-binance","module_version":"1.2.0"}
{"timestamp":"2026-04-05T14:00:01.456Z","level":"DEBUG","message":"WebSocket client connected","module":"core","request_id":null,"target":"cloudfin::ws","client_id":"ws_001","remote_addr":"127.0.0.1:54321"}
{"timestamp":"2026-04-05T14:00:02.789Z","level":"WARN","message":"Module health degraded","module":"core","request_id":null,"target":"cloudfin::health","module_name":"trader-binance","reason":"connection lost"}
```

### 5.2 日志级别

| 级别 | 数值 | 使用场景 |
|------|------|----------|
| `ERROR` | 1 | 模块初始化失败、Core panic、不可恢复错误 |
| `WARN` | 2 | 模块健康降级、配置异常、网络重连 |
| `INFO` | 3 | 模块加载/卸载、WebSocket 消息、启动/关闭 |
| `DEBUG` | 4 | WebSocket 消息收发、详细业务逻辑 |
| `TRACE` | 5 | 底层细节（字节流、帧解析、中间件执行） |

**默认级别：** 生产环境 `INFO`，开发环境 `DEBUG`。

### 5.3 日志轮转

日志轮转策略为**每天一个新文件**，保留 30 天。

**实现方案：** 使用 `tracing-appender` + `rolling_file`（基于 `tracing-subscriber`）。

```rust
use tracing_appender::rolling::{RollingFileAppender, Rotation};
use tracing_subscriber::{fmt, layer::SubscriberExt, util::SubscriberInitExt};

fn init_logging(config: &LogConfig) {
    let log_dir = config.directory.replace("{CoreRoot}", std::env::var("CLOUDFIN_ROOT").unwrap().as_str());
    std::fs::create_dir_all(&log_dir).ok();

    let file_appender = RollingFileAppender::new(
        Rotation::DAILY,
        &log_dir,
        "cloudfin.log",
    );

    let (non_blocking, _guard) = tracing_appender::non_blocking(file_appender);

    // 持久化日志
    let file_layer = fmt::layer()
        .with_writer(non_blocking)
        .with_ansi(false)
        .json();

    // 控制台日志（stdout）
    let stdout_layer = fmt::layer()
        .with_writer(std::io::stdout)
        .with_ansi(true)
        .with_target(true);

    tracing_subscriber::registry()
        .with(file_layer)
        .with(stdout_layer)
        .with(tracing_subscriber::filter::LevelFilter::from_str(&config.level).unwrap())
        .init();

    // 将 guard 泄漏到全局，防止运行时被 drop
    std::mem::forget(_guard);
}
```

**日志文件命名：**

```
cloudfin.log           # 当天日志（始终同名）
cloudfin.log.2026-04-04  # 前一天
cloudfin.log.2026-04-03  # 前两天
...
cloudfin.log.2026-03-06  # 30 天前（保留截止）
```

**清理机制：** Core 启动时自动清理超过 30 天的日志文件。

### 5.4 模块日志隔离

每个模块拥有独立的日志 `tracing` 目标域（target），实现日志隔离。

```rust
// Core 日志
tracing::info!(target: "cloudfin::core", "Core started");

// 模块日志（每个模块用各自 target）
tracing::info!(
    target: "cloudfin::module::trader-binance",
    "Connected to exchange, latency=45ms"
);
```

**过滤器配置示例（`core.json`）：**

```json
{
  "log": {
    "level": "info",
    "per_module": {
      "cloudfin::core": "debug",
      "cloudfin::module::trader-*": "info",
      "cloudfin::ws": "warn"
    }
  }
}
```

通过 `tracing-subscriber` 的 `EnvFilter` 实现动态调整。

---

## 6. 安全设计

### 6.1 网络绑定

**强制本地绑定策略：** Core 只绑定到 `127.0.0.1:19001`，不监听任何公网或 LAN 地址。

```rust
fn validate_bind_address(host: &str, port: u16) -> Result<SocketAddr> {
    if host != "127.0.0.1" && host != "localhost" && host != "::1" {
        return Err(ConfigError::SecurityViolation(
            "Core must bind to localhost only".into(),
        ));
    }

    let config_allow_external = std::env::var("CLOUDFIN_ALLOW_EXTERNAL_BIND").unwrap_or_default();
    if config_allow_external == "true" {
        tracing::warn!(
            target: "cloudfin::security",
            "External bind is enabled. This is insecure for production."
        );
    }

    let addr: SocketAddr = format!("{}:{}", host, port).parse().unwrap();
    Ok(addr)
}
```

**多层安全理由：**

1. Core 与 UI 之间的通信只走 **WebSocket**
2. 不依赖 TLS 也能保证数据不泄露（同一台机器内）
3. 即使 Core 存在漏洞，攻击者也无法从网络层直接接触

### 6.2 模块权限

模块权限采用**白名单 + 最小权限**原则。

**权限定义（`module_permissions.rs`）：**

```rust
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub enum ModulePermission {
    // 网络权限
    NetConnect,         // 建立对外 TCP/UDP 连接
    NetListen,          // 监听端口（默认禁止）
    // 文件权限
    FileRead(String),   // 读文件（路径前缀限定）
    FileWrite(String),  // 写文件（路径前缀限定）
    // 系统权限
    EnvRead,            // 读取环境变量
    SpawnProcess,       // 派生子进程
    // 敏感权限
    SecretsRead,        // 读取密钥（短暂存在于内存）
}

pub struct PermissionGuard {
    allowed: Arc<HashSet<ModulePermission>>,
}
```

**权限校验：**

```rust
impl PermissionGuard {
    pub fn check(&self, permission: &ModulePermission) -> bool {
        self.allowed.contains(permission)
    }
}

// 模块注册时声明所需权限
fn module_declare_permissions() -> Vec<ModulePermission> {
    vec![
        ModulePermission::NetConnect,
        ModulePermission::FileRead("{CoreRoot}/Cloudfin/modules".into()),
        ModulePermission::SecretsRead,
    ]
}
```

**严格模式（默认开启）：**

```json
{
  "security": {
    "module_permissions_strict": true
  }
}
```

当 `strict: true` 时，模块访问未声明权限将触发 `WARN` 并拒绝操作。

### 6.3 密钥处理

**原则：密钥只存在于内存，不持久化到磁盘。**

```rust
pub struct SecretVault {
    // 使用 String 而非 &str，避免在 panic 时被打印到日志
    secrets: RwLock<HashMap<String, String>>,
}

impl SecretVault {
    pub fn store(&self, key: &str, value: String) {
        let mut vault = self.secrets.write();
        vault.insert(key.to_owned(), value);
    }

    pub fn get(&self, key: &str) -> Option<String> {
        self.secrets.read().get(key).cloned()
    }

    pub fn revoke(&self, key: &str) {
        // 用随机数据覆写后删除，防止内存残留
        if let Some(mut val) = self.secrets.write().remove(key) {
            for byte in val.as_bytes_mut() {
                *byte = rand::random();
            }
        }
    }
}
```

**密钥来源（优先级）：**

1. 启动参数 `--secret <key>=<value>`
2. 环境变量 `CLOUDFIN_SECRET_<NAME>`
3. Core 启动后通过 WebSocket `secrets.inject` action 注入（来自 UI 的加密传输）

**内存隔离：**

- 密钥以 `String` 类型存储（堆分配），而非 `&'static str`（静态常量区）
- 进程退出时，`SecretVault` 析构时自动覆写并清零
- 密钥在内存中的逗留时间应尽量短——使用后立即 revoke

---

## 7. 启动与关闭

### 7.1 启动顺序

```
┌─────────────────────────────────────────────────────────────────┐
│                         main() 入口                             │
└────────────────────────────┬────────────────────────────────────┘
                             │
              ┌──────────────▼──────────────┐
              │ 1. 解析命令行参数 / 环境变量  │
              └──────────────┬──────────────┘
                             │
              ┌──────────────▼──────────────┐
              │ 2. 确定 CoreRoot（数据目录） │
              └──────────────┬──────────────┘
                             │
              ┌──────────────▼──────────────────────┐
              │ 3. 构建 Tokio Runtime               │
              │    (worker_threads = 配置值/自动)   │
              └──────────────┬──────────────────────┘
                             │
              ┌──────────────▼──────────────┐
              │ 4. 初始化日志系统             │
              │    tracing_subscriber init   │
              └──────────────┬──────────────┘
                             │
              ┌──────────────▼──────────────┐
              │ 5. 加载并解析配置文件         │
              │    core.json                 │
              └──────────────┬──────────────┘
                             │
              ┌──────────────▼──────────────┐
              │ 6. 初始化 SecretVault         │
              │    (注入启动密钥)             │
              └──────────────┬──────────────┘
                             │
              ┌──────────────▼──────────────┐
              │ 7. 初始化 PluginManager      │
              │    (创建模块加载器)           │
              └──────────────┬──────────────┘
                             │
              ┌──────────────▼──────────────┐
              │ 8. 加载模块                   │
              │    config/modules/ 目录中配置的模块  │
              │    对每个模块执行:            │
              │      dlopen → verify →      │
              │      init() → start()       │
              └──────────────┬──────────────┘
                             │
              ┌──────────────▼──────────────────────────┐
              │ 9. 启动 WebSocket 服务器                   │
              │    Router::new()                       │
              │    -> route("/ws", ws_handler)          │
              └──────────────┬──────────────────────────┘
                             │
              ┌──────────────▼──────────────┐
              │ 10. 注册优雅关闭处理器         │
              │     (Ctrl+C / SIGTERM)       │
              └──────────────┬──────────────┘
                             │
                             ▼
                    ==== Core RUNNING ====
```

**各阶段错误处理：**

- 阶段 1-3 失败 → 输出错误到 stderr，进程退出（退出码 1）
- 阶段 4 失败 → 日志文件无法初始化，使用 stderr 兜底，进程继续
- 阶段 5 失败 → 配置缺失/解析错误，进程退出（退出码 2）
- 阶段 7 失败 → PluginManager 初始化失败，进程退出（退出码 3）
- 阶段 8 失败 → 单个模块失败不影响其他模块，记录错误继续
- 阶段 9 失败 → HTTP 监听失败，进程退出（退出码 4）

### 7.2 优雅关闭

Core 监听 `SIGINT`（Ctrl+C）和 `SIGTERM`（kill）信号，触发优雅关闭流程。

```rust
use tokio::signal;

async fn graceful_shutdown_signal() {
    signal::ctrl_c().await.expect("Failed to listen for Ctrl+C");
    tracing::info!(target: "cloudfin::core", "Received SIGINT, initiating shutdown");
    initiate_shutdown().await;
}

async fn initiate_shutdown() {
    // 1. 停止接收新连接
    shutdown_http_server().await;

    // 2. 广播关闭通知到所有 WebSocket 客户端
    broadcast_shutdown_event().await;

    // 3. 通知所有模块优雅停止
    let modules = plugin_manager.get_all_modules();
    for module in modules {
        let _ = module.stop().await;
    }

    // 4. 等待模块停止完成（超时 30 秒）
    let timeout = tokio::time::Duration::from_secs(30);
    let _ = tokio::time::timeout(timeout, wait_all_modules_stopped()).await;

    // 5. 停止 Tokio Runtime
    tracing::info!(target: "cloudfin::core", "Shutdown complete");
    std::process::exit(0);
}
```

**关闭超时：** 所有模块停止操作共享 30 秒超时窗口。超时后强制终止（模块层面无 cleanup）。

### 7.3 进程守护

Core 以后台守护进程（daemon）方式运行。

**启动参数：**

```bash
cloudfin-core run --daemon
cloudfin-core run --foreground  # 开发调试用
cloudfin-core stop              # 停止守护进程
cloudfin-core restart           # 重启
```

**Daemon 实现（`daemonize.rs`）：**

```rust
pub fn daemonize() -> Result<()> {
    // 1. fork 第一次（创建后台进程）
    match unsafe { fork() } {
        Ok(ForkResult::Parent { .. }) => {
            // 父进程输出 PID 后退出
            println!("Cloudfin Core started with PID: {}", child_pid);
            std::process::exit(0);
        }
        Ok(ForkResult::Child) => {
            // 子进程继续
        }
        Err(_) => return Err(DaemonError::ForkFailed),
    }

    // 2. 创建新会话（setsid）
    setsid().map_err(|_| DaemonError::SessionFailed)?;

    // 3. fork 第二次（防止获得控制终端）
    match unsafe { fork() } {
        Ok(ForkResult::Parent { .. }) => std::process::exit(0),
        Ok(ForkResult::Child) => {},
        Err(_) => return Err(DaemonError::ForkFailed),
    }

    // 4. 重定向标准 I/O 到 /dev/null
    let fd_null = open("/dev/null", O_RDWR).map_err(|_| DaemonError::IoRedirect)?;
    dup2(fd_null, 0)?; // stdin
    dup2(fd_null, 1)?; // stdout
    dup2(fd_null, 2)?; // stderr

    // 5. 改变工作目录到 CoreRoot
    chdir(std::env::var("CLOUDFIN_ROOT").unwrap().as_str())?;

    Ok(())
}
```

**PID 文件：** 守护进程 PID 存储在 `{CoreRoot}/Cloudfin/cloudfin.pid`。

```
{CoreRoot}/Cloudfin/
├── cloudfin.pid    # 进程 PID
└── ...
```

**停止守护进程（`cloudfin-core stop`）：**

```bash
kill $(cat {CoreRoot}/Cloudfin/cloudfin.pid)
```

---

## 附录 A：目录结构

```
{CoreRoot}/Cloudfin/
├── cloudfin-core          # 可执行文件
├── config/
│   ├── core.json          # Core 主配置
│   └── modules/           # 模块配置目录（每个模块一个 .json）
│       ├── p2p.json
│       └── crdt.json
├── modules/               # 模块文件目录（.so 和 .so.json 同目录）
│   ├── CloudFin-Mod-Linux-amd64-P2P-v20260405-001.so
│   ├── CloudFin-Mod-Linux-amd64-P2P-v20260405-001.so.json
│   ├── CloudFin-Mod-Linux-amd64-CRDT-v20260405-001.so
│   └── CloudFin-Mod-Linux-amd64-CRDT-v20260405-001.so.json
├── logs/
│   ├── cloudfin.log
│   └── cloudfin.log.2026-04-04
├── cloudfin.pid
└── data/                  # 模块运行时数据
```

## 附录 B：模块接口定义

```rust
// cloudfin_module_api.h (C 兼容接口)
typedef struct {
    int api_version;  // 必须是 1
    const char* (*name)();
    const char* (*version)();
    void* (*init)(const char* json_config, char** error_out);
    int (*start)(void* ctx);
    int (*stop)(void* ctx);
    int (*health_check)(void* ctx);
} CloudfinModuleApi;
```

## 附录 C：tracing 日志字段约定

| 字段名 | 说明 |
|--------|------|
| `module` | 模块标识符（`core` 或 `模块名`） |
| `request_id` | 请求追踪 ID（若存在） |
| `duration_ms` | 操作耗时（毫秒） |
| `error` | 错误描述（若存在） |

---

*本文件为 Cloudfin Core 技术规格的 L1 路线级文档，所有实现细节以代码为准。*
