# Cloudfin 模块插件接口规格

> 版本：v0.1.0-draft  
> 日期：2026-04-03  
> 状态：**草案 — 待审查**  
> 依赖：SPEC-Core.md（Core 规格）

---

## 1. 概述

### 1.1 什么是模块插件接口

模块插件接口 = **Core 和模块之间打交道的标准方式**。

Core 是房东，模块是租客。接口 = 租客必须遵守的规矩（做什么、用什么格式、什么时候干什么）。

### 1.2 模块清单（ModuleManifest）

每个模块根目录必须包含 `manifest.json`，用于 Core 识别模块的完整元数据：

```json
{
  "manifestVersion": "1.0",
  "module": {
    "id": "p2p",
    "name": "P2P Network",
    "nameZh": "P2P 网络",
    "version": "0.1.0",
    "description": "P2P peer discovery and connection management",
    "descriptionZh": "P2P 节点发现与连接管理",
    "icon": "modules/icons/p2p.png"
  },
  "developer": {
    "name": "Cloudfin Team",
    "email": "dev@cloudfin.io",
    "website": "https://cloudfin.io",
    "github": "https://github.com/indivisible2025/cloudfin-mod-p2p"
  },
  "license": {
    "spdx": "GPL-3.0",
    "name": "GNU General Public License v3.0",
    "url": "https://www.gnu.org/licenses/gpl-3.0.html",
    "commercial": false
  },
  "links": {
    "source": "https://github.com/indivisible2025/cloudfin-mod-p2p",
    "documentation": "https://docs.cloudfin.io/modules/p2p",
    "bugReport": "https://github.com/indivisible2025/cloudfin-mod-p2p/issues",
    "changelog": "https://github.com/indivisible2025/cloudfin-mod-p2p/blob/main/CHANGELOG.md"
  },
  "compatibility": {
    "coreMinVersion": "0.1.0",
    "coreMaxVersion": "0.9.9",
    "platforms": ["linux", "macos", "android", "windows"]
  },
  "dependencies": {
    "native": [],
    "rust": [],
    "system": []
  },
  "permissions": {
    "canCall": ["tor", "i2p", "crypto"],
    "canSubscribe": ["tor", "i2p"],
    "canAccessConfig": false
  },
  "security": {
    "sandboxed": true,
    "networkAccess": "module_only",
    "fileAccess": "module_private"
  },
  "build": {
    "output": "modules/cloudfin_p2p.so",
    "targets": ["x86_64-unknown-linux-gnu", "aarch64-apple-darwin", "aarch64-linux-android"]
  }
}
```

**字段说明**：

| 字段 | 必填 | 说明 |
|------|------|------|
| `manifestVersion` | ✅ | 清单格式版本，当前 "1.0" |
| `module.id` | ✅ | 模块唯一 ID（小写字母+数字）|
| `module.name` | ✅ | 模块英文名称 |
| `module.nameZh` | ❌ | 模块中文名称 |
| `module.version` | ✅ | 语义化版本 x.y.z |
| `module.description` | ✅ | 英文描述 |
| `module.descriptionZh` | ❌ | 中文描述 |
| `module.icon` | ❌ | 图标路径（相对于模块目录）|
| `developer.name` | ✅ | 开发者名称 |
| `developer.email` | ❌ | 开发者邮箱 |
| `developer.website` | ❌ | 开发者网站 |
| `developer.github` | ❌ | GitHub 地址 |
| `license.spdx` | ✅ | SPDX 许可证标识符 |
| `license.name` | ✅ | 许可证全称 |
| `license.url` | ❌ | 许可证链接 |
| `license.commercial` | ✅ | 是否允许商用 |
| `links.source` | ✅ | 源代码地址 |
| `links.documentation` | ❌ | 文档地址 |
| `links.bugReport` | ❌ | Bug 反馈地址 |
| `links.changelog` | ❌ | 变更日志地址 |
| `compatibility.coreMinVersion` | ✅ | 最低兼容 Core 版本 |
| `compatibility.coreMaxVersion` | ❌ | 最高兼容 Core 版本 |
| `compatibility.platforms` | ✅ | 支持的平台列表 |
| `permissions` | ✅ | 模块权限请求 |
| `security.sandboxed` | ✅ | 是否沙箱隔离 |
| `security.networkAccess` | ✅ | 网络访问范围 |
| `security.fileAccess` | ✅ | 文件访问范围 |
| `build.output` | ✅ | 编译输出路径 |
| `build.targets` | ✅ | 支持的编译目标 |

