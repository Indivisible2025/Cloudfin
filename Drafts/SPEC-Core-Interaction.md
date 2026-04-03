# Cloudfin Core 交互逻辑规格

> 版本：v0.1.0-draft  
> 日期：2026-04-03  
> 状态：**草案 — 待审查**  
> 依赖：SPEC-Core.md、SPEC-Core-API.md、SPEC-Plugin-Interface.md

---

## 1. 概述

### 1.1 文档目的

本文档定义 **Cloudfin Core** 与三类客户端的完整交互逻辑：
- **CLI**（命令行工具 cloudfinctl）
- **UI**（各平台原生 App）
- **Module**（插件模块）

### 1.2 交互全景图

```
                    ┌─────────────────────────────────────────┐
                    │            Cloudfin Core                 │
                    │                                         │
                    │   ┌─────────────────────────────────┐   │
                    │   │         WebSocket Server          │   │
                    │   │         ws://localhost:19001     │   │
                    │   └─────────────────────────────────┘   │
                    │              │                           │
                    │   ┌───────────┼───────────┐             │
                    │   │           │           │             │
                    │   ▼           ▼           ▼             │
                    │ ┌─────┐   ┌─────┐   ┌─────────┐        │
                    │ │ CLI │   │ UI  │   │ Module  │        │
                    │ └─────┘   └─────┘   └─────────┘        │
                    │                                         │
                    │   ┌─────────────────────────────────┐   │
                    │   │       ModuleLoader               │   │
                    │   │   （动态加载 / 卸载模块）          │   │
                    │   └─────────────────────────────────┘   │
                    └─────────────────────────────────────────┘

桌面端（Linux/macOS/Windows）：
  cloudfinctl status                    ──WebSocket──▶ Core
  cloudfinctl modules load tor         ──WebSocket──▶ Core
  App（原生窗口）                      ──WebSocket──▶ Core

移动端（Android/iOS）：
  App（Kotlin/Swift）                 ──WebSocket──▶ Core

模块（任意平台）：
  Module（Rust .so）                  ──内部调用──▶ Core
```

---

## 2. 连接建立流程

### 2.1 通用连接流程（所有客户端）

```
客户端                        Core
   │                            │
   │  ──TCP 连接─────────────▶  │
   │  ──WebSocket 握手────────▶  │
   │                            │
   │  ◀──HELLO───────────────  │  Core 发送自身信息
   │                            │
   │  ──AUTH─────────────────▶  │  客户端认证（可选）
   │                            │
   │  ◀──AUTH_OK / AUTH_FAIL──  │  认证结果
   │                            │
   │  ──REQUEST───────────────▶  │  开始请求
   │  ◀──RESPONSE────────────   │
   │                            │
   │  ◀──EVENT────────────────  │  接收事件推送
   │                            │
   │  ──REQUEST───────────────▶  │  继续请求...
   │                            │
   │  ◀──EVENT────────────────  │  事件持续推送
   │                            │
   │  ──CLOSE─────────────────▶  │  客户端主动关闭
   │  ◀──CLOSE────────────────  │
```

### 2.2 HELLO 消息（Core → 客户端）

**Core 发送**：
```json
{
  "type": "HELLO",
  "version": "0.1.0",
  "coreApiVersion": "1.0",
  "capabilities": ["core", "modules", "events"],
  "supportedFeatures": ["p2p", "tor", "i2p", "crdt", "crypto", "storage"],
  "maxModules": 10,
  "serverTime": "2026-04-03T19:00:00Z"
}
```

### 2.3 AUTH 消息（客户端 → Core）

**认证方式一：匿名（默认）**
```json
{
  "type": "AUTH",
  "authType": "anonymous",
  "clientType": "cli" | "ui" | "module"
}
```

**认证方式二：Token**
```json
{
  "type": "AUTH",
  "authType": "token",
  "token": "user_provided_token",
  "clientType": "ui"
}
```

**AUTH_OK 响应**：
```json
{
  "type": "AUTH_OK",
  "sessionId": "sess_abc123",
  "expiresAt": "2026-04-04T19:00:00Z"
}
```

**AUTH_FAIL 响应**：
```json
{
  "type": "AUTH_FAIL",
  "error": "INVALID_TOKEN",
  "message": "Token 无效或已过期"
}
```

