# Cloudfin Modules 技术文档

**版本：** v1.0
**日期：** 2026-04-05
**状态：** Draft（待评审）
**级别：** L3（路线级）

---

## 1. Modules 系统概述

> **语言约定：Modules 层统一使用 Rust**，通过 `#[no_std]` + alloc + libc 编译为 `.so/.dylib`，通过 Rust FFI 与 Core（Rust）交互。

### 1.1 三层 Modules 定位

Cloudfin Modules 是 Core 的可插拔扩展，按功能分为三层，三层之间**不直接通信**，统一由 Core 充当中介和调度器。

```
┌─────────────────────────────────────────────────────────────┐
│                        Core（大脑）                          │
│              连接管理 / 路由 / 模块调度 / 配置                  │
└───────▲─────────────────────▲─────────────────────▲─────────┘
        │                     │                     │
   通信层请求            加密层请求            同步层请求
        │                     │                     │
┌───────┴─────────────────────┴─────────────────────┴─────────┐
│  三层 Modules 只和 Core 通信，彼此不直接通信                    │
└─────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────┐
  │  通信层 Modules（地基 / 桥梁）                              │
  │  提供节点互联能力，负责网络通路                              │
  │  p2p（libp2p）/ tor（arti）/ i2p                         │
  └─────────────────────────────┬───────────────────────────┘
                                │ 在通信基础上加一层保护
  ┌─────────────────────────────▼───────────────────────────┐
  │  加密层 Modules（装修 / 安全设施）                          │
  │  端到端加密管道                                            │
  │  crypto（WireGuard / Noise）                              │
  └─────────────────────────────┬───────────────────────────┘
                                │ 真正有价值的业务在这一层
  ┌─────────────────────────────▼───────────────────────────┐
  │  同步层 Modules（人工作 / 车通行）                          │
  │  最终一致性文档协同 / 本地持久化存储                          │
  │  crdt（yrs）/ storage                                    │
  └─────────────────────────────────────────────────────────┘
```

**三层职责速查表：**

| 层次 | 定位 | 类比 | Modules | 核心价值 |
|------|------|------|---------|---------|
| 通信层 | 地基 + 桥梁 | 公路网 | p2p、tor、i2p | 节点互联、网络通路 |
| 加密层 | 装修 + 安全设施 | 安检站 / 隧道 | crypto | 数据机密性、完整性 |
| 同步层 | 人工作 + 车通行 | 工厂 / 仓库 | crdt、storage | 真正有价值的业务逻辑 |

### 1.2 模块与 Core 的关系（只和 Core 通信）

Modules 是 Core 的扩展，不能独立运作。三层 Modules 均通过 Core 统一调度，模块间不直接通信：

```
同步层模块 → Core（请求发送数据）
              Core → 通信层模块（实际发包）
                    对方 → Core → 同步层模块（收到数据）
```

**模块与 Core 交互方式：**

- **Core → 模块**：Core 通过统一 `Module` Trait 接口调用模块方法，模块返回结果
- **模块 → Core**：模块调用 Core 提供的 API（如发送数据）
- **模块间**：不直接通信，所有跨模块协作由 Core 统一协调

### 1.3 模块安装目录结构

```
{CoreRoot}/Cloudfin/
└── modules/                         # 模块文件目录（.so 和 .so.json 同目录）
    ├── Cloudfin-Module-Linux-amd64-P2P-v20260405-001.so
    ├── Cloudfin-Module-Linux-amd64-P2P-v20260405-001.so.json
    ├── Cloudfin-Module-Linux-amd64-CRDT-v20260405-001.so
    ├── Cloudfin-Module-Linux-amd64-CRDT-v20260405-001.so.json
    └── ...
```

模块配置存储在：

```
{CoreRoot}/Cloudfin/config/modules/   # 每个模块一个 .json
    ├── cloudfin.p2p.json
    ├── cloudfin.crdt.json
    └── cloudfin.crypto.json
```

---

## 2. 通信层 Modules

通信层 Modules 是整个系统的地基，负责建立节点之间的网络通路，提供 P2P 连接、NAT 穿透和匿名路由能力。

### 2.1 P2P 模块（libp2p v0.54）

**模块 ID：** `cloudfin.p2p`
**底层实现：** libp2p v0.54
**开源许可：** AGPL-3.0
**分发包名：** `Cloudfin-Module-Network-Linux-P2P-v{YYYYMMDD}-{NNN}.zip`

#### 2.1.1 功能概述

P2P 模块负责去中心化节点发现与连接管理，是 Cloudfin 网络的基础通道。

**核心能力：**

| 能力 | 说明 |
|------|------|
| 节点发现 | 通过 Kademlia DHT 进行去中心化节点发现 |
| NAT 穿透 | 支持 libp2p 内置 NAT 穿透方案（ICE-lite / hole punching） |
| 连接复用 | 单 TCP 连接上多路复用多个 substream |
| 中继转发 | 支持 relay 节点作为中继（fallback when P2P fails） |
| 多传输协议 | TCP、QUIC、WebSocket、WebRTC |

#### 2.1.2 配置文件（`cloudfin.p2p.json`）