**license.commercial 含义**：
- `false`：只能免费使用，包括商业项目中的免费使用
- `true`：允许商业使用，需购买授权

**security.networkAccess 选项**：
- `none`：不允许网络访问
- `module_only`：只能使用模块自己声明的端口
- `all`：可以访问任意网络（危险，仅可信模块）

**security.fileAccess 选项**：
- `none`：不允许文件访问
- `module_private`：只能读写模块私有目录
- `module_public`：可以读写模块目录和部分公共目录
- `all`：可以访问任意文件（危险，仅可信模块）|

### 1.3 Core 对清单的识别流程

```
扫描 modules/ 目录
     │
     ├─ 查找 manifest.json
     │   └─ 不存在 → 跳过（不加载）
     │
     ├─ 解析清单
     │   ├─ 校验必填字段
     │   ├─ 检查 coreMinVersion ≤ 当前 Core 版本 ≤ coreMaxVersion
     │   └─ 检查平台兼容性
     │
     ├─ 验证 license
     │   ├─ GPL/AGPL → 标记为传染性许可证
     │   └─ 商业授权 → 标记为专有
     │
     ├─ 验证签名（可选）
     │   └─ 官方模块验证 Ed25519 签名
     │
     └─ 注册模块到 ModuleLoader
         显示：模块名 v版本 | 开发者 | 许可证 | 商用许可
```

### 1.4 设计目标

| 目标 | 说明 |
|------|------|
| **统一** | 所有模块用同一套方式接入 Core，不管内部实现 |
| **隔离** | 模块不能直接操作 Core，只能通过接口 |
| **安全** | 模块能干什么、不能干什么，接口说了算 |
| **可扩展** | 新模块不用改 Core 代码，只要实现接口就行 |

### 1.5 模块分类

| 类型 | 说明 | 示例 |
|------|------|------|
| **内置模块** | 编译到 Core 里的，Core 启动时自动加载 | p2p、tor、i2p、crdt |
| **外部模块** | 独立 .so 文件，放在 modules/ 目录，运行时加载 | crypto、storage 或第三方模块 |

---

## 2. 模块接口（Module Trait）

### 2.1 核心接口定义

所有模块必须实现 `Module` trait（Rust 里的接口概念）：

```rust
// Module trait = 模块必须实现的所有方法
pub trait Module: Send + Sync {
    
    // ---- 基本信息 ----
    
    /// 模块唯一 ID，例如 "p2p"、"tor"、"i2p"
    fn id(&self) -> &ModuleId;
    
    /// 模块名称，人类可读，例如 "P2P Network"
    fn name(&self) -> &str;
    
    /// 模块版本，例如 "0.1.0"
    fn version(&self) -> &str;
    
    // ---- 生命周期 ----
    
    /// 初始化，模块被加载后调用一次
    /// 此时模块还没启动，不能处理业务
    fn init(&self, config: serde_json::Value) -> Result<(), ModuleError>;
    
    /// 启动模块，开始处理业务
    fn start(&self) -> Result<(), ModuleError>;
    
    /// 停止模块，graceful=true 等待现有请求处理完再停
    fn stop(&self, graceful: bool, timeout: Duration) -> Result<(), ModuleError>;
    
    // ---- 调用与查询 ----
    
    /// 调用模块的方法（Core 收到 modules.call 时调用这个）
    fn call(&self, method: &str, args: serde_json::Value) -> Result<serde_json::Value, ModuleError>;
    
    /// 获取模块当前状态
    fn status(&self) -> ModuleStatus;
    
    /// 获取模块详细信息
    fn info(&self) -> ModuleInfo;
    
    // ---- 事件处理 ----
    
    /// Core 推送事件给模块时调用
    fn on_event(&self, event: &CoreEvent);
}
```

### 2.2 术语解释

**trait = 接口**，就像 TypeScript 里的 interface，Java 里的 interface。

```rust
// 举个例子：Rust 里实现这个接口
pub struct P2PModule { /* ... */ }

impl Module for P2PModule {
    fn id(&self) -> &ModuleId { &"p2p".to_string() }
    fn name(&self) -> &str { "P2P Network" }
    fn version(&self) -> &str { "0.1.0" }
    fn init(&self, config) -> Result { Ok(()) }
    fn start(&self) -> Result { Ok(()) }
    fn stop(&self, graceful, timeout) -> Result { Ok(()) }
    fn call(&self, method, args) -> Result { /* 具体实现 */ }
    fn status(&self) -> ModuleStatus { ModuleStatus::Running }
    fn info(&self) -> ModuleInfo { ModuleInfo { ... } }
    fn on_event(&self, event) { /* 处理事件 */ }
}
```

