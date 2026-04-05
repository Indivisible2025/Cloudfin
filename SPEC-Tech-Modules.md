# Cloudfin Modules 技术文档

**版本：** v1.0
**日期：** 2026-04-05
**状态：** Draft（待评审）
**级别：** L1（路线级）

---

## 摘要

本文档定义 Cloudfin 的 Modules（模块）层技术规范。Modules 是 Core 控制的插件系统，分为三个模块层：

- **通信层（Network）**：P2P / TOR / I2P 等网络传输模块
- **加密层（Encrypt）**：crypto 加密通信模块
- **同步层（Sync）**：CRDT / Storage 等数据同步与持久化模块

Modules 之间不直接相互通信，所有交互通过 Core 统一调度。本文档覆盖 Module Trait 设计、状态机、Manifest 声明、模块实现细节及分发格式。

> **前置文档**：[SPEC-Design.md](./SPEC-Design.md) 定义了三层模块的整体架构。

---

## 1. Module Trait 设计

### 1.1 Trait 定义

所有 Module 必须实现 `Module` Trait，由 Core 统一加载、初始化、启停。以下是完整的 Trait 签名：

```rust
use serde_json::Value;
use anyhow::Result;

pub trait Module: Send + Sync {
    /// 模块唯一标识符，格式为反向域名，如 "cloudfin.p2p.libp2p"
    fn id(&self) -> &str;

    /// 人类可读的模块名称
    fn name(&self) -> &str;

    /// 语义化版本号，遵循 semver 2.0.0
    fn version(&self) -> &str;

    /// 模块级别：network | encrypt | sync
    fn layer(&self) -> ModuleLayer;

    /// 异步初始化，接受一个 JSON 配置对象
    async fn init(&mut self, config: Value) -> Result<()>;

    /// 异步启动模块，进入工作状态
    async fn start(&mut self) -> Result<()>;

    /// 异步停止模块，释放资源
    async fn stop(&mut self) -> Result<()>;

    /// 获取模块当前健康状态
    fn status(&self) -> ModuleHealth;

    /// 通用调用接口，用于 Core 向模块发送指令或查询状态
    async fn call(&self, method: &str, params: Value) -> Result<Value>;
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ModuleLayer {
    Network,
    Encrypt,
    Sync,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ModuleHealth {
    /// 未初始化
    Uninitialized,
    /// 已初始化但未启动
    Initialized,
    /// 运行中
    Running,
    /// 已停止
    Stopped,
    /// 错误状态
    Error,
}
```

#### 设计说明

- **`Send + Sync`**：所有 Module 必须在多线程环境下安全使用，因为 Core 可能在不同的 async 任务中调用模块方法。
- **`init/start/stop` 三阶段分离**：init 负责解析配置和申请资源；start 正式进入工作状态；stop 优雅退出。
- **`call` 方法**：采用 method dispatch 模式，Core 通过 `call("method_name", params)` 向模块发指令，返回 `serde_json::Value`。这避免了为每个操作定义独立 Trait 方法，保持扩展性。

#### call 方法调用示例

```rust
// Core 侧调用 P2P 模块连接节点
let result = p2p_module
    .call("connect", json!({ "peer_id": "/ip4/1.2.3.4/tcp/4001/p2p/Qm..." }))
    .await?;

// Core 侧查询 CRDT 模块文档版本
let version = storage_module
    .call("get_version", json!({ "doc_id": "doc-001" }))
    .await?;
```

### 1.2 生命周期

Module 的生命周期由 Core 管理，遵循严格的状态机：

```
┌──────────────┐   init()   ┌──────────────┐   start()   ┌──────────┐   stop()   ┌─────────┐
│Uninitialized │ ─────────→ │ Initialized  │ ─────────→ │ Running  │ ─────────→ │ Stopped │
└──────────────┘            └──────────────┘            └──────────┘            └─────────┘
       ↑                           │
       │                           │ (init 失败)
       └───────────────────────────┘
                   Error
```

| 状态 | 说明 | 允许的操作 |
|------|------|-----------|
| `Uninitialized` | 模块刚实例化，未加载配置 | 调用 `init()` |
| `Initialized` | 配置已加载，资源已申请 | 调用 `start()` 或 `stop()` |
| `Running` | 模块正常工作 | 调用 `call()` 或 `stop()` |
| `Stopped` | 资源已释放，进入终态 | 无（模块可重新 init） |
| `Error` | 异常状态 | 可尝试重新 `init()` 或查询错误信息 |

#### 生命周期代码示例

```rust
pub struct ModuleHandle<M: Module> {
    inner: M,
    health: ModuleHealth,
}

impl<M: Module> ModuleHandle<M> {
    pub async fn init(&mut self, config: Value) -> Result<()> {
        assert_eq!(self.health, ModuleHealth::Uninitialized);
        self.inner.init(config).await?;
        self.health = ModuleHealth::Initialized;
        Ok(())
    }

    pub async fn start(&mut self) -> Result<()> {
        assert_eq!(self.health, ModuleHealth::Initialized);
        self.inner.start().await?;
        self.health = ModuleHealth::Running;
        Ok(())
    }

    pub async fn stop(&mut self) -> Result<()> {
        if self.health == ModuleHealth::Running {
            self.inner.stop().await?;
        }
        self.health = ModuleHealth::Stopped;
        Ok(())
    }
}
```

### 1.3 健康状态

`ModuleHealth` 不仅是状态枚举，还承载运行时健康检查信息：