```json
{
  "listen_addresses": [
    "/ip4/0.0.0.0/tcp/9000",
    "/ip4/0.0.0.0/udp/9000/quic"
  ],
  "max_peers": 50,
  "enable_upnp": true,
  "enable_relay": true,
  "boot_nodes": [
    "/ip4/1.2.3.4/tcp/9000/p2p/QmTihkzQ3w2K3jN5c5vL8gR4pMn6qR2tUi8vX9zA1bC3dE"
  ],
  "dht_mode": "server",
  "connection_limits": {
    "max_incoming": 30,
    "max_outgoing": 20
  }
}
```

**字段说明：**

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `listen_addresses` | Array\<String\> | `["/ip4/0.0.0.0/tcp/9000"]` | 监听地址列表 |
| `max_peers` | u32 | `50` | 最大 peer 数量 |
| `enable_upnp` | bool | `true` | 是否启用 UPnP 端口映射 |
| `enable_relay` | bool | `true` | 是否启用 relay 中继（NAT 穿透失败时 fallback） |
| `boot_nodes` | Array\<String\> | `[]` | 引导节点地址 |
| `dht_mode` | String | `"server"` | DHT 模式：`server` / `client` |
| `connection_limits.max_incoming` | u32 | `30` | 最大入站连接数 |
| `connection_limits.max_outgoing` | u32 | `20` | 最大出站连接数 |

#### 2.1.3 Module Trait 实现

```rust
use async_trait::async_trait;
use serde_json::Value;

pub struct P2PModule {
    config: Value,
    swarm: Option<Swarm>,
    status: ModuleHealth,
}

#[async_trait]
impl Module for P2PModule {
    fn id(&self) -> &str {
        "cloudfin.p2p"
    }

    fn name(&self) -> &str {
        "P2P Networking"
    }

    fn version(&self) -> &str {
        "0.1.0"
    }

    async fn init(&mut self, config: Value) -> anyhow::Result<()> {
        self.config = config.clone();
        // 初始化 libp2p Swarm
        let swarm = build_swarm(&config)?;
        self.swarm = Some(swarm);
        self.status = ModuleHealth::Healthy;
        tracing::info!(target: "cloudfin::module::p2p", "P2P module initialized");
        Ok(())
    }

    async fn start(&mut self) -> anyhow::Result<()> {
        if let Some(swarm) = self.swarm.take() {
            // 启动 Swarm 事件循环
            self.spawn_swarm_task(swarm).await;
            self.status = ModuleHealth::Healthy;
            tracing::info!(target: "cloudfin::module::p2p", "P2P module started");
        }
        Ok(())
    }

    async fn stop(&mut self) -> anyhow::Result<()> {
        // 优雅关闭所有连接
        self.close_all_connections().await;
        self.status = ModuleHealth::Healthy;
        tracing::info!(target: "cloudfin::module::p2p", "P2P module stopped");
        Ok(())
    }

    fn status(&self) -> ModuleHealth {
        self.status
    }

    async fn call(&self, method: &str, params: Value) -> anyhow::Result<Value> {
        match method {
            "dial" => self.dial(params).await,
            "listen" => self.listen(params).await,
            "peers" => self.list_peers().await,
            "connected" => self.is_connected(params).await,
            _ => Err(anyhow::anyhow!("Unknown method: {}", method)),
        }
    }
}
```

#### 2.1.4 `call` 方法接口

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `dial` | `{ "addr": "/ip4/x.x.x.x/tcp/9000/p2p/..." }` | `{ "peer_id": "..." }` | 主动连接远端节点 |
| `listen` | `{ "addr": "/ip4/0.0.0.0/tcp/9000" }` | `{ "listening": ["..."] }` | 启动监听 |
| `peers` | `{}` | `{ "peers": [{ "id": "...", "addr": "..." }] }` | 列出所有已知 peer |
| `connected` | `{ "peer_id": "..." }` | `{ "connected": true/false }` | 检查是否连接到指定 peer |

---

### 2.2 TOR 模块（arti）

**模块 ID：** `cloudfin.tor`
**底层实现：** arti（Rust 实现的 TOR 协议）
**开源许可：** AGPL-3.0
**分发包名：** `Cloudfin-Module-Network-Linux-TOR-v{YYYYMMDD}-{NNN}.zip`

#### 2.2.1 功能概述

TOR 模块提供洋葱路由匿名流量能力，适用于需要隐私保护的场景。

**核心能力：**

| 能力 | 说明 |
|------|------|
| 洋葱路由 | 基于 arti 的纯 Rust TOR 实现 |
| 单 circuit 多 stream | 支持在一个电路上复用多个数据流 |
| 目录缓存 | 内置本地目录缓存，减少网络请求 |
| 可插拔传输 | 支持与 P2P 模块协同工作 |

#### 2.2.2 配置文件（`cloudfin.tor.json`）

```json
{
  "data_directory": "{CoreRoot}/Cloudfin/data/tor",
  "socks_port": 9050,
  "control_port": 9051,
  "enable_expert": false,
  "circuit_count": 3,
  "bridges": [],
  "use_bridges": false
}
```

