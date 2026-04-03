# Cloudfin Core 技术规格

> 版本：v0.1.0-draft  
> 日期：2026-04-03  
> 状态：**草案 — 待审查**  
> 协议：WebSocket + JSON（见 SPEC-Core-API.md）

---

## 1. 概述

### 1.1 什么是 Cloudfin Core

Cloudfin Core 是整个框架的核心守护进程，**无 UI，纯 API/CLI 控制**，类比 Redis、cURL、无头浏览器。

**职责**：
- 模块加载与生命周期管理
- 配置存储与持久化
- 会话管理（WebSocket 连接追踪）
- API 路由（接收请求 → 分发到模块 → 返回响应）
- 事件收集与推送
- 日志记录

**不负责**：
- UI 渲染
- 平台特定功能
- 用户交互逻辑

### 1.2 技术栈

| 组件 | 技术选型 | 理由 |
|------|---------|------|
| 语言 | Rust | 高性能、安全、跨平台、.so 输出成熟 |
| 异步运行时 | tokio | 最成熟的 Rust 异步生态 |
| WebSocket | tokio-tungstenite / axum::WebSocket | 与 HTTP Server 共用端口 |
| 配置存储 | serde_json | JSON 格式配置，与 API 协议一致 |
| 日志 | tracing + tracing-subscriber | 结构化日志，支持多输出 |
| 插件加载 | libloading | Rust 官方动态库加载 |

### 1.3 项目结构

```
Cloudfin-Core/
├── src/
│   ├── main.rs              # 入口，命令行参数解析
│   ├── lib.rs                # 库入口
│   │
│   ├── core/
│   │   ├── mod.rs            # Core 主模块
│   │   ├── state.rs          # CoreState（全局状态）
│   │   ├── session.rs        # 会话管理
│   │   ├── config.rs         # 配置加载/保存
│   │   └── router.rs         # API 路由分发
│   │
│   ├── api/
│   │   ├── mod.rs            # API 模块入口
│   │   ├── ws_handler.rs     # WebSocket 处理
│   │   ├── messages.rs       # 消息序列化/反序列化
│   │   └── codec.rs          # JSON 编解码
│   │
│   ├── modules/
│   │   ├── mod.rs            # ModuleLoader
│   │   ├── registry.rs       # 模块注册表
│   │   ├── loader.rs         # .so/.dylib 动态加载
│   │   └── loader_tokio.rs   # 异步模块加载任务
│   │
│   ├── modules_builtin/
│   │   ├── mod.rs            # 内置模块（p2p/tor/i2p/crdt）
│   │   └── dummy.rs          # 空模块占位，用于测试
│   │
│   └── cli/
│       ├── mod.rs            # CLI 入口
│       └── commands.rs       # cloudfinctl 子命令
│
├── Cargo.toml
├── build.rs                  # 平台检测、动态库路径
└── README.md
```

### 1.4 进程模型

```
单进程多线程模型：

主线程：
  tokio runtime（单线程或多线程）
  ├─ WebSocket acceptor（:19001）
  ├─ Module loader task
  ├─ Config watcher task
  └─ Session manager task

模块线程（按需）：
  └─ 各模块独立任务（模块内部自己管理线程）
```

---

## 2. CoreState 全局状态

### 2.1 CoreState 结构

```rust
pub struct CoreState {
    // 元数据
    pub version: Version,
    pub started_at: DateTime<Utc>,
    pub commit: String,

    // 配置
    pub config: RwLock<Config>,
    pub config_path: PathBuf,

    // 模块管理
    pub module_loader: ModuleLoader,
    pub module_registry: RwLock<ModuleRegistry>,

    // 会话管理
    pub sessions: RwLock<HashMap<SessionId, Session>>,

    // 事件通道（模块 → Core → WebSocket）
    pub event_tx: broadcast::Sender<CoreEvent>,
    pub event_rx: broadcast::Receiver<CoreEvent>,

    // 日志
    pub log_level: RwLock<LevelFilter>,
}
```

### 2.2 启动流程

```
main()
  │
  ├─ 解析命令行参数（--config, --port, --log-level, --help）
  │
  ├─ 加载配置（config.json）
  │   └─ 不存在？生成默认配置
  │
  ├─ 初始化 tracing（日志输出到 stderr + 文件）
  │
  ├─ 初始化 ModuleLoader（扫描 modules/ 目录）
  │
  ├─ 初始化 SessionManager
  │
  ├─ 启动 WebSocket Acceptor（:19001）
  │
  ├─ 注册 Core API 路由
  │
  ├─ 注册内置模块
  │
  ├─ 加载上次运行的模块（config.startupModule）
  │
  ├─ 发送 core.started 事件
  │
  └─ 进入主循环（tokio::spawn + await）
```