```rust
#[derive(Debug, Clone)]
pub struct ModuleHealth {
    state: ModuleHealthState,
    message: Option<String>,    // 错误或警告信息
    uptime_secs: Option<u64>,   // 运行时间（Running 时有效）
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ModuleHealthState {
    Uninitialized,
    Initialized,
    Running,
    Stopped,
    Error,
}
```

Core 应定期轮询各模块的 `status()`，当状态变为 `Error` 时触发告警或自动恢复流程。

---

## 2. Module Manifest

### 2.1 Schema 定义

每个 Module 包根目录必须包含 `module.so.json`（或嵌入二进制元数据区），声明模块的元信息、依赖、平台要求等。

以下为完整的 JSON Schema：

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "CloudfinModuleManifest",
  "type": "object",
  "required": ["id", "name", "version", "layer", "entry", "platform"],
  "properties": {
    "id": {
      "type": "string",
      "pattern": "^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+$",
      "examples": ["cloudfin.p2p.libp2p", "cloudfin.sync.crdt"]
    },
    "name": {
      "type": "string",
      "minLength": 1,
      "maxLength": 64
    },
    "version": {
      "type": "string",
      "pattern": "^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9]+)?$"
    },
    "layer": {
      "type": "string",
      "enum": ["network", "encrypt", "sync"]
    },
    "description": {
      "type": "string",
      "maxLength": 512
    },
    "entry": {
      "type": "string",
      "description": "动态库入口文件名（相对于包根目录）"
    },
    "platform": {
      "$ref": "#/definitions/platform"
    },
    "ui": {
      "$ref": "#/definitions/ui"
    },
    "permissions": {
      "$ref": "#/definitions/permissions"
    },
    "dependencies": {
      "type": "object",
      "additionalProperties": {
        "type": "string",
        "pattern": "^\\d+\\.\\d+\\.\\d+$"
      }
    },
    "config_schema": {
      "type": "object",
      "description": "JSON Schema for module configuration validation"
    },
    "capabilities": {
      "type": "array",
      "items": {
        "type": "string"
      }
    }
  },
  "definitions": {
    "platform": {
      "type": "object",
      "required": ["os", "arch"],
      "properties": {
        "os": {
          "type": "array",
          "items": {
            "type": "string",
            "enum": ["linux", "macos", "windows", "android", "ios"]
          }
        },
        "arch": {
          "type": "array",
          "items": {
            "type": "string",
            "enum": ["x86_64", "aarch64", "armv7", "riscv64"]
          }
        },
        "min_version": {
          "type": "string",
          "description": "最低操作系统版本要求"
        },
        "glibc_version": {
          "type": "string",
          "description": "最低 glibc 版本（Linux）"
        }
      }
    },
    "ui": {
      "type": "object",
      "properties": {
        "label": {
          "type": "string"
        },
        "icon": {
          "type": "string"
        },
        "fields": {
          "type": "array",
          "items": {
            "$ref": "#/definitions/ui_field"
          }
        }
      }
    },
    "ui_field": {
      "type": "object",
      "required": ["name", "type"],
      "properties": {
        "name": { "type": "string" },
        "label": { "type": "string" },
        "type": {
          "type": "string",
          "enum": ["text", "number", "boolean", "select", "password", "slider", "code"]
        },
        "default": {},
        "options": {
          "type": "array",
          "items": { "type": "string" }
        },
        "min": { "type": "number" },
        "max": { "type": "number" },
        "step": { "type": "number" },
        "placeholder": { "type": "string" },
        "hint": { "type": "string" },
        "required": { "type": "boolean" },
        "depends_on": {
          "type": "array",
          "items": { "type": "string" }
        }
      }
    },
    "permissions": {
      "type": "object",
      "properties": {
        "network": {
          "type": "array",
          "items": {
            "type": "string",
            "enum": ["internet", "local_network", "tor", "i2p"]
          }
        },
        "storage": {
          "type": "array",
          "items": {
            "type": "string",
            "enum": ["read_app_data", "write_app_data", "read_external", "write_external"]
          }
        },
        "system": {
          "type": "array",
          "items": {
            "type": "string",
            "enum": ["notifications", "clipboard", "biometrics"]
          }
        }
      }
    }
  }
}
```

#### Manifest 示例

```json
{
  "id": "cloudfin.p2p.libp2p",
  "name": "P2P Network Module",
  "version": "0.1.0",
  "layer": "network",
  "description": "基于 libp2p 的 P2P 网络通信模块，支持 NAT穿透和节点发现",
  "entry": "module.so",
  "platform": {
    "os": ["linux", "macos", "windows"],
    "arch": ["x86_64", "aarch64"],
    "glibc_version": "2.31"
  },
  "ui": {
    "label": "P2P 网络",
    "icon": "network",
    "fields": [
      {
        "name": "bootstrap_nodes",
        "label": "引导节点",
        "type": "text",
        "placeholder": "/ip4/1.2.3.4/tcp/4001/p2p/Qm...",
        "hint": "多个节点用逗号分隔",
        "required": false
      },
      {
        "name": "enable_relay",
        "label": "启用中继",
        "type": "boolean",
        "default": true
      },
      {
        "name": "max_peers",
        "label": "最大连接数",
        "type": "slider",
        "min": 1,
        "max": 100,
        "default": 25
      }
    ]
  },
  "permissions": {
    "network": ["internet", "local_network"],
    "storage": ["read_app_data", "write_app_data"]
  },
  "dependencies": {
    "libp2p": "0.54.0"
  },
  "config_schema": {
    "type": "object",
    "properties": {
      "bootstrap_nodes": { "type": "array", "items": { "type": "string" } },
      "enable_relay": { "type": "boolean" },
      "max_peers": { "type": "integer", "minimum": 1, "maximum": 100 }
    }
  },
  "capabilities": ["dial", "listen", "relay", "mdns_discovery"]
}
```

### 2.2 UI 声明结构

UI 声明允许 Module 在不修改 Core 代码的情况下声明配置表单，Core（或前端）根据声明动态渲染界面。

#### 支持的字段类型

| 类型 | 渲染组件 | 说明 |
|------|---------|------|
| `text` | 单行文本框 | 用于字符串输入 |
| `number` | 数字输入框 | 整数或浮点数 |
| `boolean` | 开关 | 二值开关 |
| `select` | 下拉选择框 | `options` 定义选项列表 |
| `password` | 密码输入框 | 隐藏显示，支持显示切换 |
| `slider` | 滑块 | 配合 `min`/`max`/`step` 使用 |
| `code` | 代码编辑器 | 支持语法高亮（JSON/YAML 等） |

#### 条件依赖

`depends_on` 字段支持条件显示：

```json
{
  "name": "relay_hop_limit",
  "label": "中继跳数限制",
  "type": "number",
  "default": 3,
  "depends_on": ["enable_relay"]
}
```

上述字段仅在 `enable_relay` 为 `true` 时显示。

### 2.3 平台与架构声明

`platform` 字段声明模块的运行环境要求。Core 在加载前进行平台匹配校验，不匹配的模块不展示或不可启用。

```json
{
  "platform": {
    "os": ["linux", "macos", "windows", "android"],
    "arch": ["x86_64", "aarch64"],
    "min_version": {
      "linux": "5.4",
      "windows": "10",
      "macos": "12.0",
      "android": "8.0"
    },
    "glibc_version": "2.31"
  }
}
```

### 2.4 权限声明

模块必须声明所需权限，Core 在安装或首次启用时请求用户授权。

```json
{
  "permissions": {
    "network": ["internet", "local_network", "tor"],
    "storage": ["read_app_data", "write_app_data", "read_external"],
    "system": ["notifications"]
  }
}
```

| 权限类型 | 可选值 | 说明 |
|---------|--------|------|
| `network` | `internet` | 互联网访问 |
| `network` | `local_network` | 局域网发现与通信 |
| `network` | `tor` | TOR 网络路由 |
| `network` | `i2p` | I2P 网络路由 |
| `storage` | `read_app_data` | 读取应用私有数据 |
| `storage` | `write_app_data` | 写入应用私有数据 |
| `storage` | `read_external` | 读取外部存储 |
| `storage` | `write_external` | 写入外部存储 |
| `system` | `notifications` | 系统通知 |
| `system` | `clipboard` | 剪贴板访问 |
| `system` | `biometrics` | 生物识别认证 |

---

## 3. 通信层 Modules（Network Layer）

通信层负责网络传输，支持多种网络协议和匿名路由方案。各通信模块之间互斥——同一时间只能激活一个。

### 3.1 P2P 模块（libp2p）

**库版本：** libp2p v0.54

#### 3.1.1 核心能力

| 能力 | 说明 |
|------|------|
| `dial` | 主动连接远端节点 |
| `listen` | 监听 incoming 连接 |
| `relay` | 作为中继协助 NAT 内节点 |
| `mdns_discovery` | 局域网内节点自动发现 |
| `kad_discovery` | Kademlia DHT 节点发现 |

#### 3.1.2 NAT 穿透

P2P 模块支持两种 NAT 穿透策略：

1. **中继模式（Relay）**
   - 模块作为 circuit relay 节点，转发双方流量
   - 配置项：`enable_relay: bool`
   - 中继跳数限制：`relay_hop_limit: u8`（默认 3）

2. **NAT 穿透（Hole Punching）**
   - 直连协商协议（针对对称型 NAT）
   - 需要双方在线协调，由 Core 提供 rendezvous 服务
   - 仍无法直连时自动降级为中继

```rust
// libp2p 配置示例
let transport = {
    let tcp = TokioTcpTransport::new(GenTransportConfig::default()
        .timeout(std::time::Duration::from_secs(10)));
    
    let relay = RelayConfig::new();
    let noise = NoiseConfig::xx(authenticated());
    
    tcp.upgrade(Version::V1)
        .authenticate(noise)
        .multiplex(yamux::YamuxConfig::default())
        .boxed()
};
```

#### 3.1.3 节点发现

| 方式 | 适用场景 | 配置项 |
|------|---------|--------|
| Bootstrap Nodes | 初始化已知节点列表 | `bootstrap_nodes: Vec<String>` |
| mDNS | 局域网内零配置发现 | `enable_mdns: bool` |
| Kademlia DHT | 大规模去中心化发现 | `enable_kad: bool` |

```rust
// 发现服务配置
let discovery = {
    let mdns = Mdns::new(MdnsConfig::default()).await?;
    let kad = KadProtocol::new(KadConfig::new(bootstrap_nodes));
    vec![Service::new(mdns), Service::new(kad)]
};
```

#### 3.1.4 Peer 管理 API

P2P 模块通过 `call()` 方法暴露以下接口：

| method | 说明 | params | return |
|--------|------|--------|--------|
| `connect` | 连接指定节点 | `{ peer_id: string, addr?: string }` | `{ success: bool }` |
| `disconnect` | 断开连接 | `{ peer_id: string }` | `{ success: bool }` |
| `list_peers` | 列出已连接节点 | `{}` | `{ peers: Vec<PeerInfo> }` |
| `peer_status` | 查询节点状态 | `{ peer_id: string }` | `{ status: string }` |
| `add_address` | 手动添加节点地址 | `{ peer_id, addr }` | `{ success: bool }` |

#### 3.1.5 Manifest 声明

```json
{
  "id": "cloudfin.p2p.libp2p",
  "name": "P2P Network Module",
  "version": "0.1.0",
  "layer": "network",
  "entry": "module.so",
  "platform": {
    "os": ["linux", "macos", "windows"],
    "arch": ["x86_64", "aarch64"]
  },
  "capabilities": ["dial", "listen", "relay", "mdns_discovery", "kad_discovery"],
  "permissions": {
    "network": ["internet", "local_network"]
  },
  "ui": {
    "label": "P2P 网络",
    "fields": [
      { "name": "bootstrap_nodes", "type": "text", "label": "引导节点" },
      { "name": "enable_relay", "type": "boolean", "label": "启用中继", "default": true },
      { "name": "enable_mdns", "type": "boolean", "label": "局域网发现", "default": true },
      { "name": "max_peers", "type": "slider", "label": "最大连接数", "min": 1, "max": 100, "default": 25 }
    ]
  }
}
```

---

### 3.2 TOR 模块（arti）

**库版本：** arti（Rust TOR 实现）

#### 3.2.1 Circuit 管理

TOR 模块使用 arti 库，提供洋葱路由电路管理：

```rust
pub struct TorModule {
    client: TorClient<Ed25519>,
    circuits: HashMap<CircuitId, Circuit>,
    config: TorConfig,
}