---

## 3. CLI 交互逻辑

### 3.1 CLI 设计原则

| 原则 | 说明 |
|------|------|
| **无状态** | 每次命令独立连接，完成后断开 |
| **人类可读** | 输出格式适合 Terminal 阅读 |
| **可脚本化** | 支持 `--json` 输出格式供脚本使用 |
| **进度反馈** | 长时间操作显示进度条 |

### 3.2 命令行接口

```bash
cloudfinctl [全局选项] <命令> [参数]

# 全局选项
  --host <ADDR>      连接地址（默认 localhost:19001）
  --unix <PATH>      Unix Socket 路径
  --json             JSON 输出格式
  --no-color         不使用颜色
  --quiet            静默模式（只输出错误）
  --verbose          详细输出
  --help             显示帮助
  --version          显示版本

# 命令
  info               查看 Core 信息
  status             查看 Core 状态
  modules            列出模块
  modules load <ID>  加载模块
  modules unload <ID>  卸载模块
  modules call <ID> <METHOD> [ARGS]  调用模块方法
  config get [KEY]   获取配置
  config set <KEY> <VALUE>  设置配置
  sessions           列出会话
  logs [OPTIONS]     查看日志
  shell              交互模式
  shutdown           关闭 Core
```

### 3.3 典型交互流程

#### 3.3.1 查看 Core 状态

```bash
$ cloudfinctl status

Cloudfin Core v0.1.0
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
状态：运行中
运行时间：2 小时 34 分钟
连接数：3（UI: 2, CLI: 1）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
已加载模块：
  ✓ p2p    v0.1.0  运行中
  ✓ tor    v0.1.0  运行中
  ○ i2p    v0.1.0  已停止
  ○ crdt   v0.1.0  已停止
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

#### 3.3.2 加载模块

```bash
$ cloudfinctl modules load tor

正在加载 tor v0.1.0...
✓ 模块已加载：tor
✓ 模块已启动
  状态：运行中
  监听端口：9050 (SOCKS5)
  控制端口：9051
```

#### 3.3.3 调用模块方法

```bash
$ cloudfinctl modules call p2p connect '{"address": "/ip4/1.2.3.4/tcp/9000"}'

正在调用 p2p.connect...
✓ 成功
  peerId: QmXp7s9Y2Hjk3L4m5n6O7P8q
  节点数: 1
```

#### 3.3.4 交互模式

```bash
$ cloudfinctl shell

Cloudfin Shell v0.1.0
输入 'help' 查看可用命令，输入 'exit' 退出。

cloudfin> status
Core 状态：运行中

cloudfin> modules list
  p2p   运行中
  tor    运行中

cloudfin> modules load i2p
✓ i2p 已加载

cloudfin> exit
再见！
```

### 3.4 错误处理

| 错误码 | 说明 | CLI 输出 |
|--------|------|---------|
| `CORE_NOT_RUNNING` | Core 未运行 | `错误：无法连接到 Cloudfin Core。请确保 Core 正在运行。` |
| `MODULE_NOT_FOUND` | 模块不存在 | `错误：模块 'xxx' 不存在。` |
| `MODULE_ALREADY_LOADED` | 模块已加载 | `模块 'xxx' 已经加载。` |
| `METHOD_NOT_FOUND` | 方法不存在 | `错误：模块 'xxx' 没有方法 'yyy'。` |
| `CALL_TIMEOUT` | 调用超时 | `错误：调用超时（5秒）。` |
| `INVALID_ARGS` | 参数错误 | `错误：参数格式错误。` |

### 3.5 脚本化支持

```bash
# 使用 --json 输出供脚本解析
$ cloudfinctl status --json
{
  "status": "running",
  "uptime": 9240,
  "modules": {
    "p2p": {"status": "running"},
    "tor": {"status": "running"}
  }
}

# 检查 Core 是否运行（用于 systemd 等）
$ cloudfinctl status --quiet && echo "Core 运行中" || echo "Core 未运行"
Core 运行中
```

---

## 4. UI 交互逻辑

### 4.1 UI 连接策略

| 场景 | 策略 |
|------|------|
| App 启动 | 立即连接 Core，显示加载动画 |
| Core 未运行 | 显示"启动 Core"引导 |
| 连接断开 | 自动重连（最多 5 次，指数退避）|
| App 退后台 | 保持连接，接收事件推送 |
| App 退出 | 优雅关闭连接 |

### 4.2 UI 连接流程

```
App 启动
    │
    ▼