---

## 3. 模块 ID 和命名规范

### 3.1 ID 命名规则

| 规则 | 说明 | 正确示例 | 错误示例 |
|------|------|---------|---------|
| 小写字母 | 全部小写 | `p2p` | `P2P`、`P2p` |
| 字母+数字 | 可以包含数字 | `crdt_v1`、`tor2` | `tor-1`（不能用-）|
| 唯一性 | 全局唯一 | — | 已有 p2p 再加一个 p2p |
| 固定性 | 一旦分配不可更改 | — | 从 p2p 改成 p2p_network |

### 3.2 保留 ID

以下 ID 保留给内置模块：

| ID | 模块名称 | 说明 |
|----|---------|------|
| `p2p` | P2P Network | P2P 节点发现和连接 |
| `tor` | Tor Protocol | Tor 协议封装 |
| `i2p` | I2P Protocol | I2P 协议封装 |
| `crdt` | CRDT Sync | CRDT 同步引擎 |
| `crypto` | Cryptography | 加密和密钥管理 |
| `storage` | Storage | 本地数据持久化 |

### 3.3 外部模块命名

第三方模块建议格式：`org_name.module_name`

| 示例 | 说明 |
|------|------|
| `cloudfin.tor_bridges` | 云鳞官方的 Tor 网桥模块 |
| `cloudfin.vpn` | 云鳞官方的 VPN 模块 |
| `third.wireguard` | 第三方 WireGuard 模块 |

---

## 4. 方法注册表

### 4.1 什么是方法注册表

模块实现了 `call()` 方法，但 Core 需要知道：
- 模块有哪些方法可以调用？
- 方法的参数格式是什么？
- 方法返回什么格式？

**方法注册表** = 模块告诉 Core "我支持这些方法"的机制。

### 4.2 注册表结构

```rust
pub struct MethodRegistry {
    pub methods: HashMap<MethodName, MethodSpec>,
}

pub struct MethodSpec {
    pub name: MethodName,           // "connect"
    pub description: String,       // "连接到 P2P 节点"
    pub args_schema: JsonSchema,    // JSON Schema，描述参数格式
    pub result_schema: JsonSchema,  // JSON Schema，描述返回值格式
    pub errors: Vec<ErrorCode>,     // 可能返回的错误码
}
```

### 4.3 JSON Schema 示例

P2P 模块的 `connect` 方法：

```json
// args_schema（参数格式）
{
  "type": "object",
  "properties": {
    "peerId": { "type": "string", "description": "节点 ID" },
    "address": { "type": "string", "description": "节点地址，格式 /ip4/x/tcp/y" }
  },
  "required": ["peerId", "address"]
}

// result_schema（返回值格式）
{
  "type": "object",
  "properties": {
    "connected": { "type": "boolean" },
    "peerId": { "type": "string" },
    "latency": { "type": "number", "description": "延迟，毫秒" }
  }
}
```

### 4.4 为什么要用 JSON Schema

**好处**：
1. **自动校验**：Core 可以自动检查参数格式是否正确，不用模块自己校验
2. **文档生成**：可以自动从 Schema 生成 API 文档
3. **类型安全**：Core 和模块之间有明确的类型约定

**Core 校验流程**：
```
App 发送 modules.call { method: "connect", args: { peerId: "xxx" } }
         ↓
Core 查找 P2P 模块的 connect 方法的 args_schema
         ↓
校验 args 是否符合 schema
         ↓
  不符合 → 返回 400 BAD_REQUEST
  符合 → 调用模块的 call("connect", args)
         ↓
  返回结果给 App
```

---

## 5. 错误处理

### 5.1 模块错误类型

```rust
pub enum ModuleError {
    // 通用错误（code 400-499）
    BadRequest(String),              // 400：参数错误
    NotFound(String),                // 404：资源不存在
    AlreadyExists(String),           // 409：资源已存在
    PermissionDenied(String),        // 403：无权限
    
    // 模块特定错误（code 500-599）
    Internal(String),                // 500：模块内部错误
    NetworkError(String),            // 501：网络错误
    Timeout(String),                 // 504：操作超时
    NotSupported(String),            // 501：方法不支持
    
    // 致命错误（模块需要卸载）
    Fatal(String),                   // 500：不可恢复的错误
}
```