impl Module for TorModule {
    // ...
}

#[derive(Debug, Clone)]
pub struct Circuit {
    id: CircuitId,
    purpose: CircuitPurpose,
    hops: Vec<RelayEndpoint>,
    created_at: SystemTime,
    expires_at: SystemTime,
}

#[derive(Debug, Clone, Copy)]
pub enum CircuitPurpose {
    /// 通用匿名浏览
    General,
    /// P2P 节点通信
    P2P,
    /// 带出口节点的直连（最弱匿名性）
    Exit,
}
```

#### 3.2.2 匿名流量路由

模块支持将 P2P 流量通过 TOR 路由，提供两层路由选择：

| 路由模式 | 说明 | 匿名等级 |
|---------|------|---------|
| `tor-only` | 所有流量走 TOR 电路 | 高 |
| `tor-p2p` | P2P 信令走 TOR，数据直连 | 中 |
| `hybrid` | 部分节点走 TOR，部分直连 | 低 |

```json
{
  "id": "cloudfin.encrypt.tor",
  "name": "TOR Module",
  "version": "0.1.0",
  "layer": "network",
  "entry": "module.so",
  "capabilities": ["tor_circuit", "tor_proxy", "anonymous_routing"],
  "ui": {
    "label": "TOR 匿名网络",
    "fields": [
      {
        "name": "routing_mode",
        "type": "select",
        "label": "路由模式",
        "options": ["tor-only", "tor-p2p", "hybrid"],
        "default": "tor-p2p"
      },
      { "name": "num_circuits", "type": "slider", "label": "并发电路数", "min": 1, "max": 10, "default": 3 },
      { "name": "bridge_enabled", "type": "boolean", "label": "使用网桥", "default": false }
    ]
  },
  "permissions": {
    "network": ["internet", "tor"]
  }
}
```

#### 3.2.3 Tor 模块 API

| method | 说明 | params |
|--------|------|--------|
| `new_circuit` | 创建新电路 | `{ purpose: CircuitPurpose }` |
| `close_circuit` | 关闭电路 | `{ circuit_id: string }` |
| `list_circuits` | 列出活跃电路 | `{}` |
| `tor_proxy` | 将任意流量通过 TOR 代理 | `{ target: string, port: u16 }` |
| `status` | 获取 TOR 网络状态 | `{}` |

---

### 3.3 I2P 模块（待定）

**状态：** 待定（TBD）

#### 背景说明

I2P（ Invisible Internet Project）是另一种匿名网络协议，与 TOR 的主要区别在于：

| 特性 | TOR | I2P |
|------|-----|-----|
| 出口节点 | 需要 | 不需要（garlic routing） |
| 延迟 | 中等 | 较高 |
| 协议 | 洋葱路由（onion） | 大蒜路由（garlic） |
| 生态 | 广泛 | 较小 |
| 适用 | 网页浏览 | 文件共享、P2P |

#### 待定项

- 库选型：i2p sam library（`i2p-rust`）或通过 `i2pd` 守护进程通信
- circuit 管理方式
- 与现有 encrypt 层的集成方案
- Manifest 模板（待实现时补充）

---

## 4. 加密层 Modules（Encrypt Layer）

加密层负责端到端加密、密钥交换和安全通信。各加密模块可叠加使用（如 P2P + crypto 同时激活）。

### 4.1 crypto 模块

crypto 模块是 Cloudfin 的核心加密组件，提供 WireGuard 风格的密钥协商和 Noise Protocol 框架支持。

#### 4.1.1 协议选择

| 协议 | 说明 | 适用场景 |
|------|------|---------|
| `WireGuard` | 现代 UDP VPN 协议，高性能 | P2P 直连 |
| `Noise_XX` | Noise Protocol 框架，灵活 | 需要前向保密的场景 |
| `Noise_KK` | 共享密钥模式 | 预共享密钥组网 |

```rust
pub struct CryptoModule {
    protocol: CryptoProtocol,
    key_store: Arc<KeyStore>,
    session_manager: SessionManager,
}