**字段说明：**

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `data_directory` | String | — | TOR 数据目录（含私钥、circuit 状态） |
| `socks_port` | u16 | `9050` | SOCKS5 代理端口 |
| `control_port` | u16 | `9051` | TOR 控制端口（用于模块间通信） |
| `enable_expert` | bool | `false` | 是否启用专家模式（自定义 circuit） |
| `circuit_count` | u32 | `3` | 同时维持的 circuit 数量 |
| `bridges` | Array\<String\> | `[]` | 网桥地址（需要时配置） |
| `use_bridges` | bool | `false` | 是否使用网桥连接 |

#### 2.2.3 Module Trait 实现

```rust
pub struct TorModule {
    config: Value,
   arti_client: Option<ArtiClient>,
    status: ModuleHealth,
}

#[async_trait]
impl Module for TorModule {
    fn id(&self) -> &str {
        "cloudfin.tor"
    }

    fn name(&self) -> &str {
        "TOR Anonymous Network"
    }

    fn version(&self) -> &str {
        "0.1.0"
    }

    async fn init(&mut self, config: Value) -> anyhow::Result<()> {
        let data_dir = config["data_directory"]
            .as_str()
            .map(PathBuf::from)
            .unwrap_or_else(|| {
                PathBuf::from(std::env::var("CLOUDFIN_ROOT").unwrap())
                    .join("data/tor")
            });
        std::fs::create_dir_all(&data_dir)?;
        letarti_client = ArtiBuilder::default()
            .data_dir(&data_dir)
            .build()
            .await?;
        self.arti_client = Some(arti_client);
        self.status = ModuleHealth::Healthy;
        tracing::info!(target: "cloudfin::module::tor", "TOR module initialized");
        Ok(())
    }

    async fn start(&mut self) -> anyhow::Result<()> {
        if let Some(ref mut client) = self.arti_client {
            client.enable().await?;
        }
        tracing::info!(target: "cloudfin::module::tor", "TOR module started");
        Ok(())
    }

    async fn stop(&mut self) -> anyhow::Result<()> {
        if let Some(ref mut client) = self.arti_client {
            client.disable().await?;
        }
        tracing::info!(target: "cloudfin::module::tor", "TOR module stopped");
        Ok(())
    }

    fn status(&self) -> ModuleHealth {
        self.status
    }

    async fn call(&self, method: &str, params: Value) -> anyhow::Result<Value> {
        match method {
            "connect" => self.tor_connect(params).await,
            "circuits" => self.list_circuits().await,
            "new_circuit" => self.new_circuit(params).await,
            _ => Err(anyhow::anyhow!("Unknown method: {}", method)),
        }
    }
}
```

#### 2.2.4 `call` 方法接口

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `connect` | `{ "host": "...", "port": 80 }` | `{ "stream_id": "..." }` | 通过 TOR 网络建立连接 |
| `circuits` | `{}` | `{ "circuits": [...] }` | 列出当前活跃的 circuits |
| `new_circuit` | `{ "purpose": "general" }` | `{ "circuit_id": "..." }` | 创建新的匿名电路 |

---

### 2.3 I2P 模块（待定）

**模块 ID：** `cloudfin.i2p`
**底层实现：** 待定
**开源许可：** 待定
**状态：** 规划中，暂未实现

I2P 模块提供另一种匿名网络隧道能力，与 TOR 模块互补，适用于不同威胁模型。实现细节待补充。

---

## 3. 加密层 Modules

加密层 Modules 在通信层的基础上提供端到端加密管道，确保数据机密性和完整性。

### 3.1 crypto 模块（WireGuard / Noise）

**模块 ID：** `cloudfin.crypto`
**开源许可：** 商业授权
**分发包名：** `Cloudfin-Module-Encrypt-{OS}-crypto-v{YYYYMMDD}-{NNN}.zip`

#### 3.1.1 功能概述

crypto 模块为 P2P 连接提供端到端加密，支持 WireGuard 协议和 Noise 框架两种模式。

**核心能力：**

| 能力 | 说明 |
|------|------|
| WireGuard 模式 | 基于 WireGuard 协议，适合端到端 VPN 场景 |
| Noise 协议 | 基于 libnoise，支持灵活的加密握手模式 |
| 前向保密（PFS） | 每个会话独立密钥，泄密历史无法解密 |
| 密钥交换 | 支持 Curve25519、ECDSA 多种密钥算法 |

#### 3.1.2 配置文件（`cloudfin.crypto.json`）

```json
{
  "mode": "noise",
  "noise": {
    "handshake_pattern": "IK",
    "local_private_key_ref": "key:local_static",
    "local_ephemeral_key_ref": "key:local_ephemeral",
    "remote_static_key_ref": null
  },
  "wireguard": {
    "listen_port": 51820,
    "endpoint": null,
    "allowed_ips": ["0.0.0.0/0", "::/0"],
    "persistent_keepalive": 25
  }
}
```

**字段说明：**

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `mode` | String | `"noise"` | 加密模式：`noise` 或 `wireguard` |
| `noise.handshake_pattern` | String | `"IK"` | Noise 握手模式 |
| `noise.local_private_key_ref` | String | — | 本地静态私钥引用（来自 SecretVault） |
| `wireguard.listen_port` | u16 | `51820` | WireGuard 监听端口 |
| `wireguard.allowed_ips` | Array\<String\> | `["0.0.0.0/0"]` | WireGuard 允许的 IP 范围 |
| `wireguard.persistent_keepalive` | u16 | `25` | 保活间隔（秒），0 表示禁用 |