### 5.2 错误响应格式

模块返回错误时，Core 会转换成统一格式：

```json
{
  "id": "msg_000001",
  "type": "ERROR",
  "action": "modules.call",
  "error": {
    "code": 404,
    "message": "NOT_FOUND",
    "detail": "peer 'node_xxx' not found",
    "moduleError": "PeerNotFound"    // 模块原始错误类型
  }
}
```

### 5.3 错误处理原则

| 原则 | 说明 |
|------|------|
| **不要 panic** | 模块内部不要用 `panic!()`，用 Result 返回错误 |
| **超时必须处理** | 任何可能阻塞的操作都要有超时机制 |
| **资源必须释放** | 发生错误时，确保文件/网络连接/内存正确释放 |
| **错误要具体** | 返回具体错误信息，不要只写 "error" |

---

## 6. 模块生命周期

### 6.1 完整生命周期

```
模块 .so 文件被扫描
         │
         │ 用户触发加载（modules.load）
         ▼
    ┌─────────┐
    │ stopped │ ← 初始状态，模块未初始化
    └────┬────┘
         │ Module::init(config)
         ▼
    ┌─────────┐
    │ stopped │ ← init 完成后（模块已就绪，但未运行）
    └────┬────┘
         │ Module::start()
         ▼
    ┌─────────┐
    │ running │ ← 正常运行，处理业务
    └────┬────┘
         │
    ┌────┴────┐
    │         │
    │   Module::stop(graceful, timeout)
    │         │
    ▼         ▼
   成功      超时/强制
    │         │
    │         ▼
    │    ┌─────────┐
    └────│ stopped │ ← 返回 stopped 状态
         └────┬────┘
              │ 用户触发卸载（modules.unload）
              ▼
         动态库卸载
         内存释放
         模块消失
```

### 6.2 init vs start

```
init = 准备工作
  - 读取配置
  - 初始化内部状态
  - 申请资源（内存、文件句柄）
  - 不建立网络连接

start = 开始干活
  - 建立网络连接
  - 启动后台任务
  - 开始监听事件
  - 可以处理业务请求
```

### 6.3 停止流程详解

```rust
fn stop(&self, graceful: bool, timeout: Duration) {
    if graceful {
        // 1. 停止接受新请求
        self.accepting_new_requests = false;
        
        // 2. 等待现有请求处理完成
        let deadline = Instant::now() + timeout;
        while self.pending_requests > 0 {
            if Instant::now() > deadline {
                // 超时，强制停止
                self.force_stop();
                return;
            }
            sleep(100ms);
        }
        
        // 3. 关闭网络连接
        self.close_connections();
        
        // 4. 停止后台任务
        self.stop_background_tasks();
    } else {
        // 强制停止：立即中断所有操作
        self.force_stop();
    }
}
```

---

## 7. 事件系统

### 7.1 模块 → Core 事件

模块通过事件通道向 Core 发送事件：

```rust
pub trait Module: Send + Sync {
    // 模块内部调用这个发送事件
    fn emit_event(&self, event: ModuleEvent);
}

pub enum ModuleEvent {
    // 模块自己定义的任意事件
    StatusChanged { from: ModuleStatus, to: ModuleStatus },
    Error { code: u16, message: String },
    Custom(String, serde_json::Value),
}
```

### 7.2 Core → 模块事件

Core 向模块推送事件：

```rust
// Core 调用的回调
fn on_event(&self, event: &CoreEvent);

// CoreEvent 定义
pub enum CoreEvent {
    // Core 发给所有模块的通用事件
    CoreShutdown,                     // Core 即将关闭
    ConfigChanged { key: String },     // 配置被修改
    SessionConnected { session_id: SessionId },
    SessionDisconnected { session_id: SessionId },
}
```

### 7.3 事件流向图

```
┌─────────────┐     emit_event()      ┌─────────────┐
│  P2P 模块   │ ──────────────────▶  │   Core      │
│             │ ◀──────────────────  │             │
└─────────────┘     on_event()       └──────┬──────┘
                                            │
                                            │ 广播/路由
                                            ▼
                                     ┌─────────────┐
                                     │   WebSocket  │
                                     │   会话       │
                                     └─────────────┘
```

---

## 8. 模块间通信

### 8.1 为什么模块之间不能直接通信

```
❌ 错误做法：P2P 模块直接调用 Tor 模块
    P2P 模块 ────▶ Tor 模块
         │
         问题：
         - P2P 依赖 Tor 的具体实现
         - 如果 Tor 换了实现方式（比如用不同的 API），P2P 要跟着改
         - 模块之间形成耦合
```