pub enum CryptoProtocol {
    WireGuard(WireGuardConfig),
    NoiseXX(NoiseConfig),
    NoiseKK(NoiseConfig),
}

impl Module for CryptoModule {
    fn layer(&self) -> ModuleLayer { ModuleLayer::Encrypt }
    // ...
}
```

#### 4.1.2 密钥交换

crypto 模块采用以下密钥交换流程（以 Noise_XX 为例）：

```
Alice                          Bob
  │                              │
  │  ─── NE(AlicePub) ────────→  │  1. 生成临时椭圆曲线密钥对
  │                              │
  │  ←── NE(BobPub) ──────────  │  2. 生成临时密钥对，计算共享密钥
  │                              │
  │  ─── ENC(AliceStatic) ───→  │  3. 用 DH 结果加密传输静态公钥
  │                              │
  │  ←── ENC(BobStatic) ──────  │  4. Bob 解密并验证 Alice 身份
  │                              │
  │  ─── ENC(handshake_done) → │  5. 双方完成密钥派生，进入加密通信
```

```rust
// 密钥交换核心代码
async fn perform_handshake(
    &self,
    prologue: &[u8],
    local_static: &StaticSecret,
    local_ephemeral: &EphemeralSecret,
    remote_pubkey: &PublicKey,
) -> Result<SymmetricState> {
    let dh_result = local_ephemeral.dh(remote_pubkey)?;
    let mut state = SymmetricState::new(prologue);
    state.mix_hash(remote_pubkey.as_bytes());
    state.mix_key(dh_result.as_bytes())?;
    Ok(state)
}
```

#### 4.1.3 密钥存储

密钥存储采用分层管理：

```rust
pub trait KeyStore: Send + Sync {
    /// 生成新的身份密钥对
    async fn generate_identity(&self, id: &str) -> Result<IdentityKeys>;
    
