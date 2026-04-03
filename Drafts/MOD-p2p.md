# Cloudfin P2P 模块规格

> 版本：v0.1.0-draft  
> 日期：2026-04-03  
> 状态：**草案 — 待审查**  
> 依赖：SPEC-Plugin-Interface.md（插件接口）

---

## 1. 模块概述

### 1.1 什么是 P2P 模块

P2P 模块 = **让设备之间直接建立网络连接**，不依赖中央服务器。

类比：
- **传统方式**：你和别人说话，要通过微信服务器转发
- **P2P 方式**：你和别人直接拉一根网线连起来，不需要服务器

### 1.2 模块基本信息

| 属性 | 值 |
|------|---|
| 模块 ID | `p2p` |
| 模块名称 | P2P Network |
| 模块名称（中文）| P2P 网络 |
| 版本 | 0.1.0 |
| 许可证 | GPL-3.0 |
| 开发者 | Cloudfin Team |
| 依赖 | libp2p（Rust P2P 网络库）|

### 1.3 核心功能

```
┌──────────────────────────────────────────────────────────┐
│                      P2P 模块                             │
│                                                          │
│  节点发现 ──▶ 建立连接 ──▶ 数据传输 ──▶ 断开连接          │
│     │                                    │               │
│     │        自动发现局域网内的节点          │              │
│     │                                    │              │
│     │        自动建立直接连接               │              │
│     │                                    │              │
│     │        高速传输数据                   │              │
│     │                                    │              │
│     └───────────── mDNS / 引导节点 ─────────┘              │
└──────────────────────────────────────────────────────────┘
```

### 1.4 适用范围

**适合使用 P2P 的场景**：

| 场景 | 说明 | 效果 |
|------|------|------|
| **局域网文件传输** | 同一 WiFi 下设备之间传文件 | 速度极快（取决于局域网带宽），无需互联网 |
| **去中心化通信** | 不希望数据经过第三方服务器 | 隐私保护，抗审查 |
| **低延迟实时通信** | 游戏同步、语音/视频通话（同局域网）| 延迟极低（<10ms）|
| **大文件传输** | 两设备间直接传大文件 | 速度由双方带宽决定，无服务器瓶颈 |
| **分布式存储** | 多设备间同步数据 | 去中心化，无单点故障 |

**不适合使用 P2P 的场景**：

| 场景 | 原因 | 替代方案 |
|------|------|---------|
| **跨 NAT 的公网通信** | 双方都在私有 NAT 后，穿透成功率仅 60-80% | Tor / I2P 模块 |
| **需要 100% 可靠性** | NAT 穿透可能失败 | 中继模式（速度较慢）|
| **隐私要求极高** | 直连会暴露双方 IP | Tor（隐藏 IP）/ I2P（洋葱路由）|
| **弱网络环境** | 移动网络 NAT 类型复杂，穿透困难 | 云端中继 |
| **单设备使用** | 没有对等节点，P2P 无意义 | 存储模块直接本地操作 |

**典型使用流程**：

```
场景：手机A 和 手机B 在不同网络下通过 P2P 传输文件

第一步：各自启动 P2P 模块
  ├─ 手机A：P2P 模块启动，监听 19000 端口
  ├─ 手机B：P2P 模块启动，监听 19000 端口
  └─ 各自获取 PeerId：
       手机A → QmNvTvdqEbNyCb3ArW9TWXF7E3N1z3Kdsk8K1QxC1X3mX
       手机B → QmXgaoyTjqxJnZ8Y8Vv9MX1YW2d7Y3sLk2mR5oW9Zs4cD

第二步：通过引导节点交换地址
  ├─ 手机A 连接引导节点，宣告"我在 QmNvTvdq..."
  ├─ 手机B 连接同一引导节点，宣告"我在 QmXgaoyT..."
  └─ 引导节点通知双方对方的地址

第三步：NAT 穿透尝试
  ├─ 成功 → 直连建立（高速）
  └─ 失败 → 降级到中继模式（可用但较慢）

第四步：数据传输
  └─ 手机A → 手机B：send { data: 文件内容 }
       └─ 手机B 收到 data_received 事件
```