```
✅ 正确做法：通过 Core 路由
    P2P 模块 ──▶ Core ──▶ modules.call ──▶ Tor 模块
         │
         Core 负责：
         - 找到 Tor 模块
         - 调用正确的方法
         - 返回结果给 P2P
         - P2P 不需要知道 Tor 怎么实现的
```

### 8.2 模块间通信方式

**方式一：modules.call（同步调用）**

```rust
// P2P 模块想用 Tor 的 SOCKS 代理
// 只能通过 Core 的 modules.call

// P2P 模块内部：
fn route_through_tor(&self, data: &[u8]) -> Result<Vec<u8>, ModuleError> {
    // 1. P2P 不直接调用 Tor
    // 2. P2P 发请求给 Core
    let result = self.core.call(
        "tor",
        "socks_proxy",
        serde_json::json!({
            "host": "127.0.0.1",
            "port": 9050,
            "data": base64::encode(data)
        })
    )?;
    Ok(base64::decode(result["response"])?)
}
```

**方式二：事件订阅（异步通知）**

```rust
// P2P 想知道 Tor 的连接状态变化
// P2P 订阅 Tor 的事件

// P2P 内部：
self.subscribe("tor", "status_changed", |event| {
    if event.payload["status"] == "connected" {
        self.on_tor_ready();
    }
});
```

### 8.3 模块间权限控制

模块不能无限制地调用其他模块，Core 需要控制权限：

```rust
pub struct ModulePermissions {
    pub can_call: Vec<ModuleId>,      // 可以调用哪些模块
    pub can_subscribe: Vec<ModuleId>, // 可以订阅哪些模块的事件
    pub can_access_config: bool,       // 能否读写配置
}

impl Default for ModulePermissions {
    fn default() -> Self {
        // 默认权限：只能调用自己
        Self {
            can_call: vec![],
            can_subscribe: vec![],
            can_access_config: false,
        }
    }
}
```

**权限配置示例（config.json）**：

```json
{
  "modules": {
    "p2p": {
      "permissions": {
        "can_call": ["tor", "i2p", "crypto"],
        "can_subscribe": ["tor", "i2p"],
        "can_access_config": false
      }
    },
    "crdt": {
      "permissions": {
        "can_call": ["storage", "crypto"],
        "can_subscribe": [],
        "can_access_config": false
      }
    }
  }
}
```

---

## 9. 模块配置文件

### 9.1 模块配置结构

每个模块在 config.json 里有一节自己的配置：

```json
{
  "modules": {
    "p2p": {
      "enabled": true,
      "config": {
        "listenPort": 19000,
        "maxConnections": 50,
        "announceInterval": 300,
        "bootstrapNodes": [
          "/ip4/192.168.1.100/tcp/19000"
        ]
      }
    },
    "tor": {
      "enabled": false,
      "config": {
        "port": 9050,
        "bridges": [],
        "useBridges": false
      }
    }
  }
}
```

### 9.2 模块配置的校验

模块在 `init()` 时应该校验自己的配置：

```rust
fn init(&self, config: serde_json::Value) -> Result<(), ModuleError> {
    // 1. 尝试解析配置
    let cfg: P2PConfig = serde_json::from_value(config)
        .map_err(|e| ModuleError::BadRequest(format!("invalid config: {}", e)))?;
    
    // 2. 校验配置合法性
    if cfg.listenPort < 1024 {
        return Err(ModuleError::BadRequest("port must be >= 1024".into()));
    }
    
    // 3. 保存配置
    self.config = cfg;
    
    Ok(())
}
```

---

## 10. 模块开发流程

### 10.1 开发新模块的步骤

```
第一步：创建模块项目
    /
    └── cloudfin-mod-p2p/
         ├── Cargo.toml
         ├── src/
         │    └── lib.rs
         └── cloudfin_mod_p2p.md   ← 本规格文档

第二步：实现 Module trait
    impl Module for P2PModule {
        // 实现全部 8 个方法
    }

第三步：定义方法注册表
    pub fn method_registry() -> MethodRegistry {
        // 定义模块支持的所有方法 + JSON Schema
    }

第四步：编写规格文档
    参考本模板，描述模块的所有方法、事件、配置

第五步：审查
    - 检查 Module trait 实现完整性
    - 检查 JSON Schema 正确性
    - 检查错误处理
    - 检查线程安全

第六步：提交
    - 发布 .so 文件到模块市场
    - 或提交 PR 到 Cloudfin 官方模块仓库
```