#### 3.1.3 Module Trait 实现

```rust
pub struct CryptoModule {
    config: Value,
    cipher_states: HashMap<PeerId, CipherState>,
    status: ModuleHealth,
}

#[async_trait]
impl Module for CryptoModule {
    fn id(&self) -> &str {
        "cloudfin.crypto"
    }

    fn name(&self) -> &str {
        "End-to-End Encryption"
    }

    fn version(&self) -> &str {
        "0.1.0"
    }

    async fn init(&mut self, config: Value) -> anyhow::Result<()> {
        self.config = config.clone();
        self.cipher_states = HashMap::new();
        self.status = ModuleHealth::Healthy;
        tracing::info!(target: "cloudfin::module::crypto", "Crypto module initialized");
        Ok(())
    }

    async fn start(&mut self) -> anyhow::Result<()> {
        tracing::info!(target: "cloudfin::module::crypto", "Crypto module started");
        Ok(())
    }

    async fn stop(&mut self) -> anyhow::Result<()> {
        // 覆写并清除所有密钥状态
        for (_, state) in self.cipher_states.drain() {
            state.destroy();
        }
        tracing::info!(target: "cloudfin::module::crypto", "Crypto module stopped");
        Ok(())
    }

    fn status(&self) -> ModuleHealth {
        self.status
    }

    async fn call(&self, method: &str, params: Value) -> anyhow::Result<Value> {
        match method {
            "encrypt" => self.encrypt(params).await,
            "decrypt" => self.decrypt(params).await,
            "init_handshake" => self.init_handshake(params).await,
            "complete_handshake" => self.complete_handshake(params).await,
            _ => Err(anyhow::anyhow!("Unknown method: {}", method)),
        }
    }
}
```

#### 3.1.4 `call` 方法接口

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `encrypt` | `{ "peer_id": "...", "plaintext": "..." }` | `{ "ciphertext": "..." }` | 加密数据 |
| `decrypt` | `{ "peer_id": "...", "ciphertext": "..." }` | `{ "plaintext": "..." }` | 解密数据 |
| `init_handshake` | `{ "peer_id": "...", "local_static_key_ref": "..." }` | `{ "handshake_payload": "..." }` | 初始化密钥交换 |
| `complete_handshake` | `{ "peer_id": "...", "handshake_payload": "..." }` | `{ "session_established": true }` | 完成密钥交换 |

---

## 4. 同步层 Modules

同步层 Modules 是 Cloudfin 的核心价值层，提供最终一致性的文档协同和本地持久化存储能力。

### 4.1 CRDT 模块（yrs 0.22）

**模块 ID：** `cloudfin.crdt`
**底层实现：** yrs（Yjs Rust 核心）v0.22
**开源许可：** AGPL-3.0
**分发包名：** `Cloudfin-Module-Sync-Linux-CRDT-v{YYYYMMDD}-{NNN}.zip`

#### 4.1.1 功能概述

CRDT 模块提供最终一致性的文档协同能力，基于 yrs（Yjs Rust）实现，支持离线编辑和冲突自动合并。

**核心能力：**

| 能力 | 说明 |
|------|------|
| CRDT 文档类型 | 支持 Y.Doc（含 Text、Map、Array、XmlFragment 等） |
| 更新传播 | 将本地更新序列化为二进制，通过 P2P 模块传播 |
| 冲突合并 | 离线编辑可自动合并，无需人工干预 |
| Undo / Redo | 支持撤销/重做（文档级别） |
| 持久化 | 支持将 Doc 状态持久化到 storage 模块 |

#### 4.1.2 配置文件（`cloudfin.crdt.json`）

```json
{
  "gc_enabled": true,
  "snapshots_enabled": false,
  "snapshot_interval_secs": 3600,
  "max_documents": 100,
  "sync_transport": "p2p",
  "encoding": "binary"
}
```

**字段说明：**

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `gc_enabled` | bool | `true` | 是否启用垃圾回收（清理已删除的 item） |
| `snapshots_enabled` | bool | `false` | 是否启用定期快照 |
| `snapshot_interval_secs` | u64 | `3600` | 快照间隔（秒） |
| `max_documents` | u32 | `100` | 最大文档数量上限 |
| `sync_transport` | String | `"p2p"` | 同步传输方式（`p2p` 或 `relay`） |
| `encoding` | String | `"binary"` | 更新编码格式：`binary` 或 `json` |

#### 4.1.3 Module Trait 实现