---

## 3. 配置管理

### 3.1 配置文件路径

| 平台 | 路径 |
|------|------|
| Linux | `~/.config/cloudfin/config.json` |
| macOS | `~/Library/Application Support/Cloudfin/config.json` |
| Windows | `%APPDATA%\Cloudfin\config.json` |
| Android | `/data/data/com.cloudfin/files/config.json` |

### 3.2 配置结构

```rust
#[derive(Serialize, Deserialize)]
pub struct Config {
    pub core: CoreConfig,
    pub network: NetworkConfig,
    pub modules: ModulesConfig,
    pub security: SecurityConfig,
}

#[derive(Serialize, Deserialize)]
pub struct CoreConfig {
    pub version: String,           // 配置文件版本
    pub language: String,          // "zh-CN" | "en-US"
    pub theme: String,            // "dark" | "light" | "system"
    pub startup_module: Vec<ModuleId>,  // 开机自启模块
    pub log_level: String,        // "error" | "warn" | "info" | "debug" | "trace"
}

#[derive(Serialize, Deserialize)]
pub struct NetworkConfig {
    pub listen: String,           // "localhost:19001"
    pub unix_socket: Option<String>,  // "/run/cloudfin.sock"
    pub max_connections: usize,   // 默认 10
    pub ping_interval: u64,       // 秒，默认 30
}

#[derive(Serialize, Deserialize)]
pub struct ModulesConfig {
    pub p2p: ModuleConfig,
    pub tor: ModuleConfig,
    pub i2p: ModuleConfig,
    pub crdt: ModuleConfig,
}

#[derive(Serialize, Deserialize)]
pub struct ModuleConfig {
    pub enabled: bool,
    pub config: serde_json::Value,  // 模块特定配置
}

#[derive(Serialize, Deserialize)]
pub struct SecurityConfig {
    pub device_id: String,
    pub device_key_hash: String,   // bcrypt 哈希后的密钥
}
```

### 3.3 配置加载

```
启动时：
  1. 尝试读取 config_path
  2. 存在 → 解析 JSON → 校验版本 → 合并默认配置
  3. 不存在 → 创建默认配置 → 写入磁盘
  4. 监听文件变化（inotify / FSEvents）→ 热重载
```

### 3.4 配置保存

- 每次 config.set 成功后，同步写入磁盘
- 使用临时文件 + rename 保证原子性（防止写入中途崩溃）

---

## 4. 模块系统

### 4.1 ModuleLoader

```rust
pub struct ModuleLoader {
    modules_dir: PathBuf,        // 动态库目录
    loaded: RwLock<HashMap<ModuleId, LoadedModule>>,
}

pub struct LoadedModule {
    pub id: ModuleId,
    pub path: PathBuf,
    pub library: libloading::Library,  // 动态库句柄
    pub api: ModuleApi,         // 模块导出接口
    pub status: ModuleStatus,   // loaded | running | stopped | error
    pub version: String,
    pub config: serde_json::Value,
}
```

### 4.2 模块 API 接口

每个模块必须实现以下接口（Rust trait）：

```rust
pub trait Module: Send + Sync {
    /// 模块 ID（唯一标识）
    fn id(&self) -> &ModuleId;

    /// 模块名称（人类可读）
    fn name(&self) -> &str;

    /// 模块版本
    fn version(&self) -> &str;

    /// 初始化模块（在 .so 加载后调用一次）
    fn init(&self, config: serde_json::Value) -> Result<(), ModuleError>;

    /// 启动模块（init 后调用，或随时重新启动）
    fn start(&self) -> Result<(), ModuleError>;

    /// 停止模块（等待现有请求处理完成）
    fn stop(&self, graceful: bool, timeout: Duration) -> Result<(), ModuleError>;

    /// 调用模块方法
    fn call(&self, method: &str, args: serde_json::Value) -> Result<serde_json::Value, ModuleError>;

    /// 获取模块状态
    fn status(&self) -> ModuleStatus;

    /// 获取模块信息
    fn info(&self) -> ModuleInfo;

    /// 处理来自 Core 的事件
    fn on_event(&self, event: &CoreEvent);
}
```

### 4.3 模块动态库格式

| 平台 | 格式 | 路径约定 |
|------|------|---------|
| Linux | .so | `modules/libcloudfin_p2p.so` |
| macOS | .dylib | `modules/libcloudfin_p2p.dylib` |
| Windows | .dll | `modules/cloudfin_p2p.dll` |
| Android | .so | `modules/libcloudfin_p2p.so` |

### 4.4 内置模块 vs 外部模块