### 1.5 技术选型

### 1.4 技术选型

| 组件 | 技术 | 说明 |
|------|------|------|
| P2P 核心库 | libp2p | Rust 最成熟的 P2P 网络库 |
| 传输层 | TCP / WebSocket | 兼容 NAT 的传输协议 |
| 发现协议 | mDNS（局域网发现）+ 引导节点 | 自动发现节点 |
| 加密 | Noise Protocol + TLS 1.3 | 链路加密 |
| 协商 | secp256k1 / Ed25519 | 密钥协商 |
| NAT 穿透 | auto NAT / hole punching | 跨 NAT 通信 |

---

## 2. 模块状态机

```
                    ┌─────────────────────────────────────┐
                    │                                     │
                    ▼                                     │
              ┌──────────┐                               │
    ┌─────────│  stopped │◀─────────────────────────┐    │
    │         └────┬─────┘                           │    │
    │              │ start                           │    │
    │              ▼                                 │    │
    │         ┌──────────┐        error     ┌──────┐ │    │
    │         │ starting │─────────────────▶│ error │─┘    │
    │         └────┬─────┘                  └──────┘       │
    │              │                                        │
    │              ▼                                        │
    │         ┌──────────┐        stop          ┌────────┐ │
    └─────────│  running │────────────────────▶│ stopped│─┘
              └────┬─────┘                       └────────┘
                   │
                   │ Peers > 0
                   ▼
              ┌──────────┐
              │ connected │ ← 至少有一个对等节点连接
              └────┬─────┘
                   │ Peers = 0
                   ▼
              （回到 running）
```

### 2.1 状态说明

| 状态 | 说明 |
|------|------|
| `stopped` | 模块未启动，不占用任何资源 |
| `starting` | 启动中，正在初始化网络、监听端口 |
| `running` | 运行中，可以发现和连接节点 |
| `connected` | 运行中，且至少有一个对等节点连接 |
| `error` | 发生错误，模块进入安全停止流程 |

---

## 3. 配置结构

### 3.1 模块配置（manifest.json 中的 config 字段）

```json
{
  "modules": {
    "p2p": {
      "enabled": true,
      "config": {
        "listenAddresses": [
          "/ip4/0.0.0.0/tcp/19000",
          "/ip4/0.0.0.0/udp/19000/quic"
        ],
        "announceAddresses": [
          "/ip4/$PUBLIC_IP/tcp/19000"
        ],
        "maxConnections": 50,
        "connectionTimeout": 30000,
        "idleTimeout": 600000,
        "discovery": {
          "mdns": true,
          "relay": true,
          "bootstrapNodes": [
            "/ip4/192.168.1.100/tcp/19000/p2p/QmPeerIdxxx"
          ]
        },
        "nat": {
          "enabled": true,
          "behavior": "auto",
          "portMapping": "upnp"
        },
        "security": {
          "encryption": "noise",
          "allowlist": []
        }
      }
    }
  }
}
```

### 3.2 配置字段详解

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `listenAddresses` | Array | `["/ip4/0.0.0.0/tcp/19000"]` | 本机监听地址 |
| `announceAddresses` | Array | `[]` | 对外宣告地址（用于公网连接）|
| `maxConnections` | Integer | `50` | 最大连接数 |
| `connectionTimeout` | Integer | `30000` | 连接超时（毫秒）|
| `idleTimeout` | Integer | `600000` | 空闲断开时间（毫秒）|
| `discovery.mdns` | Boolean | `true` | 启用 mDNS 局域网节点发现 |
| `discovery.relay` | Boolean | `true` | 启用中继（用于跨 NAT）|
| `discovery.bootstrapNodes` | Array | `[]` | 引导节点列表 |
| `nat.enabled` | Boolean | `true` | 启用 NAT 穿透 |
| `nat.portMapping` | String | `"upnp"` | UPnP 端口映射 |

---

## 4. 方法注册表

### 4.1 支持的方法