```rust
use yrs::{Doc, Transact, Update};
use std::sync::Arc;
use tokio::sync::RwLock;

pub struct CrdtModule {
    config: Value,
    documents: Arc<RwLock<HashMap<String, Doc>>>,
    update_tx: broadcast::Sender<DocUpdate>,
    status: ModuleHealth,
}

#[derive(Clone)]
pub struct DocUpdate {
    pub doc_id: String,
    pub update: Vec<u8>,
    pub origin: PeerId,
}

#[async_trait]
impl Module for CrdtModule {
    fn id(&self) -> &str {
        "cloudfin.crdt"
    }

    fn name(&self) -> &str {
        "CRDT Document Sync"
    }

    fn version(&self) -> &str {
        "0.1.0"
    }

    async fn init(&mut self, config: Value) -> anyhow::Result<()> {
        self.config = config.clone();
        self.documents = Arc::new(RwLock::new(HashMap::new()));
        self.status = ModuleHealth::Healthy;
        tracing::info!(target: "cloudfin::module::crdt", "CRDT module initialized");
        Ok(())
    }

    async fn start(&mut self) -> anyhow::Result<()> {
        // 启动更新广播任务
        self.spawn_sync_task().await;
        tracing::info!(target: "cloudfin::module::crdt", "CRDT module started");
        Ok(())
    }

    async fn stop(&mut self) -> anyhow::Result<()> {
        // 持久化所有文档状态
        self.persist_all().await?;
        tracing::info!(target: "cloudfin::module::crdt", "CRDT module stopped");
        Ok(())
    }

    fn status(&self) -> ModuleHealth {
        self.status
    }

    async fn call(&self, method: &str, params: Value) -> anyhow::Result<Value> {
        match method {
            "doc_create" => self.doc_create(params).await,
            "doc_get" => self.doc_get(params).await,
            "doc_update" => self.doc_update(params).await,
            "doc_apply" => self.doc_apply(params).await,
            "doc_subscribe" => self.doc_subscribe(params).await,
            _ => Err(anyhow::anyhow!("Unknown method: {}", method)),
        }
    }
}
```

#### 4.1.4 `call` 方法接口

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `doc_create` | `{ "id": "doc-uuid", "type": "text" }` | `{ "doc_id": "..." }` | 创建新文档 |
| `doc_get` | `{ "id": "doc-uuid" }` | `{ "state": "..." }` | 获取文档当前状态 |
| `doc_update` | `{ "id": "doc-uuid", "update": "base64..." }` | `{ "update": "base64..." }` | 对文档执行本地更新 |
| `doc_apply` | `{ "id": "doc-uuid", "update": "base64..." }` | `{}` | 应用来自远端的更新 |
| `doc_subscribe` | `{ "id": "doc-uuid" }` | event stream | 订阅文档变更事件 |

---

### 4.2 Storage 模块

**模块 ID：** `cloudfin.storage`
**开源许可：** AGPL-3.0
**分发包名：** `Cloudfin-Module-Sync-{OS}-Storage-v{YYYYMMDD}-{NNN}.zip`

#### 4.2.1 功能概述

Storage 模块提供本地持久化存储抽象，支持键值存储、文档存储和文件存储三种模式。

**核心能力：**

| 能力 | 说明 |
|------|------|
| 键值存储 | K/V 存储（基于 RocksDB 或 Sled） |
| 文档存储 | JSON 文档存储（CRDT 模块状态快照） |
| 文件存储 | 大文件块存储（分块、压缩、去重） |
| 加密存储 | 支持对存储内容进行加密（AES-256-GCM） |
| 备份导出 | 支持将存储内容导出为 tarball |

#### 4.2.2 配置文件（`cloudfin.storage.json`）

```json
{
  "engine": "rocksdb",
  "data_dir": "{CoreRoot}/Cloudfin/data/storage",
  "kv": {
    "enabled": true,
    "max_db_size_gb": 10
  },
  "docstore": {
    "enabled": true,
    "collection_names": ["crdt_snapshots", "config_backups"]
  },
  "filestore": {
    "enabled": true,
    "chunk_size_kb": 256,
    "dedup_enabled": true
  },
  "encryption": {
    "enabled": false,
    "key_ref": null
  }
}
```

**字段说明：**

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `engine` | String | `"rocksdb"` | 存储引擎：`rocksdb` 或 `sled` |
| `data_dir` | String | — | 存储根目录 |
| `kv.enabled` | bool | `true` | 是否启用键值存储 |
| `kv.max_db_size_gb` | u32 | `10` | KV 数据库最大体积 |
| `docstore.enabled` | bool | `true` | 是否启用文档存储 |
| `filestore.enabled` | bool | `true` | 是否启用文件存储 |
| `filestore.chunk_size_kb` | u32 | `256` | 文件分块大小（KB） |
| `filestore.dedup_enabled` | bool | `true` | 是否启用去重 |
| `encryption.enabled` | bool | `false` | 是否启用存储加密 |
| `encryption.key_ref` | String | `null` | 加密密钥引用（来自 SecretVault） |

#### 4.2.3 Module Trait 实现