**内置模块**：
- 编译到 Core 二进制中（`modules_builtin/`）
- 提供最小功能集（p2p / tor / i2p / crdt）
- 与 Core 同时发布

**外部模块**：
- 独立 .so/.dylib 文件，放置在 `modules/` 目录
- 通过 ModuleLoader 动态加载
- 可独立更新/替换
- 遵循 Module trait 接口

### 4.5 模块加载流程

```
modules.load { moduleId: "tor" }
     │
     ├─ 检查模块是否已加载
     │   └─ 已加载 → 返回 MODULE_ALREADY_LOADED
     │
     ├─ 查找动态库文件
     │   ├─ 内置模块 → 直接调用 Module::new()
     │   └─ 外部模块 → libloading::Library::new(path)
     │
     ├─ 获取模块实例（Module::new()）
     │
     ├─ 调用 module.init(config)
     │
     ├─ 调用 module.start()
     │
     ├─ 注册模块方法到 registry
     │
     ├─ 发送 module.loaded 事件
     │
     └─ 返回 { moduleId, status: "running" }
```

### 4.6 模块卸载流程

```
modules.unload { moduleId: "tor", graceful: true, timeout: 5000 }
     │
     ├─ 检查模块是否存在
     │   └─ 不存在 → 返回 MODULE_NOT_FOUND
     │
     ├─ graceful = true？
     │   ├─ 是：等待现有请求处理完成（最多 timeout）
     │   └─ 否：立即中断
     │
     ├─ 调用 module.stop(graceful, timeout)
     │
     ├─ 从 registry 移除模块方法
     │
     ├─ 释放动态库句柄（libloading::Library::drop）
     │
     ├─ 从 loaded 列表移除
     │
     ├─ 发送 module.unloaded 事件
     │
     └─ 返回 { unloaded: true }
```

---

## 5. 会话管理

### 5.1 Session 结构

```rust
pub struct Session {
    pub session_id: SessionId,
    pub session_type: SessionType,  // ui | cli | module
    pub platform: String,            // "android" | "ios" | "linux" | ...
    pub app_version: String,
    pub connected_at: DateTime<Utc>,
    pub last_active: RwLock<DateTime<Utc>>,
    pub remote_addr: String,
    pub websocket: Weak<WebSocket>,   // WebSocket 引用（不阻止关闭）
}
```

### 5.2 Session 生命周期

```
WebSocket 连接建立
     │
     ├─ 创建 Session，生成 session_id
     │
     ├─ 存储到 sessions HashMap
     │
     ├─ 发送 HELLO
     │
     ├─ 等待 AUTH
     │
     ├─ AUTH 成功 → 标记为 authenticated
     │   │
     │   └─ 发送 AUTH_OK
     │       │
     │       └─ 等待请求 / 推送事件
     │
     ├─ AUTH 失败 → 发送 AUTH_FAIL → 关闭连接
     │
     │         │
     │     UI 主动关闭
     │         │
     │     WebSocket CLOSE 帧
     │         │
     │     Core 清理 session
     │
     │     网络中断 / UI Crash
     │         │
     │     TCP 连接断开（心跳超时检测）
     │         │
     │     等待重连（30 秒）
     │         │
     │     无重连 → 清理 session
```

---

## 6. 日志系统

### 6.1 日志格式

结构化 JSON 日志，tracing_subscriber + fmt/json：

```json
{
  "timestamp": "2026-04-03T10:00:00.000Z",
  "level": "INFO",
  "target": "cloudfin_core::modules",
  "message": "module loaded",
  "module_id": "p2p",
  "version": "0.1.0",
  "load_time_ms": 150
}
```

### 6.2 日志输出

| 场景 | 输出 |
|------|------|
| 开发调试 | stderr（彩色格式化）|
| 生产环境 | 文件（`~/.local/share/cloudfin/logs/`，按日期切割）|
| systemd | journald |

### 6.3 日志级别

| 级别 | 说明 |
|------|------|
| ERROR | 模块错误、Core 崩溃 |
| WARN | 配置警告、模块降级 |
| INFO | 启动、模块加载/卸载、连接事件 |
| DEBUG | 详细请求/响应、路由分发 |
| TRACE | JSON 序列化详情、心跳 |

### 6.4 日志轮转

- 按日期切割（每天一个文件）
- 保留最近 7 天日志
- 单文件最大 100MB，超出切割

---

## 7. CLI 工具

### 7.1 cloudfinctl 命令行