| 方法 | 说明 | 参数 | 返回 |
|------|------|------|------|
| `connect` | 连接到指定节点 | peerId, address | { connected, latency } |
| `disconnect` | 断开指定连接 | peerId | { disconnected } |
| `list_peers` | 列出所有已连接节点 | — | { peers: [...] } |
| `peer_info` | 获取指定节点详细信息 | peerId | { info } |
| `send` | 向节点发送数据 | peerId, data | { sent, bytes } |
| `status` | 获取 P2P 网络状态 | — | { status, peers, connections } |
| `discover` | 触发节点发现 | — | { discovered: count } |

### 4.2 方法详细定义

#### connect

**描述**：连接到指定节点

**参数**：
```json
{
  "peerId": "QmPeerIdxxx",
  "address": "/ip4/192.168.1.100/tcp/19000"
}
```

**peerId**：可选，如果不提供则通过 address 连接
**address**：必填，节点地址，格式 `/ip4/x/tcp/y`

**返回值**：
```json
{
  "connected": true,
  "peerId": "QmPeerIdxxx",
  "latency": 32,
  "connectionType": "direct"
}
```

**latency**：延迟（毫秒），如果无法测量为 null
**connectionType**：`direct`（直连）或 `relayed`（中继）

**错误**：
| code | message | 说明 |
|------|---------|------|
| 404 | PEER_NOT_FOUND | 节点地址无效 |
| 408 | CONNECTION_TIMEOUT | 连接超时 |
| 409 | ALREADY_CONNECTED | 已连接到此节点 |
| 501 | NAT_ERROR | NAT 穿透失败 |

---

#### disconnect

**描述**：断开与指定节点的连接

**参数**：
```json
{
  "peerId": "QmPeerIdxxx"
}
```

**返回值**：
```json
{
  "disconnected": true,
  "peerId": "QmPeerIdxxx"
}
```

---

#### list_peers

**描述**：列出所有已连接的节点

**返回值**：
```json
{
  "peers": [
    {
      "peerId": "QmPeerId001",
      "address": "/ip4/192.168.1.100/tcp/19000",
      "connectedAt": "2026-04-03T10:00:00Z",
      "latency": 32,
      "connectionType": "direct",
      "protocols": ["/p2p/1.0.0"]
    },
    {
      "peerId": "QmPeerId002",
      "address": "/ip4/10.0.0.5/tcp/19000",
      "connectedAt": "2026-04-03T10:01:00Z",
      "latency": null,
      "connectionType": "relayed",
      "protocols": ["/p2p/1.0.0"]
    }
  ],
  "count": 2
}
```

---

#### peer_info

**描述**：获取指定节点的详细信息

**参数**：
```json
{
  "peerId": "QmPeerIdxxx"
}
```

**返回值**：
```json
{
  "peerId": "QmPeerIdxxx",
  "address": "/ip4/192.168.1.100/tcp/19000",
  "connectedAt": "2026-04-03T10:00:00Z",
  "lastActive": "2026-04-03T10:05:00Z",
  "latency": 32,
  "connectionType": "direct",
  "protocols": ["/p2p/1.0.0"],
  "agentVersion": "cloudfin-p2p/0.1.0",
  "publicKey": "base64_encoded..."
}
```

---

#### send

**描述**：向指定节点发送数据

**参数**：
```json
{
  "peerId": "QmPeerIdxxx",
  "data": "base64_encoded_data",
  "timeout": 5000
}
```

**data**：Base64 编码的数据
**timeout**：可选，发送超时（毫秒），默认 5000

**返回值**：
```json
{
  "sent": true,
  "peerId": "QmPeerIdxxx",
  "bytes": 1024,
  "duration": 12
}
```

**duration**：发送耗时（毫秒）

**错误**：
| code | message | 说明 |
|------|---------|------|
| 404 | PEER_NOT_FOUND | 节点不存在 |
| 408 | SEND_TIMEOUT | 发送超时 |
| 500 | SEND_FAILED | 发送失败 |

---

#### status

**描述**：获取 P2P 网络整体状态