检查 Core 是否运行（ping）
    │
    ├── Core 未运行 ──▶ 显示引导页
    │   ├─ 提示用户安装/启动 Core
    │   └─ 提供"启动 Core"按钮
    │
    └── Core 运行中 ──▶ 建立 WebSocket 连接
            │
            ▼
        接收 HELLO
            │
            ▼
        发送 AUTH
            │
            ▼
        等待 AUTH_OK
            │
            ▼
        发送 modules.list 请求
            │
            ▼
        接收模块列表，更新 UI
            │
            ▼
        进入主界面
```

### 4.3 UI 状态同步

UI 必须实时同步 Core 和模块的状态。

#### 4.3.1 启动时获取完整状态

```json
UI ──▶ Core
REQUEST {
  "action": "modules.list"
}

Core ──▶ UI
RESPONSE {
  "core": { "version": "0.1.0", ... },
  "modules": [ ... 完整模块列表 ... ]
}
```

#### 4.3.2 订阅状态变更事件

```json
UI ──▶ Core
REQUEST {
  "action": "events.subscribe",
  "payload": {
    "events": [
      "core.status_changed",
      "module.status_changed",
      "module.loaded",
      "module.unloaded",
      "p2p.peer_connected",
      "p2p.peer_disconnected"
    ]
  }
}
```

#### 4.3.3 接收事件推送

```json
Core ──▶ UI
EVENT {
  "type": "module.status_changed",
  "data": {
    "moduleId": "p2p",
    "oldStatus": "running",
    "newStatus": "error",
    "error": "Connection refused"
  },
  "timestamp": "2026-04-03T19:05:00Z"
}
```

### 4.4 UI 生命周期交互

```
┌─────────────────────────────────────────────────────────┐
│                     UI App 生命周期                      │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  [启动]                                                  │
│    │                                                    │
│    ▼                                                    │
│  [连接 Core] ──▶ [获取模块列表] ──▶ [订阅事件]           │
│    │                                                    │
│    ▼                                                    │
│  [主界面 - 模块状态卡片]                                  │
│    │                                                    │
│    ├── 用户点击"连接" ──▶ UI 发送 modules.load ──▶ 等待 EVENT │
│    │                                                    │
│    ├── 用户点击"断开" ──▶ UI 发送 modules.unload ──▶ 等待 EVENT │
│    │                                                    │
│    └── Core 推送 EVENT ──▶ UI 自动更新卡片状态            │
│                                                          │
│  [App 退后台]                                           │
│    │                                                    │
│    ▼                                                    │
│  [保持连接，接收事件]                                     │
│    │                                                    │
│  [App 回到前台]                                         │
│    │                                                    │
│    ▼                                                    │
│  [发送 core.status 刷新状态]                             │
│                                                          │
│  [App 退出]                                             │
│    │                                                    │
│    ▼                                                    │
│  [发送 CLOSE，优雅断开]                                  │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 4.5 UI 错误处理

| 场景 | UI 行为 |
|------|---------|
| 连接失败 | 显示错误对话框，提供"重试"按钮 |
| 请求超时 | 显示 toast "请求超时"，自动重试（最多 3 次）|
| 模块加载失败 | 在模块卡片上显示错误图标，点击查看详情 |
| Core 关闭 | 显示全屏提示"Core 已停止"，提供"重新连接"按钮 |
| 网络断开 | 显示离线横幅，自动重连 |

---

## 5. 模块交互逻辑

### 5.1 模块生命周期

```
模块 .so 文件
      │
      ▼
Core 扫描 modules/ 目录
      │
      ▼
发现 manifest.json，解析元数据
      │
      ▼
验证 coreMinVersion / 平台兼容性
      │
      ├─ 不兼容 ──▶ 跳过并记录日志
      │
      └─ 兼容 ──▶ libloading 加载 .so
                    │
                    ▼
              调用 cloudfin_module_new()
                    │
                    ▼
              调用 module.init(config)
                    │
                    ▼
              注册到 ModuleRegistry
                    │
                    ▼
              等待 modules.load 调用
                    │
                    ▼
            用户/UI 调用 modules.load
                    │
                    ▼
              调用 module.start()
                    │
                    ▼
              模块进入 running 状态
                    │
                    ▼
              发送 module.loaded 事件
                    │
                    ▼
            运行中，接收 modules.call
                    │
                    ▼
            用户/UI 调用 modules.unload
                    │
                    ▼
              调用 module.stop(graceful=true)
                    │
                    ▼
              发送 module.unloaded 事件
                    │
                    ▼
              释放 .so 句柄
```