```bash
cloudfinctl [OPTIONS] [COMMAND]

OPTIONS:
  --host <HOST>     连接地址（默认 localhost:19001）
  --unix <PATH>     Unix Socket 路径
  --help            帮助
  --version         版本

COMMANDS:
  status            查看 Core 状态
  info              查看 Core 版本信息
  modules           列出模块
  load <MODULE_ID>  加载模块
  unload <MODULE_ID> 卸载模块
  call <MODULE_ID> <METHOD> [ARGS]  调用模块方法
  config get [KEY]  获取配置
  config set <KEY> <VALUE>  设置配置
  sessions          列出会话
  kill <SESSION_ID> 关闭会话
  logs              查看日志
  shell             交互模式
  shutdown          关闭 Core
```

### 7.2 实现方式

cloudfinctl 使用与 UI 相同的 WebSocket 连接，只是请求格式改为命令行参数转换。

```rust
async fn main() {
    let args = Cli::parse();

    // 建立 WebSocket 连接
    let ws = connect(args.host.clone()).await?;

    // 发送命令
    match args.command {
        Command::Status => send_request(&ws, "core.status", {}).await?,
        Command::Modules => send_request(&ws, "modules.list", {}).await?,
        ...
    }
}
```

---

## 8. 编译与发布

### 8.1 编译目标

```bash
# Linux
cargo build --release --target x86_64-unknown-linux-gnu
cargo build --release --target aarch64-unknown-linux-gnu

# macOS
cargo build --release --target x86_64-apple-darwin
cargo build --release --target aarch64-apple-darwin

# Windows
cargo build --release --target x86_64-pc-windows-msvc

# Android（交叉编译）
cargo build --release --target aarch64-linux-android
```

### 8.2 发布包结构

```
Cloudfin-0.1.0-linux-x86_64.tar.gz
├── cloudfin               # Core 主程序
├── cloudfinctl           # CLI 工具
├── modules/
│   ├── libcloudfin_p2p.so
│   ├── libcloudfin_tor.so
│   ├── libcloudfin_i2p.so
│   └── libcloudfin_crdt.so
├── config.json           # 默认配置
├── README.md
└── INSTALL.sh           # 安装脚本
```

### 8.3 版本命名

```
Cloudfin Core 版本号：与 UI 版本同步

格式：x.y.z
- x：大版本，不兼容变更
- y：功能更新，向后兼容
- z：bug 修复

标签：v0.1.0、v0.2.0 ...
```

---

## 9. 状态机

### 9.1 Core 状态

```
[Stopped] ──start──▶ [Running] ──stop──▶ [Stopped]
                        │
                        │ error
                        ▼
                   [Error]
```

### 9.2 模块状态

```
[Stopped] ──load──▶ [Loaded] ──unload──▶ [Stopped]
                      │
                      ├─start──▶ [Running] ──stop──▶ [Loaded]
                      │
                      └─error──▶ [Error]
```

---

## 10. 安全性

### 10.1 模块隔离

- 每个外部模块是独立 .so，共享 Core 进程空间
- 模块之间不能直接访问对方内存
- 模块只能通过 Core API 访问其他模块功能
- 恶意模块可以：消耗 CPU/内存、读写网络、读取配置

### 10.2 模块信任级别

| 模块类型 | 来源 | 信任级别 |
|---------|------|---------|
| 内置模块 | Core 同版本 | 完全信任 |
| 官方模块 | GitHub release | 验证签名 |
| 第三方模块 | 模块市场 | 沙箱隔离 |
| 用户自编译 | 本地 | 沙箱隔离 |

### 10.3 设备认证

- 首次启动时生成 Ed25519 密钥对
- 私钥存储在系统密钥链
- 认证使用密钥签名挑战响应（简单实现）

---

## 11. 性能目标

| 指标 | 目标 |
|------|------|
| Core 启动时间 | < 500ms |
| WebSocket 连接建立 | < 100ms |
| 模块方法调用延迟 | < 50ms（不含模块内部）|
| 并发连接数 | ≥ 50 |
| 内存占用（空载）| < 10MB |
| 内存占用（4 模块加载）| < 50MB |

---

## 12. 审查清单

```
□ CoreState 所有字段已定义
□ 启动流程 7 个步骤完整
□ 配置加载/保存逻辑正确（原子性写入）
□ Module trait 8 个方法完整
□ 模块加载/卸载流程正确
□ Session 生命周期 3 种情况（正常关闭、异常断开、重连）完整
□ 日志格式为 JSON 结构化
□ 日志轮转（按日期 + 7 天保留）
□ cloudfinctl 所有子命令已列出
□ 所有 5 个平台编译目标已列出
□ 发布包结构完整
□ 性能目标 6 个指标已定义
□ Core 状态机 3 状态转换正确
□ 模块状态机 4 状态转换正确
```

---

*本文档由 墨尘 🌙 编写*  
*最后更新：2026-04-03*