```rust
use rocksdb::{DB, Options};

pub struct StorageModule {
    config: Value,
    db: Option<DB>,
    status: ModuleHealth,
}

#[async_trait]
impl Module for StorageModule {
    fn id(&self) -> &str {
        "cloudfin.storage"
    }

    fn name(&self) -> &str {
        "Persistent Storage"
    }

    fn version(&self) -> &str {
        "0.1.0"
    }

    async fn init(&mut self, config: Value) -> anyhow::Result<()> {
        let data_dir = config["data_dir"]
            .as_str()
            .map(PathBuf::from)
            .unwrap_or_else(|| {
                PathBuf::from(std::env::var("CLOUDFIN_ROOT").unwrap())
                    .join("data/storage")
            });
        std::fs::create_dir_all(&data_dir)?;

        let mut db_opts = Options::default();
        db_opts.create_if_missing(true);
        let db = DB::open(&db_opts, &data_dir)?;
        self.db = Some(db);
        self.status = ModuleHealth::Healthy;
        tracing::info!(target: "cloudfin::module::storage", "Storage module initialized");
        Ok(())
    }

    async fn start(&mut self) -> anyhow::Result<()> {
        tracing::info!(target: "cloudfin::module::storage", "Storage module started");
        Ok(())
    }

    async fn stop(&mut self) -> anyhow::Result<()> {
        if let Some(db) = self.db.take() {
            db.close();
        }
        tracing::info!(target: "cloudfin::module::storage", "Storage module stopped");
        Ok(())
    }

    fn status(&self) -> ModuleHealth {
        self.status
    }

    async fn call(&self, method: &str, params: Value) -> anyhow::Result<Value> {
        match method {
            "kv_get" => self.kv_get(params).await,
            "kv_set" => self.kv_set(params).await,
            "kv_del" => self.kv_del(params).await,
            "doc_put" => self.doc_put(params).await,
            "doc_get" => self.doc_get(params).await,
            "file_put" => self.file_put(params).await,
            "file_get" => self.file_get(params).await,
            "export" => self.export(params).await,
            _ => Err(anyhow::anyhow!("Unknown method: {}", method)),
        }
    }
}
```

#### 4.2.4 `call` 方法接口

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `kv_get` | `{ "key": "..." }` | `{ "value": "..." }` | 获取 KV 值 |
| `kv_set` | `{ "key": "...", "value": "..." }` | `{}` | 设置 KV 值 |
| `kv_del` | `{ "key": "..." }` | `{}` | 删除 KV 值 |
| `doc_put` | `{ "collection": "...", "id": "...", "doc": {...} }` | `{}` | 存储 JSON 文档 |
| `doc_get` | `{ "collection": "...", "id": "..." }` | `{ "doc": {...} }` | 获取 JSON 文档 |
| `file_put` | `{ "path": "...", "data": "base64..." }` | `{ "file_id": "..." }` | 存储文件 |
| `file_get` | `{ "file_id": "..." }` | `{ "data": "base64..." }` | 获取文件 |
| `export` | `{ "format": "tarball" }` | `{ "data": "base64..." }` | 导出全部存储数据 |

---

## 5. 共同接口

所有 Cloudfin Modules 必须实现以下统一接口，确保与 Core 的兼容性和可替换性。

### 5.1 Module Trait

所有模块必须实现以下 Trait，与 [SPEC-Impl-Core.md §3 Module Trait 定义](SPEC-Impl-Core.md) 完全一致：

```rust
use serde::{Deserialize, Serialize};
use serde_json::Value;

// ─── 健康状态枚举 ───────────────────────────────────
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ModuleHealth {
    Healthy,
    Degraded(String),
    Unhealthy(String),
    Unknown,
}

// ─── 模块元信息 ───────────────────────────────────
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModuleInfo {
    pub id: String,
    pub name: String,
    pub version: String,
    pub health: ModuleHealth,
}

// ─── Module Trait ─────────────────────────────────
#[async_trait::async_trait]
pub trait Module: Send + Sync {
    // ── 元信息 ──────────────────────────────
    fn id(&self) -> &str;
    fn name(&self) -> &str;
    fn version(&self) -> &str;

    // ── 生命周期 ────────────────────────────
    async fn init(&mut self, config: Value) -> anyhow::Result<()>;
    async fn start(&mut self) -> anyhow::Result<()>;
    async fn stop(&mut self) -> anyhow::Result<()>;

    // ── 状态 ────────────────────────────────
    fn status(&self) -> ModuleHealth;

    fn info(&self) -> ModuleInfo {
        ModuleInfo {
            id: self.id().to_string(),
            name: self.name().to_string(),
            version: self.version().to_string(),
            health: self.status(),
        }
    }

    // ── 调用接口 ────────────────────────────
    async fn call(&self, method: &str, params: Value) -> anyhow::Result<Value>;
}
```

**实现约束：**

- 所有模块均使用 `#[async_trait]` 以支持 async 方法的 trait object 安全调用
- `init` / `start` / `stop` 三阶段严格有序，不得跨阶段调用
- `call` 方法须自行实现方法分发逻辑（推荐使用 `match method`）
- 耗时操作须在内部 spawn 任务，避免阻塞 Core 事件循环
- 线程安全：模块须实现 `Send + Sync`，所有内部状态须使用适当的同步原语

### 5.2 Manifest Schema

每个模块 ZIP 包必须包含一个 `.so.json` 文件（Manifest），与 [SPEC-Impl-Core.md §4 Manifest Schema](SPEC-Impl-Core.md) 一致：