**返回值**：
```json
{
  "status": "connected",
  "moduleStatus": "running",
  "listeningAddresses": [
    "/ip4/192.168.1.100/tcp/19000",
    "/ip4/192.168.1.100/udp/19000/quic"
  ],
  "announceAddresses": [
    "/ip4/公网IP/tcp/19000"
  ],
  "peers": {
    "connected": 2,
    "max": 50
  },
  "connections": {
    "active": 2,
    "pending": 0,
    "failed": 0
  },
  "discovery": {
    "mdns": "enabled",
    "relay": "enabled",
    "bootstrapNodes": 3,
    "discoveredPeers": 5
  },
  "nat": {
    "enabled": true,
    "mapped": true,
    "externalPort": 19000
  },
  "statistics": {
    "bytesSent": 1024000,
    "bytesReceived": 2048000,
    "uptime": 3600
  }
}
```

---

#### discover

**描述**：手动触发节点发现

**返回值**：
```json
{
  "discovered": 3,
  "peers": [
    { "peerId": "QmPeerId001", "address": "/ip4/192.168.1.101/tcp/19000" },
    { "peerId": "QmPeerId002", "address": "/ip4/192.168.1.102/tcp/19000" }
  ]
}
```

---

## 5. 事件

### 5.1 P2P 模块推送的事件

| 事件 | 说明 | 触发时机 |
|------|------|---------|
| `p2p.started` | P2P 模块启动完成 | 模块状态变为 running |
| `p2p.stopped` | P2P 模块停止 | 模块状态变为 stopped |
| `p2p.error` | P2P 模块错误 | 发生可捕获错误 |
| `p2p.peer_discovered` | 发现新节点 | mDNS 或引导节点发现 |
| `p2p.peer_connected` | 节点连接成功 | 与某节点建立连接 |
| `p2p.peer_disconnected` | 节点断开 | 与某节点断开连接 |
| `p2p.data_received` | 收到数据 | 从某节点收到数据 |
| `p2p.nat_mapped` | NAT 端口映射成功 | UPnP/手动映射成功 |

### 5.2 事件格式

```json
{
  "type": "EVENT",
  "event": "p2p.peer_connected",
  "payload": {
    "peerId": "QmPeerIdxxx",
    "address": "/ip4/192.168.1.100/tcp/19000",
    "latency": 32,
    "connectionType": "direct"
  },
  "timestamp": "2026-04-03T10:00:00Z"
}
```

---

## 6. 数据格式

### 6.1 Peer ID 格式

P2P 模块使用 libp2p 的 PeerId，格式为 Base58 编码的 protobuf：

```
QmPeerIdxxx...
```

示例：
```
QmNvTvdqEbNyCb3ArW9TWXF7E3N1z3Kdsk8K1QxC1X3mX
```

### 6.2 地址格式

遵循 libp2p 的多地址格式：

| 类型 | 格式 | 示例 |
|------|------|------|
| IPv4 TCP | `/ip4/x.x.x.x/tcp/port` | `/ip4/192.168.1.100/tcp/19000` |
| IPv4 UDP QUIC | `/ip4/x.x.x.x/udp/port/quic` | `/ip4/192.168.1.100/udp/19000/quic` |
| DNS | `/dns/hostname/tcp/port` | `/dns/example.com/tcp/19000` |
| WebSocket | `/ip4/x.x.x.x/tcp/port/ws` | `/ip4/192.168.1.100/tcp/19000/ws` |
| 中继 | `/p2p-circuit/p2p/PeerId` | `/p2p-circuit/p2p/QmPeerIdxxx` |

---

## 7. NAT 穿透

### 7.1 穿透策略

```
┌─────────────────────────────────────────────────────┐
│                 NAT 穿透优先级                        │
│                                                      │
│  1. 直连（无 NAT）                                    │
│     ↓ NAT 存在                                       │
│  2. 自动 NAT（hole punching）                         │
│     ↓ 失败                                           │
│  3. 中继（relay）                                    │
│     ↓ 用户配置                                       │
│  4. UPnP 端口映射                                    │
└─────────────────────────────────────────────────────┘
```

### 7.2 NAT 穿透说明

| 方式 | 适用场景 | 成功率 |
|------|---------|-------|
| 直连 | 两端至少有一端在公网 | 100% |
| Hole punching | 双方都在 NAT 后但不是对称 NAT | 60-80% |
| 中继 | 对称 NAT 或所有穿透失败后 | 100% |
| UPnP | NAT 设备支持 UPnP | 50-70% |