    /// 获取身份密钥对
    async fn get_identity(&self, id: &str) -> Result<Option<IdentityKeys>>;
    
    /// 存储预共享密钥
    async fn store_psk(&self, id: &str, psk: PreSharedKey) -> Result<()>;
    
    /// 删除密钥
    async fn delete_identity(&self, id: &str) -> Result<()>;
}

pub struct IdentityKeys {
    pub static_public: PublicKey,
    pub static_private: StaticSecret,  // 内存中解密，不持久化明文
    pub session_cache: SessionCache,
}
```

#### 4.1.4 Manifest 声明

```json
{
  "id": "cloudfin.encrypt.crypto",
  "name": "Crypto Module",
  "version": "0.1.0",
  "layer": "encrypt",
  "entry": "module.so",
  "capabilities": ["wireguard", "noise_xx", "noise_kk", "key_exchange", "forward_secrecy"],
  "permissions": {
    "storage": ["read_app_data", "write_app_data"]
  },
  "ui": {
    "label": "加密通信",
    "fields": [
      {
        "name": "protocol",
        "type": "select",
        "label": "加密协议",
        "options": ["wireguard", "noise_xx", "noise_kk"],
        "default": "noise_xx"
      },
      {
        "name": "enable_pfs",
        "type": "boolean",
        "label": "前向保密",
        "default": true
      },
      {
        "name": "rekey_interval",
        "type": "slider",
        "label": "密钥轮换间隔（秒）",
        "min": 60,
        "max": 3600,
        "default": 300
      }
    ]
  }
}
```

### 4.2 密钥交换详解

#### 4.2.1 WireGuard 握手

```
                   Initiator                          Responder
                     │                                     │
  ├─生成临时公钥 (ephemeral_pub)                           │
  │ ──── REPLY_HEADERS ────────────────────────────────→ │
  │       + ephemeral_pub                                │
  │       + timestamp (cookie)                           │
  │                                                      ├─生成临时公钥 (ephemeral_pub)
  │                                                      ├─验证 timestamp
  │       (cookie 解密)                                  │
  │ ←──── REPLY_COOKIE ──────────────────────────────── │
  │       + encrypted_cookie                             │
  │                                                      │
  │ ──── COOKIE_RESPONSE ────────────────────────────→  │
  │       + mac(REPLY_HEADERS)                           │
  │                                                      ├─生成对称密钥 (static_private.dh(ephemeral_pub))
  │       + 加密的回应消息                                │
  │                                                      │
  │ ←──── TRANSPORT_DATA ──────────────────────────── │
```

#### 4.2.2 端到端加密流程

```rust
impl CryptoModule {
    /// 加密一条消息
    pub async fn encrypt(&self, peer: &PeerId, plaintext: &[u8]) -> Result<Ciphertext> {
        let session = self.session_manager.get_session(peer).await?;
        let nonce = session.next_nonce()?;
        let ciphertext = session.encrypt_with_nonce(nonce, plaintext)?;
        Ok(Ciphertext { nonce, ciphertext })
    }

    /// 解密一条消息
    pub async fn decrypt(&self, peer: &PeerId, ciphertext: &Ciphertext) -> Result<Vec<u8>> {
        let session = self.session_manager.get_session(peer).await?;
        session.decrypt_with_nonce(ciphertext.nonce, &ciphertext.ciphertext)
    }
}
```

### 4.3 商业授权说明

crypto 模块涉及以下可能需要商业授权的技术：

| 组件 | 许可证 | 说明 |
|------|--------|------|
| Curve25519 | BSD / Apache 2.0 | 无限制 |
| ChaCha20-Poly1305 | BSD / Apache 2.0 | 无限制 |
| WireGuard 协议 | GPL 2.0 | 实现需注意 GPL 传染性 |
| Noise Protocol | Public Domain / CC0 | 无限制 |

**建议：**
- 若采用纯 Rust 实现（Ring + curve25519-dalek），可保持 Apache 2.0 / MIT 许可证兼容
- WireGuard 协议本身 GPL，但仅使用协议规范实现无需 GPL 授权（参考 wireguard-rs 争议）
- 商业项目建议法务评估，或使用 Noise_XX 替代避免协议层面的 GPL 问题

---

## 5. 同步层 Modules（Sync Layer）

同步层负责数据的分布式同步和持久化，包含 CRDT 协作编辑模块和本地存储模块。

### 5.1 CRDT 模块（yrs）

**库版本：** yrs 0.22

#### 5.1.1 文档结构

CRDT 模块基于 yrs（Yjs Rust 实现），支持树状文档结构：

```rust
pub struct CrdtModule {
    doc: Arc<RwLock<YDoc>>,
    provider: SyncProvider,
    config: CrdtConfig,
}