### 10.2 模块项目模板

```
cloudfin-mod-example/
├── Cargo.toml
│
├── src/
│   ├── lib.rs              # 模块主文件
│   ├── methods.rs          # 方法实现
│   ├── config.rs           # 配置解析
│   ├── state.rs            # 内部状态
│   └── errors.rs           # 错误类型
│
├── Cargo.toml
│   [package]
│   name = "cloudfin-mod-example"
│   version = "0.1.0"
│   
│   [lib]
│   name = "cloudfin_mod_example"
│   crate-type = ["cdylib"]  # 编译为动态库
│   
│   [dependencies]
│   serde = { version = "1", features = ["derive"] }
│   serde_json = "1"
│   tokio = { version = "1", features = ["full"] }
│
└── README.md               # 模块说明
```

### 10.3 编译为动态库

```toml
# Cargo.toml
[lib]
name = "cloudfin_mod_example"
crate-type = ["cdylib"]  # 编译为 .so（Linux）、.dylib（macOS）、.dll（Windows）
```

```bash
# Linux
cargo build --release --target x86_64-unknown-linux-gnu
# 输出：target/x86_64-unknown-linux-gnu/release/libcloudfin_mod_example.so

# macOS
cargo build --release --target aarch64-apple-darwin
# 输出：target/aarch64-apple-darwin/release/libcloudfin_mod_example.dylib

# Android
cargo build --release --target aarch64-linux-android
# 输出：target/a86_64-linux-android/release/libcloudfin_mod_example.so
```

---

## 11. 模块的测试

### 11.1 测试策略

```
单元测试：
  测试模块内部函数逻辑
  
集成测试：
  Core + 模块，启动完整的 WebSocket 连接
  模拟 modules.call，验证返回结果
```

### 11.2 集成测试示例

```rust
#[cfg(test)]
mod tests {
    use super::*;
    
    #[tokio::test]
    async fn test_module_load_and_call() {
        // 1. 启动 Core（测试模式）
        let core = TestCore::new().await;
        
        // 2. 加载模块
        core.modules().load("p2p", json!({})).await.unwrap();
        
        // 3. 调用模块方法
        let result = core.modules()
            .call("p2p", "connect", json!({
                "peerId": "node_test",
                "address": "/ip4/127.0.0.1/tcp/19000"
            }))
            .await
            .unwrap();
        
        // 4. 验证结果
        assert_eq!(result["connected"], true);
        
        // 5. 卸载模块
        core.modules().unload("p2p").await.unwrap();
    }
}
```

---

## 12. 安全性

### 12.1 模块能做什么

| 能力 | 默认权限 | 说明 |
|------|---------|------|
| 实现 Module trait | ✅ 允许 | 必须实现 |
| 注册方法 | ✅ 允许 | 通过方法注册表 |
| 发送事件 | ✅ 允许 | 通过事件通道 |
| 调用其他模块 | ❌ 默认禁止 | 需配置权限 |
| 读写配置 | ❌ 默认禁止 | 需配置权限 |
| 访问文件系统 | ⚠️ 受限 | 沙箱内可访问自己的目录 |
| 访问网络 | ⚠️ 受限 | 只能使用 Core 分配的端口 |

### 12.2 恶意模块防范

| 风险 | 防范措施 |
|------|---------|
| 无限循环 | 每个方法调用有超时（默认 30s）|
| 内存泄漏 | 模块停止时强制释放资源 |
| 网络攻击 | 模块只能使用授权的端口 |
| 读取配置 | 只能读自己模块的配置项 |
| 破坏其他模块 | 模块间隔离，只能通过 Core API 交互 |

---

## 13. 审查清单

```
□ Module trait 全部 8 个方法已实现
□ 所有支持的方法已在注册表注册
□ 每个方法的 JSON Schema 完整且正确
□ 错误处理覆盖所有分支（不要 panic）
□ stop() 方法优雅停止逻辑正确
□ init() 配置校验逻辑正确
□ 线程安全（Send + Sync）
□ .so/.dylib 编译输出正确
□ 集成测试覆盖主要方法
□ README.md 文档完整
□ 无硬编码路径（使用标准配置目录）
□ 模块权限配置已定义
□ 版本号与 Core 版本兼容
```

---

*本文档由 墨尘 🌙 编写*  
*最后更新：2026-04-03*