---

## 8. 安全模型

### 8.1 加密

| 层级 | 协议 | 说明 |
|------|------|------|
| 链路加密 | Noise Protocol | 所有流量加密 |
| 身份验证 | libp2p secio | 节点身份验证 |
| 密钥交换 | secp256k1 / Ed25519 | 安全密钥协商 |

### 8.2 连接安全

- 所有连接使用 TLS 1.3 或等价加密
- 节点身份通过公钥验证
- 支持连接白名单（allowlist）

---

## 9. 示例

### 9.1 典型使用流程

```
场景：两台手机（手机A、手机B）在不同网络下通过 P2P 传输文件

第一步：各自启动 P2P 模块
─────────────────────────────────────────
App：modules.load { moduleId: "p2p" }
     ↓
P2P 模块启动，监听 19000 端口
     ↓
各自获取自己的 PeerId：
  手机A：QmNvTvdqEbNyCb3ArW9TWXF7E3N1z3Kdsk8K1QxC1X3mX
  手机B：QmXgaoyTjqxJnZ8Y8Vv9MX1YW2d7Y3sLk2mR5oW9Zs4cD

第二步：通过引导节点交换地址
─────────────────────────────────────────
手机A 连接到引导节点，宣告自己：我在这里（QmNvTvdq...）
手机B 连接到同一个引导节点，宣告自己：我在这里（QmXgaoyT...）
     ↓
引导节点通知双方对方的地址
     ↓
手机A 获知手机B 的地址：/ip4/手机B公网IP/tcp/19000

第三步：NAT 穿透尝试
─────────────────────────────────────────
手机A 和 手机B 互相发送 hole punching 包
     ↓
成功 → 直连建立
失败 → 降级到中继

第四步：直连传输
─────────────────────────────────────────
手机A → 手机B：send { peerId: "QmXgaoyT...", data: "文件内容base64" }
     ↓
手机B 收到 data_received 事件
     ↓
手机B 返回确认
```

### 9.2 配置示例

```json
{
  "modules": {
    "p2p": {
      "enabled": true,
      "config": {
        "listenAddresses": [
          "/ip4/0.0.0.0/tcp/19000",
          "/ip4/0.0.0.0/udp/19000/quic"
        ],
        "maxConnections": 50,
        "discovery": {
          "mdns": true,
          "relay": true,
          "bootstrapNodes": [
            "/ip4/你的公网服务器/tcp/19000/p2p/Qm引导节点PeerId"
          ]
        },
        "nat": {
          "enabled": true,
          "behavior": "auto"
        }
      }
    }
  }
}
```

---

## 10. manifest.json

```json
{
  "manifestVersion": "1.0",
  "module": {
    "id": "p2p",
    "name": "P2P Network",
    "nameZh": "P2P 网络",
    "version": "0.1.0",
    "description": "P2P peer discovery and direct connection management",
    "descriptionZh": "P2P 节点发现与直连管理",
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
    "native": ["libp2p"],
    "rust": ["libp2p", "tokio", "serde"],
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
    "output": "modules/libcloudfin_p2p.so",
    "targets": [
      "x86_64-unknown-linux-gnu",
      "aarch64-unknown-linux-gnu",
      "aarch64-apple-darwin",
      "aarch64-linux-android",
      "x86_64-pc-windows-msvc"
    ]
  }
}
```

---

## 11. 审查清单

```
□ Module trait 全部 8 个方法已实现
□ 7 个 P2P 方法已定义（connect/disconnect/list_peers/peer_info/send/status/discover）
□ 所有方法的 JSON Schema 完整
□ 9 种事件类型已定义
□ 模块状态机 5 个状态转换正确
□ 配置字段完整且有默认值
□ NAT 穿透逻辑正确（直连 > hole punching > 中继 > UPnP）
□ 加密方案（Noise + TLS）已说明
□ PeerId 和地址格式已定义
□ manifest.json 所有必填字段完整
□ 许可证为 GPL-3.0（传染性）
□ 示例流程可执行
```

---

*本文档由 墨尘 🌙 编写*  
*最后更新：2026-04-03*