impl Module for CrdtModule {
    // ...
}
```

**支持的 Yjs 数据类型：**

| Yjs 类型 | Rust 类型 | 说明 |
|---------|----------|------|
| `YText` | `YText` | 可并发编辑的文本（CRDT 字符序列） |
| `YMap` | `YMap` | 键值映射（Last-Write-Wins） |
| `YArray` | `YArray` | 有序列表 |
| `YXmlFragment` | `YXmlFragment` | XML 树结构 |

```rust
// 创建文档并操作
let doc = YDoc::new();
let text: YText = doc.get_or_create_text("content");
let mut text_mut = text.write();
text_mut.insert(0, "Hello, ")?;
text_mut.insert(7, "CRDT!")?;

// 创建 Map
let map: YMap = doc.get_or_create_map("metadata");
let mut map_mut = map.write();
map_mut.insert("author", "Alice")?;
map_mut.insert("version", 1)?;
```

#### 5.1.2 同步协议

CRDT 模块通过以下协议进行节点间同步：

```
┌─────────┐                         ┌─────────┐
│  Node A │                         │  Node B │
└────┬────┘                         └────┬────┘
     │                                   │
     │ ─── StateVector A ────────────→  │  A 询问 B 的已知状态
     │                                   │
     │ ←── StateVector B ────────────  │  B 回应自己的状态
     │                                   │
     │ ─── Update(A ⊖ B) ────────────→ │  A 发送 B 未有的更新
     │                                   │
     │ ←── Update(B ⊖ A) ────────────  │  B 发送 A 未有的更新
     │                                   │
     │  双方状态一致                      │
```

```rust
pub trait SyncProtocol {
    /// 获取本地状态向量
    fn state_vector(&self) -> StateVector;
    
    /// 计算需要同步的增量更新
    fn diff_updates(&self, remote_state: &StateVector) -> Vec<Update>;
    
    /// 应用来自远端的更新
    fn apply_updates(&self, updates: Vec<Update>) -> Result<()>;
}
```

#### 5.1.3 冲突解决（LWW）

CRDT 模块使用 **Last-Write-Wins Register (LWW)** 作为默认冲突解决策略：

```rust
/// LWW 寄存器实现
pub struct LwwRegister<T> {
    clock: Arc<Mutex<HLC>>,
    value: Arc<RwLock<(T, LamportTimestamp, PeerId)>>,
}

impl<T: Clone + 'static> Mergeable for LwwRegister<T> {
    fn merge(&mut self, other: &(T, LamportTimestamp, PeerId)) {
        let mut guard = self.value.write().unwrap();
        if other.1 > guard.1 || (other.1 == guard.1 && other.2 > guard.0) {
            *guard = other.clone();
        }
    }
}

/// HLC（Hybrid Logical Clocks）实现
pub struct HLC {
    wall_time: u64,
    logical: u64,
    node_id: PeerId,
}

impl HLC {
    pub fn now(&mut self) -> LamportTimestamp {
        let wall = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_millis() as u64;
        
        if wall > self.wall_time {
            self.wall_time = wall;
            self.logical = 0;
        } else {
            self.logical += 1;
        }
        
        LamportTimestamp {
            wall: self.wall_time,
            logical: self.logical,
            node: self.node_id.clone(),
        }
    }
}
```

**LWW 冲突解决规则：**
1. 比较时间戳（物理时间优先，相同则比较 `logical` 计数器）
2. 若时间戳完全相同（不同节点 HLC 碰撞），比较 `PeerId` 字典序
3. 胜出者的值覆盖当前值

#### 5.1.4 Manifest 声明

```json
{
  "id": "cloudfin.sync.crdt",
  "name": "CRDT Module",
  "version": "0.1.0",
  "layer": "sync",
  "entry": "module.so",
  "capabilities": ["ytext", "ymap", "yarray", "sync", "offline_edit", "persistence"],
  "dependencies": {
    "yrs": "0.22.0"
  },
  "ui": {
    "label": "协作编辑",
    "fields": [
      {
        "name": "sync_mode",
        "type": "select",
        "label": "同步模式",
        "options": ["eager", "lazy"],
        "default": "eager",
        "hint": "eager: 实时同步所有变更; lazy: 按需拉取"
      },
      {
        "name": "gc_enabled",
        "type": "boolean",
        "label": "垃圾回收",
        "default": true,
        "hint": "清理已删除内容的 CRDT 元数据"
      },
      {
        "name": "snapshot_interval",
        "type": "slider",
        "label": "快照间隔（秒）",
        "min": 30,
        "max": 600,
        "default": 120
      }
    ]
  },
  "permissions": {
    "storage": ["read_app_data", "write_app_data"]
  }
}
```

#### 5.1.5 CRDT 模块 API

| method | 说明 | params | return |
|--------|------|--------|--------|
| `create_doc` | 创建新文档 | `{ doc_id: string }` | `{ doc_id: string }` |
| `get_text` | 获取文本类型 | `{ doc_id, field }` | `{ content: string }` |
| `update_text` | 更新文本 | `{ doc_id, field, delta }` | `{ version: u64 }` |
| `get_map` | 获取 Map | `{ doc_id, field }` | `{ entries: object }` |
| `observe` | 监听变更 | `{ doc_id, field }` | `Notification` stream |
| `snapshot` | 创建快照 | `{ doc_id }` | `{ snapshot: Vec<u8> }` |
| `restore` | 恢复快照 | `{ doc_id, snapshot }` | `{ success: bool }` |

---

### 5.2 Storage 模块

Storage 模块提供本地持久化抽象，负责数据的存储路径管理、自动备份和导出功能。

#### 5.2.1 存储抽象

```rust
pub trait StorageBackend: Send + Sync {
    /// 读取数据
    async fn read(&self, path: &Path) -> Result<Bytes>;
    