**Manifest 文件名：** `{模块名}.so.json`（与 `.so` 同目录）

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
      "description": "所需系统权限列表"
    },
    "api_version": {
      "type": "string",
      "description": "所需 Core API 版本约束，使用 semver range 格式（如 ^1.0）"
    },
    "ui": {
      "type": "object",
      "description": "UI 声明，详见 SPEC-Impl-UI.md L4 实现文档"
    }
  }
}
```

**Manifest 示例（cloudfin.p2p.so.json）：**

```json
{
  "id": "cloudfin.p2p",
  "name": "P2P Networking",
  "version": "0.1.0",
  "description": "去中心化 P2P 网络模块，基于 libp2p v0.54",
  "author": "Cloudfin Team",
  "platform": ["linux", "android"],
  "permissions": ["network", "file_system"],
  "api_version": "^1.0",
  "ui": {
    "type": "form",
    "sections": [
      {
        "title": "网络设置",
        "fields": [
          {
            "key": "listen_addresses",
            "label": "监听地址",
            "type": "text",
            "default": "/ip4/0.0.0.0/tcp/9000"
          },
          {
            "key": "max_peers",
            "label": "最大 peer 数",
            "type": "number",
            "default": 50
          }
        ]
      }
    ]
  }
}
```

### 5.3 生命周期

模块生命周期严格遵循以下状态机（与 [SPEC-Tech-Core.md §4.2 模块生命周期](SPEC-Tech-Core.md) 一致）：

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
                │ load_module (for each module entry)
                ▼
        ┌───────────────┐
        │    INIT       │  加载动态库 → 版本校验 → init()
        └───────┬───────┘
                │ init() 成功
                ▼
        ┌───────────────┐
        │    START      │  start()，启动业务逻辑
        └───────┬───────┘
                │ start() 成功
                ▼
        ┌───────────────┐
        │   RUNNING     │  正常运行，定期健康检查（每 30s）
        └───────┬───────┘
                │
                │ stop() / Core shutdown
                ▼
        ┌───────────────┐
        │     STOP      │  stop()，优雅关闭、释放资源
        └───────┬───────┘
                │
                ▼
        ┌───────────────┐
        │   UNLOADED    │  dlclose() 卸载动态库
        └───────────────┘
```

**生命周期状态枚举（`ModuleLifecycle`）：**

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

**各阶段 Core 行为：**

| 阶段 | Core 行为 | 模块应做事项 |
|------|----------|------------|
| `INIT` | 调用 `Module::init(config)` | 解析配置、分配资源、注册 handler |
| `START` | 调用 `Module::start()` | 启动业务逻辑（连接网络、开始监听等） |
| `RUNNING` | 每 30s 调用 `Module::status()` | 返回当前健康状态 |
| `STOP` | 调用 `Module::stop()` | 优雅关闭、保存状态、释放资源 |

**版本校验（`PluginManager::verify_module_version`）：**

```rust
use semver::{Version, VersionReq};

const CORE_VERSION: &str = env!("CARGO_PKG_VERSION");

pub fn verify_module_version(module_version_str: &str) -> Result<Version> {
    let module_version = Version::parse(module_version_str)
        .map_err(|_| ModuleError::InvalidVersion)?;

    // Core 向后兼容：模块版本 <= Core 版本 + 1 个 minor
    let compatibility_constraint =
        format!("<={}.{}.x", CORE_VERSION.major, CORE_VERSION.minor);

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

| 关系 | 兼容？ |
|------|--------|
| 模块 `1.2.x` ≤ Core `1.2.x` | ✅ 兼容 |
| 模块 `1.1.x` ≤ Core `1.2.x` | ✅ 兼容 |
| 模块 `1.3.x` > Core `1.2.x` | ❌ 不兼容（跨 minor） |
| 模块 `2.0.x` vs Core `1.2.x` | ❌ 不兼容（跨 major） |

### 5.4 健康状态（HealthStatus）

```rust
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
// HealthStatus 统一至 ModuleHealth（见 §5.1），本节引用 ModuleHealth
```

**健康检查结果处理：**

| 状态 | WebSocket 推送 | 处理建议 |
|------|--------------|---------|
| `Healthy` | `module.health` 事件（可选） | 无需干预 |
| `Degraded(reason)` | `module.health` 事件 | 记录日志，尝试自动恢复 |
| `Unhealthy(reason)` | `module.health` 事件 | 记录日志，考虑自动重启模块 |
| `Unknown` | — | 模块未响应健康检查 |

---

## 6. 模块分发格式

### 6.1 ZIP 包结构

每个模块以 ZIP 格式分发，命名规范：

```
Cloudfin-Module-{Type}-{OS}-{Name}-v{YYYYMMDD}-{NNN}.zip
```

**其中：**

| 占位符 | 含义 | 示例 |
|--------|------|------|
| `Type` | 模块类型 | `Network` / `Encrypt` / `Sync` |
| `OS` | 操作系统 | `Linux` / `Windows` / `MacOS` |
| `Name` | 模块名称 | `P2P` / `TOR` / `CRDT` / `crypto` / `Storage` |
| `YYYYMMDD` | 发布日期 | `20260405` |
| `NNN` | 当日迭代序号（从 001） | `001` |

> **注意：** Linux 适用于 Android 和 Linux；MacOS 适用于 macOS 和 iOS。

**ZIP 包内文件结构：**

```
Cloudfin-Module-Network-Linux-P2P-v20260405-001.zip
├── Cloudfin-Module-Network-Linux-P2P-v20260405-001.so    # 模块二进制
└── Cloudfin-Module-Network-Linux-P2P-v20260405-001.so.json  # Manifest
```

**Manifest（`.so.json`）内包含的字段（与 §5.2 Schema 一致）：**

```json
{
  "id": "cloudfin.p2p",
  "name": "P2P Networking",
  "version": "0.1.0",
  "description": "...",
  "author": "...",
  "platform": ["linux", "android"],
  "permissions": ["network", "file_system"],
  "api_version": "^1.0",
  "ui": { ... }
}
```

### 6.2 安装目录

模块安装后存放于：

```
{CoreRoot}/Cloudfin/
└── modules/                              # 模块文件目录
    ├── Cloudfin-Module-Network-Linux-P2P-v20260405-001.so
    ├── Cloudfin-Module-Network-Linux-P2P-v20260405-001.so.json
    ├── Cloudfin-Module-Sync-Linux-CRDT-v20260405-001.so
    ├── Cloudfin-Module-Sync-Linux-CRDT-v20260405-001.so.json
    └── ...