### 5.2 模块调用模块（Module-to-Module）

**重要约束**：模块之间不能直接通信，必须通过 Core 路由。

```
┌─────────────────────────────────────────────────────────────┐
│                     模块间调用流程                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  P2P 模块                           Tor 模块                │
│      │                                  │                    │
│      │  modules.call("tor", "proxy",    │                    │
│      │      {"target": "..."})          │                    │
│      │──────────▶ Core ◀───────────────  │                    │
│                 │                                         │
│                 │ 1. 验证调用权限（manifest.permissions）    │
│                 │ 2. 获取 Tor 模块锁                       │
│                 │ 3. 调用 Tor.proxy()                       │
│                 │ 4. 返回结果                               │
│                 │                                          │
│      ◀──────────┴───────────────────────  │                  │
│      │                                           │           │
│      ▼                                           ▼           │
│  处理 P2P 请求                                   执行代理      │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**manifest.json 中的调用权限**：
```json
{
  "permissions": {
    "canCall": ["tor", "i2p", "crypto"],
    "canSubscribe": ["tor", "i2p"],
    "canAccessConfig": false
  }
}
```

### 5.3 模块事件订阅

模块可以订阅其他模块的事件。

```rust
// 模块内部
pub struct P2PModule {
    event_tx: broadcast::Sender<ModuleEvent>,
}

impl Module for P2PModule {
    fn on_event(&self, event: &CoreEvent) {
        match event {
            CoreEvent::ModuleStatusChanged { module_id, status } => {
                // 收到 Tor 状态变更事件
                if module_id == "tor" && status == "running" {
                    // 可以使用 Tor 代理了
                }
            }
            _ => {}
        }
    }
}
```

### 5.4 模块注册的方法

模块注册到 Core 的方法列表（供 UI 生成操作界面）：

```json
{
  "moduleId": "p2p",
  "methods": [
    {
      "name": "connect",
      "description": "连接到 P2P 节点",
      "args": [
        {"name": "address", "type": "string", "required": true}
      ]
    },
    {
      "name": "disconnect",
      "description": "断开连接",
      "args": []
    },
    {
      "name": "get_peers",
      "description": "获取已连接节点列表",
      "args": []
    }
  ]
}
```

---

## 6. 通用交互模式

### 6.1 请求-响应模式

所有 API 调用都是请求-响应模式：

```json
UI ──▶ Core
REQUEST {
  "id": "msg_000001",
  "type": "REQUEST",
  "action": "modules.load",
  "payload": {
    "moduleId": "tor"
  }
}

Core ──▶ UI
RESPONSE {
  "id": "msg_000001",
  "type": "RESPONSE",
  "result": {
    "moduleId": "tor",
    "status": "running"
  }
}
```

### 6.2 错误响应格式

```json
{
  "id": "msg_000001",
  "type": "RESPONSE",
  "error": {
    "code": "MODULE_NOT_FOUND",
    "message": "模块 'xxx' 不存在",
    "details": {}
  }
}
```

### 6.3 事件推送格式

```json
{
  "type": "EVENT",
  "event": "module.status_changed",
  "data": { ... },
  "timestamp": "2026-04-03T19:00:00Z"
}
```

### 6.4 心跳保活

```json
Core ──▶ UI
PING {}

UI ──▶ Core
PONG {}
```

- Core 每 30 秒发送一次 PING
- UI 必须在 10 秒内回复 PONG
- 超时则认为连接断开

---

## 7. 并发处理

### 7.1 多客户端并发

```
WebSocket 连接 1 (CLI) ──▶ Session 1 ──▶ Request A
                                          │
WebSocket 连接 2 (UI)  ──▶ Session 2 ──▶ Request B
                                          │