    /// 写入数据
    async fn write(&self, path: &Path, data: Bytes) -> Result<()>;
    
    /// 删除数据
    async fn delete(&self, path: &Path) -> Result<()>;
    
    /// 列出目录
    async fn list(&self, dir: &Path) -> Result<Vec<PathBuf>>;
    
    /// 检查是否存在
    async fn exists(&self, path: &Path) -> bool;
}

pub enum StorageBackendType {
    /// 本地文件系统
    LocalFileSystem,
    /// SQLite 数据库
    Sqlite,
    /// RocksDB（嵌入式 KV）
    RocksDB,
    /// S3 兼容对象存储
    S3Compatible,
}
```

#### 5.2.2 路径管理

存储模块采用固定的目录布局：

```
{app_data_dir}/
├── cloudfin/
│   ├── config.json           # 全局配置
│   ├── modules/              # 模块目录
│   │   ├── cloudfin.p2p.libp2p/
│   │   │   ├── module.so
│   │   │   ├── manifest.json
│   │   │   └── data/         # 模块私有数据
│   │   └── cloudfin.encrypt.crypto/
│   │       └── ...
│   ├── data/                 # 用户数据
│   │   ├── docs/             # CRDT 文档
│   │   │   ├── doc-001.ycrdt
│   │   │   └── doc-002.ycrdt
│   │   └── snapshots/        # CRDT 快照
│   ├── keys/                 # 密钥存储（权限隔离）
│   ├── logs/                 # 日志文件
│   └── backups/              # 自动备份
```

#### 5.2.3 备份与导出

```rust
pub struct BackupManager {
    storage: Arc<dyn StorageBackend>,
    retention_days: u32,
    compression: CompressionType,
}

pub enum BackupFormat {
    /// 压缩 JSON
    Json,
    /// CRDT 二进制快照
    YSnapshot,
    /// 完整 ZIP 归档
    Zip,
}

impl BackupManager {
    /// 创建备份
    pub async fn create_backup(&self, doc_id: &str) -> Result<BackupId> {
        let snapshot = self.take_snapshot(doc_id).await?;
        let compressed = self.compress(snapshot).await?;
        let backup_path = self.generate_backup_path(doc_id);
        self.storage.write(&backup_path, compressed).await?;
        Ok(BackupId { doc_id: doc_id.into(), path: backup_path })
    }

    /// 导出数据（用户主动）
    pub async fn export(&self, doc_id: &str, format: ExportFormat) -> Result<PathBuf> {
        match format {
            ExportFormat::Json => self.export_json(doc_id).await,
            ExportFormat::Markdown => self.export_markdown(doc_id).await,
            ExportFormat::Pdf => self.export_pdf(doc_id).await,
        }
    }

    /// 清理过期备份
    pub async fn prune_old_backups(&self) -> Result<u32> {
        let cutoff = SystemTime::now() - Duration::days(self.retention_days as i64);
        let mut pruned = 0;
        for backup in self.list_backups().await? {
            if backup.created_at < cutoff {
                self.storage.delete(&backup.path).await?;
                pruned += 1;
            }
        }
        Ok(pruned)
    }
}
```

#### 5.2.4 Manifest 声明

```json
{
  "id": "cloudfin.sync.storage",
  "name": "Storage Module",
  "version": "0.1.0",
  "layer": "sync",
  "entry": "module.so",
  "capabilities": ["local_fs", "backup", "export", "prune", "encryption_at_rest"],
  "ui": {
    "label": "数据存储",
    "fields": [
      {
        "name": "backend",
        "type": "select",
        "label": "存储后端",
        "options": ["local_fs", "sqlite", "rocksdb"],
        "default": "local_fs"
      },
      {
        "name": "backup_enabled",
        "type": "boolean",
        "label": "自动备份",
        "default": true
      },
      {
        "name": "backup_interval",
        "type": "slider",
        "label": "备份间隔（小时）",
        "min": 1,
        "max": 72,
        "default": 6
      },
      {
        "name": "retention_days",
        "type": "slider",
        "label": "备份保留（天）",
        "min": 1,
        "max": 365,
        "default": 30
      },
      {
        "name": "export_format",
        "type": "select",
        "label": "默认导出格式",
        "options": ["json", "markdown", "pdf"],
        "default": "json"
      }
    ]
  },
  "permissions": {
    "storage": ["read_app_data", "write_app_data", "read_external", "write_external"]
  }
}
```

---

## 6. 分发格式

### 6.1 ZIP 包结构

Module 以 ZIP 压缩包形式分发，包名为 `{module_id}-{version}.zip`，结构如下：

```
cloudfin.p2p.libp2p-0.1.0.zip
├── module.so              # 动态链接库（实际入口文件）
├── module.so.json        # Module Manifest
├── module.so.sig         # 签名文件（可选，用于校验来源）
├── icon.png              # 模块图标（256x256, PNG）
├── preview.png           # 预览图（可选）
├── README.md             # 模块说明文档
├── CHANGELOG.md          # 变更日志
├── locales/
│   ├── zh-CN.json        # 中文本地化
│   ├── en-US.json        # 英文本地化
│   └── ja-JP.json        # 日文本地化（可选）
└── resources/            # 静态资源（可选）
    ├── wasm/
    └── config_templates/