```

**核心规则：`.so` 二进制文件和对应的 `.so.json` Manifest 文件必须放在同一目录下。**

配置存储在：

```
{CoreRoot}/Cloudfin/config/modules/
    ├── cloudfin.p2p.json
    ├── cloudfin.tor.json
    ├── cloudfin.crdt.json
    └── cloudfin.storage.json
```

运行时数据存储在：

```
{CoreRoot}/Cloudfin/data/
    ├── tor/                 # TOR 模块数据
    ├── storage/            # Storage 模块数据
    └── ...                  # 其他模块数据
```

### 6.3 版本文件命名规范

**Core 版本格式：**

```
Cloudfin-Core-{OS}-{Arch}-v{YYYYMMDD}-{NNN}.{format}
```

**Modules 版本格式：**

```
Cloudfin-Module-{Type}-{OS}-{Name}-v{YYYYMMDD}-{NNN}.zip
```

**UI 版本格式：**

```
Cloudfin-UI-{OS}-{Arch}-v{YYYYMMDD}-{NNN}.{format}
```

**架构标识对照表：**

| CPU 架构 | 标识 | 备注 |
|---------|------|------|
| x86_64 | `amd64` | 桌面 / 服务器主流 |
| arm64 / aarch64 | `arm64` | Apple Silicon / ARM 服务器 |
| arm64-v8a（Android） | `arm64-v8a` | Android 完整架构标识 |

**OS 标识对照表（Modules）：**

| 操作系统 | Modules OS 标识 | 备注 |
|---------|---------------|------|
| Linux | `Linux` | Android 与 Linux 共用 |
| Windows | `Windows` | — |
| macOS / iOS | `MacOS` | macOS 与 iOS 共用 |

**模块类型对照表：**

| Type | 含义 | 对应 Modules |
|------|------|------------|
| `Network` | 通信层 | p2p、tor、i2p |
| `Encrypt` | 加密层 | crypto |
| `Sync` | 同步层 | crdt、storage |

**分发包名完整示例：**

| 模块 | 包名 |
|------|------|
| P2P（Linux） | `Cloudfin-Module-Network-Linux-P2P-v20260405-001.zip` |
| TOR（Linux） | `Cloudfin-Module-Network-Linux-TOR-v20260405-001.zip` |
| crypto（Windows） | `Cloudfin-Module-Encrypt-Windows-crypto-v20260405-001.zip` |
| CRDT（Linux） | `Cloudfin-Module-Sync-Linux-CRDT-v20260405-001.zip` |
| Storage（MacOS） | `Cloudfin-Module-Sync-MacOS-Storage-v20260405-001.zip` |

---

## 附录 A：模块快速参考

### A.1 模块清单

| 模块 ID | 名称 | 层次 | 底层实现 | 许可 |
|--------|------|------|---------|------|
| `cloudfin.p2p` | P2P Networking | 通信层 | libp2p v0.54 | AGPL-3.0 |
| `cloudfin.tor` | TOR Anonymous Network | 通信层 | arti | AGPL-3.0 |
| `cloudfin.i2p` | I2P Tunnel | 通信层 | 待定 | 待定 |
| `cloudfin.crypto` | End-to-End Encryption | 加密层 | WireGuard / Noise | 商业授权 |
| `cloudfin.crdt` | CRDT Document Sync | 同步层 | yrs 0.22 | AGPL-3.0 |
| `cloudfin.storage` | Persistent Storage | 同步层 | RocksDB / Sled | AGPL-3.0 |

### A.2 关键依赖版本

| 依赖 | 版本 | 所属模块 |
|------|------|---------|
| `libp2p` | v0.54 | p2p |
| `arti` | latest | tor |
| `yrs` | 0.22 | crdt |
| `libloading` | ^0.8 | Core（动态加载） |
| `rocksdb` | latest | storage |
| `sled` | latest | storage（备选） |
| `semver` | ^1.0 | Core（版本校验） |

---

*本文件为 Cloudfin Modules 路线级技术文档（L3），所有实现细节以 L2 [SPEC-Impl-Core.md](SPEC-Impl-Core.md) 和代码为准。如有变更须经评审后更新。*