WebSocket 连接 3 (Module) ──▶ Session 3 ──▶ Request C
```

Core 必须能处理：
- 多个 CLI 同时连接
- 多个 UI 同时连接
- 模块加载/卸载与请求处理并发

### 7.2 模块并发调用

```
Session A ──▶ modules.call("p2p", "connect", {...})
                  │
                  ├─ P2P 模块锁
                  │
Session B ──▶ modules.call("p2p", "get_peers", {...})
                  │
                  ├─ P2P 模块锁（等待）
                  │
Session C ──▶ modules.call("tor", "proxy", {...})
                  │
                  ├─ Tor 模块锁（不阻塞 P2P）
                  │
```

**设计原则**：
- 每个模块有独立的锁
- 同一模块的多个调用排队处理
- 不同模块之间互不阻塞

### 7.3 模块间调用并发

```
P2P ──▶ modules.call("tor", "proxy", {...})
              │
              │ Core 获取 Tor 模块锁
              │
Tor ──▶ modules.call("p2p", "get_peers", {...})
              │
              │ Core 获取 P2P 模块锁（等待 P2P 完成）
              │
```

**防止死锁策略**：
- 模块调用模块时，按模块 ID 字母顺序获取锁
- 或者：模块调用模块时不加锁（模块内部保证线程安全）

---

## 8. 会话管理

### 8.1 会话创建

每次 WebSocket 连接建立时创建新会话：

```rust
pub struct Session {
    pub id: SessionId,          // 唯一标识
    pub client_type: ClientType, // cli | ui | module
    pub client_version: String, // 客户端版本
    pub platform: String,        // android | ios | linux | ...
    pub connected_at: DateTime<Utc>,
    pub last_active: DateTime<Utc>,
    pub subscriptions: Vec<String>,  // 订阅的事件类型
    pub auth_type: AuthType,
}
```

### 8.2 会话清理

| 触发条件 | 清理时机 |
|---------|---------|
| 客户端主动 CLOSE | 立即清理 |
| 心跳超时 | 60 秒后清理 |
| Core 关闭 | 全部清理 |

---

## 9. 交互流程总结

### 9.1 CLI 完整交互

```
启动 cloudfinctl
    │
    ▼
解析命令
    │
    ├── help/version ──▶ 显示信息，退出
    │
    ├── status ──▶ 连接 Core → 请求 core.status → 格式化输出 → 断开
    │
    ├── modules load xxx ──▶ 连接 Core → 请求 modules.load → 格式化输出 → 断开
    │
    ├── shell ──▶ 连接 Core → 循环读取命令 → 请求 → 格式化输出 → exit 时断开
    │
    └── shutdown ──▶ 连接 Core → 请求 core.shutdown → 等待结果 → 断开
```

### 9.2 UI 完整交互

```
启动 App
    │
    ▼
连接 Core（WebSocket）
    │
    ▼
HELLO ← → AUTH
    │
    ▼
请求 modules.list → 获取完整状态
    │
    ▼
请求 events.subscribe → 订阅事件
    │
    ▼
进入主界面，显示模块卡片
    │
    ├── 用户操作 → 发送请求 → 等待响应 → 更新 UI
    │
    └── 收到 EVENT → 更新对应模块卡片状态
```

### 9.3 Module 完整交互

```
Core 启动
    │
    ▼
扫描 modules/ 目录
    │
    ▼
加载 .so，调用 init()
    │
    ▼
注册到 ModuleRegistry
    │
    ▼
等待 modules.load
    │
    ▼
调用 start() → 模块开始工作
    │
    ▼
接收 modules.call → 处理请求
    │
    ├── 可发送 EVENT 通知 Core
    │
    └── 可调用其他模块（通过 Core）
```

---

## 10. 审查清单

```
□ CLI 命令设计完整
□ CLI 错误处理完整
□ CLI 脚本化支持
□ UI 连接流程完整
□ UI 状态同步机制完整
□ UI 生命周期处理完整
□ 模块生命周期完整
□ 模块间调用机制完整
□ 通用请求-响应格式
□ 事件推送格式
□ 心跳保活机制
□ 多客户端并发
□ 模块并发调用
□ 会话管理
□ 错误码规范
```

---

*本文档由 墨尘 🌙 编写*  
*最后更新：2026-04-03*