```

#### 签名验证

```rust
pub async fn verify_module(package: &[u8], signature: &[u8], public_key: &PublicKey) -> Result<bool> {
    use ed25519_dalek::{Signature, Signer, Verifier};
    
    let sig = Signature::from_bytes(signature.try_into()?);
    Ok(public_key.verify(package, &sig).is_ok())
}
```

### 6.2 安装目录

Core 将模块安装到固定目录：

| 路径 | 说明 |
|------|------|
| `{app_data}/modules/{module_id}/{version}/` | 当前激活版本 |
| `{app_data}/modules/{module_id}/.pending/` | 升级待安装版本 |
| `{app_data}/modules/{module_id}/.rollback/` | 回滚版本（最近一个旧版本） |

#### 安装流程

```
1. 下载 ZIP 包 → {app_data}/modules/{id}/.pending/
2. 验证签名（signature）→ 失败则删除并报错
3. 验证 Manifest schema → 失败则删除并报错
4. 解压到 {app_data}/modules/{id}/{version}/
5. 原子替换符号链接或版本文件
6. 删除 .pending/ 目录
7. 如升级成功，将旧版本移至 .rollback/（仅保留一个）
```

### 6.3 版本文件命名

版本文件命名规则：`version-{semver}.lock`

```
modules/
└── cloudfin.p2p.libp2p/
    ├── version-0.1.0.lock     # 当前激活版本
    ├── version-0.1.0/          # 实际模块文件
    ├── version-0.0.9.lock      # 可回滚版本
    ├── version-0.0.9/
    ├── .pending/               # 安装中
    └── .rollback/              # 备用回滚
```

#### version lock 文件格式

```json
{
  "version": "0.1.0",
  "installed_at": "2026-04-05T10:30:00Z",
  "checksum": "sha256:a3f5c...",
  "signature": "base64...",
  "manifest_hash": "sha256:b7d9e..."
}
```

#### 版本兼容矩阵

| Core 版本 | 支持的 Module 版本策略 |
|----------|----------------------|
| Core 1.x | Module 0.x 任意版本 |
| Core 2.x | Module 1.x + Module 0.x（向后兼容） |
| 未来 | 通过 semver 范围声明约束 |

---

## 附录 A：Module 层与 Core 的交互

```
┌──────────────────────────────────────────────────────┐
│                       Core                            │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐     │
│  │ModuleLoader│  │ModuleManager│  │ EventBus   │     │
│  └─────┬──────┘  └──────┬─────┘  └─────┬──────┘     │
└────────┼───────────────┼──────────────┼────────────┘
         │               │              │
         │ load(id)      │ register()   │ emit(event)
         ▼               ▼              ▼
┌──────────────────────────────────────────────────────┐
│                     Modules                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│  │ P2P      │  │ Crypto   │  │ CRDT     │  ...       │
│  │ (network)│  │(encrypt) │  │ (sync)   │            │
│  └──────────┘  └──────────┘  └──────────┘            │
│                                                        │
│  所有模块不直接通信，通过 Core 调度                     │
└──────────────────────────────────────────────────────┘
```

**Core 提供的能力（供 Module 调用）：**

| 能力 | 说明 |
|------|------|
| `Core::emit(event, payload)` | 向事件总线发布事件 |
| `Core::dial(target)` | 请求 Core 代为连接节点 |
| `Core::storage()` | 获取 StorageBackend 实例 |
| `Core::config()` | 获取全局配置 |

---

## 附录 B：模块清单（计划）

| 模块 ID | 名称 | 层 | 状态 | 优先级 |
|---------|------|-----|------|--------|
| `cloudfin.p2p.libp2p` | P2P 模块 | network | 实现中 | P0 |
| `cloudfin.encrypt.tor` | TOR 模块 | network | 实现中 | P1 |
| `cloudfin.encrypt.i2p` | I2P 模块 | network | TBD | P2 |
| `cloudfin.encrypt.crypto` | 加密模块 | encrypt | 实现中 | P0 |
| `cloudfin.sync.crdt` | CRDT 模块 | sync | 实现中 | P0 |
| `cloudfin.sync.storage` | 存储模块 | sync | 实现中 | P1 |

---

## 附录 C：常见问题（FAQ）

**Q: Module 之间如何通信？**
A: Modules 之间不直接通信。所有交互通过 Core 中转：Module A 调用 `Core::emit(event)` 发布消息，Core 将消息路由给订阅的 Module B。这确保了模块间的松耦合和可插拔性。

**Q: 多个同层模块能否同时激活？**
A: 通信层模块（network）同一时间只能激活一个，因为它们都试图管理网络传输。加密层和同步层模块可以叠加（如 crypto + crdt + storage）。

**Q: 模块崩溃后 Core 如何处理？**
A: 模块的 `status()` 变为 `Error`，Core 触发告警。可配置自动重启（重新 init + start）或降级到备用模块。

**Q: 如何升级运行中的模块？**
A: 通过热升级流程：安装新版本到 `.pending/`，验证通过后切换符号链接，新版本 init + start 成功后旧版本移至 `.rollback/`。整个过程对用户无感知。

---

*本文档为工作草案（Working Draft），待核心团队评审后生效。*
