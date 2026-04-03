# Cloudfin I2P 模块规格

> 版本：v0.1.0-draft  
> 日期：2026-04-03  
> 状态：**草案 — 待审查**  
> 依赖：SPEC-Plugin-Interface.md（插件接口）

---

## 1. 模块概述

### 1.1 什么是 I2P 模块

I2P 模块 = **通过 I2P 匿名网络进行通信**，保护网络隐私，比 Tor 更适合某些场景。

类比：
- **Tor**：像一条高速公路，三层加密，每站换车（节点）
- **I2P**：像一个地下隧道网络，所有流量在隧道内加密端到端传输

### 1.2 模块基本信息

| 属性 | 值 |
|------|---|
| 模块 ID | `i2p` |
| 模块名称 | I2P Protocol |
| 模块名称（中文）| I2P 协议 |
| 版本 | 0.1.0 |
| 许可证 | AGPL-3.0 |
| 开发者 | Cloudfin Team |
| 依赖 | i2p（Rust I2P 实现，疑似 i2pd）|

### 1.3 I2P vs Tor 对比

| 特性 | Tor | I2P |
|------|-----|-----|
| 架构 | 电路式（3跳）| 隧道式（双向隧道）|
| 出口流量 | 仅出口节点能看到明文 | 端到端加密，出口节点也看不到明文 |
| 适用方向 | 浏览外部网站（出口导向）| 内部服务、点对点通信（入口导向）|
| 延迟 | 中等 | 较低（隧道复用）|
| 带宽 | 有限（志愿者节点）| 较好（参与节点共享带宽）|
| 抗审查 | 较好（有桥接）| 更好（协议特征不明显）|
| NAT 穿透 | 需要中继 | 原生支持入站连接 |

### 1.4 适用范围

**适合使用 I2P 的场景**：

| 场景 | 说明 | 效果 |
|------|------|------|
| **I2P 内部服务** | 托管在 I2P 网络内的服务 | 服务无法被外部追踪 |
| **匿名文件共享** | I2P 内置的共享协议 | 去中心化匿名传输 |
| **点对点通信** | 即时消息、语音（I2P 内）| 端到端加密，无法被窃听 |
| **对抗网络封锁** | I2P 协议特征不明显 | 比 Tor 更难被封锁 |
| **中国等高封锁地区** | Tor 桥接不稳定 | I2P 更隐蔽，适合长期使用 |
| **P2P 的底层传输** | 为其他 P2P 协议提供匿名通道 | 比 Tor 更适合双向通信 |

**不适合使用 I2P 的场景**：

| 场景 | 原因 | 替代 |
|------|------|------|
| **访问普通互联网** | I2P 主要面向内部网络，访问外部网站较复杂 | Tor（专门做出口）|
| **极低延迟需求** | 隧道建立有额外延迟 | 直接连接或 VPN |
| **单点可靠性** | I2P 节点可能不稳定 | Tor（更稳定的全球节点）|
| **访问 clearnet 网站** | 需要额外配置出口代理 | Tor 出口更方便 |

**典型使用流程**：

```
场景：在 I2P 网络内建立匿名文件共享

第一步：启动 I2P 模块
  └─ modules.load { moduleId: "i2p" }
       ├─ 初始化 I2P 网络参与
       ├─ 生成 I2P  Destination（相当于匿名身份）
       └─ 建立入站/出站隧道

第二步：获取本机 I2P Destination
  └─ modules.call i2p.get_destination
       └─ 返回：abc123...i2p（I2P 地址，可分享给他人）

第三步：其他 I2P 用户连接你
  └─ 对方通过你的 Destination 连接
       └─ 通过 Core 路由，不直接连端口
```

### 1.5 技术选型

| 组件 | 技术 | 说明 |
|------|------|------|
| I2P 核心 | i2pd（C++）或 rust-i2p | I2P 网络客户端 |
| SAM 协议 | i2pd SAM | 提供 TCP 到 I2P 的桥接 |
| 隧道类型 | CLIENT（出站）+ SERVER（入站）| 双向通信 |
| 加密 | ECIES-X25519 | 端到端加密 |
| 传输 | NTCP2 / SSU2 | I2P 传输协议 |

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
    │         │  booting │─────────────────▶│ error │─┘    │
    │         └────┬─────┘                  └──────┘       │
    │              │                                        │
    │              ▼                                        │
    │         ┌──────────┐        网络不可达               │
    │         │ reseeding │◀────────────────────┐         │
    │         └────┬─────┘                      │         │
    │              │ reseed 成功                │         │
    │              ▼                            │         │
    │         ┌──────────┐        stop         │         │
    └─────────│  running │──────────────────────▶│ stopped │
              └────┬─────┘                      └─────────┘
                   │
                   │ 隧道建立完成
                   ▼
              ┌──────────┐
              │  tunneled │ ← 可进行 I2P 通信
              └──────────┘
```

### 2.1 状态说明

| 状态 | 说明 |
|------|------|
| `stopped` | 模块未启动 |
| `booting` | 正在初始化，加入 I2P 网络 |
| `reseeding` | 正在从种子节点获取网络信息 |
| `running` | 运行中，已加入 I2P 网络 |
| `tunneled` | 运行中，已建立入站/出站隧道，可通信 |
| `error` | 发生错误 |

### 2.2 隧道概念

```
I2P 的通信单位是"隧道"（Tunnel）：

出站隧道（Outbound）：你的设备 → I2P 网络 → 目标
入站隧道（Inbound）：发送者 → I2P 网络 → 你的设备

完整通信需要一对隧道：
┌─────────┐    Outbound     ┌─────────┐    Inbound     ┌─────────┐
│ 你的设备 │ ──────────────▶ │ I2P 网络 │ ◀──────────── │ 对方设备 │
└─────────┘                  └─────────┘               └─────────┘
     ◀──────────────────────────────┘
              端到端加密（ECIES-X25519）
```

---

## 3. 配置结构

### 3.1 模块配置

```json
{
  "modules": {
    "i2p": {
      "enabled": false,
      "config": {
        "samPort": 7656,
        "ntcp2Port": 9321,
        "ssu2Port": 9322,
        "dataDirectory": "/data/data/com.cloudfin/files/i2p",
        "tunnels": {
          "inboundQuantity": 3,
          "outboundQuantity": 3,
          "inboundBackupQuantity": 1,
          "outboundBackupQuantity": 1
        },
        "reseed": {
          "enabled": true,
          "useManualPeers": false,
          "manualPeers": []
        },
        "bandwidth": {
          "share": true,
          "sharePercentage": 50,
          "inboundLimit": 0,
          "outboundLimit": 0
        },
        "netDb": {
          "enabled": true,
          "published": true
        }
      }
    }
  }
}
```

### 3.2 配置字段详解

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `samPort` | Integer | `7656` | SAM API 端口（模块内部使用）|
| `ntcp2Port` | Integer | `9321` | NTCP2 传输端口（内部）|
| `ssu2Port` | Integer | `9322` | SSU2 传输端口（内部）|
| `tunnels.inboundQuantity` | Integer | `3` | 入站隧道数量 |
| `tunnels.outboundQuantity` | Integer | `3` | 出站隧道数量 |
| `reseed.enabled` | Boolean | `true` | 是否启用种子节点 |
| `reseed.useManualPeers` | Boolean | `false` | 是否使用手动 peer |
| `bandwidth.share` | Boolean | `true` | 是否分享带宽（成为中继）|
| `bandwidth.sharePercentage` | Integer | `50` | 分享带宽比例 |
| `netDb.published` | Boolean | `true` | 是否发布到网络数据库 |

---

## 4. 方法注册表

### 4.1 支持的方法

| 方法 | 说明 | 参数 | 返回 |
|------|------|------|------|
| `connect` | 启动 I2P 并建立隧道 | — | { connected, destination } |
| `disconnect` | 关闭 I2P | — | { disconnected } |
| `status` | 获取 I2P 连接状态 | — | { status, tunnels, peers } |
| `get_destination` | 获取本机 I2P 地址 | — | { destination, base32, base64 } |
| `create_tunnel` | 创建指定类型的隧道 | tunnelType, target | { tunnelId, localPort } |
| `list_tunnels` | 列出所有隧道 | — | { tunnels: [...] } |
| `delete_tunnel` | 删除隧道 | tunnelId | { deleted: true } |
| `send_message` | **通过 Core 调用**：发送 I2P 内消息 | destination, payload | { sent: true, messageId } |
| `proxy` | **通过 Core 调用**：代理 TCP 连接（供其他模块使用）| host, port, data, timeout | { success, response, duration } |

### 4.2 方法详细定义

#### connect

**描述**：启动 I2P 模块，加入 I2P 网络

**参数**：无

**返回值**：
```json
{
  "connected": true,
  "destination": "abc123...i2p",
  "base32": "abc123...b32.i2p",
  "uptime": 0,
  "tunnels": {
    "inbound": 3,
    "outbound": 3
  }
}
```

---

#### get_destination

**描述**：获取本机在 I2P 网络中的地址（Destination）

**返回值**：
```json
{
  "destination": "abc123def456...i2p",
  "base32": "abc123def456...b32.i2p",
  "base64": "YmFiYzEyM2RlZjQ1Ni...==",
  "createdAt": "2026-04-03T10:00:00Z",
  "tunnelId": "tunnel_inbound_001"
}
```

**用途**：
- 将此地址分享给其他人，让他们能连接你
- 其他 I2P 用户通过此地址建立入站隧道

---

#### send_message（模块间通信接口）

**描述**：通过 I2P 网络发送端到端加密消息（供其他模块调用）

**调用方式**（其他模块通过 Core 调用）：
```json
{
  "id": "msg_000001",
  "type": "REQUEST",
  "action": "modules.call",
  "payload": {
    "moduleId": "i2p",
    "method": "send_message",
    "args": {
      "destination": "abc123...i2p",
      "payload": "base64_encoded_message",
      "timeout": 30000
    }
  }
}
```

**返回值**：
```json
{
  "id": "msg_000001",
  "type": "RESPONSE",
  "result": {
    "sent": true,
    "messageId": "msg_abc123",
    "duration": 520
  }
}
```

**错误码**：
| code | message | 说明 |
|------|---------|------|
| 404 | DESTINATION_NOT_FOUND | 目标不可达 |
| 408 | MESSAGE_TIMEOUT | 消息发送超时 |
| 500 | I2P_NOT_RUNNING | I2P 模块未运行 |
| 501 | TUNNEL_NOT_ESTABLISHED | 没有可用隧道 |

---

#### proxy（模块间通信接口）

**描述**：通过 I2P 网络代理 TCP 连接（供其他模块调用）

**调用方式**：
```json
{
  "id": "msg_000002",
  "type": "REQUEST",
  "action": "modules.call",
  "payload": {
    "moduleId": "i2p",
    "method": "proxy",
    "args": {
      "host": "service.i2p",
      "port": 80,
      "data": "base64_encoded_request",
      "timeout": 30000
    }
  }
}
```

**返回值**：
```json
{
  "id": "msg_000002",
  "type": "RESPONSE",
  "result": {
    "success": true,
    "response": "base64_encoded_response",
    "duration": 850
  }
}
```

---

#### create_tunnel

**描述**：创建新的隧道

**参数**：
```json
{
  "tunnelType": "http",
  "target": "localhost:8080"
}
```

**tunnelType**：
| 类型 | 说明 |
|------|------|
| `client` | 通用出站隧道 |
| `server` | 入站隧道（暴露本地服务）|
| `http` | HTTP 代理隧道 |
| `irc` | IRC 隧道 |
| `datagram` | 数据报隧道（无连接）|

**返回值**：
```json
{
  "tunnelId": "tunnel_http_001",
  "tunnelType": "http",
  "localPort": 7777,
  "target": "localhost:8080",
  "createdAt": "2026-04-03T10:00:00Z"
}
```

---

## 5. 事件

### 5.1 I2P 模块推送的事件

| 事件 | 说明 | 触发时机 |
|------|------|---------|
| `i2p.started` | I2P 模块启动 | 模块状态变为 running |
| `i2p.stopped` | I2P 模块停止 | 模块状态变为 stopped |
| `i2p.error` | I2P 模块错误 | 发生可捕获错误 |
| `i2p.peer_discovered` | 发现新节点 | 网络数据库发现新节点 |
| `i2p.tunnel_created` | 隧道创建成功 | 新隧道建立完成 |
| `i2p.tunnel_destroyed` | 隧道关闭 | 隧道被关闭或超时 |
| `i2p.tunnel_failed` | 隧道建立失败 | 隧道建立失败 |
| `i2p.message_received` | 收到消息 | 收到其他 I2P 用户的消息 |
| `i2p.bandwidth_update` | 带宽使用更新 | 带宽使用统计更新 |

### 5.2 事件格式

```json
{
  "type": "EVENT",
  "event": "i2p.tunnel_created",
  "payload": {
    "tunnelId": "tunnel_http_001",
    "tunnelType": "http",
    "localPort": 7777
  },
  "timestamp": "2026-04-03T10:00:00Z"
}
```

---

## 6. 模块间通信（通过 Core）

> **核心原则**：所有模块间通信必须通过 Core，`modules.call` 是唯一入口。

### 6.1 通信架构

```
CRDT 模块              Core                    I2P 模块
    │                  │                         │
    │ ──▶ modules.call │ ──▶ module.call() ────▶│
    │                  │                         │
    │                  │◀── result ─────────────│
    │◀── result ───────│                         │
```

**使用场景**：
- CRDT 模块需要跨设备同步数据，但不想暴露真实 IP
- P2P 模块在 I2P 网络内发现节点、建立连接
- 其他任何模块需要 I2P 网络通信能力

### 6.2 内部 SAM 端口（不对外）

| 协议 | 地址 | 说明 |
|------|------|------|
| SAM | `127.0.0.1:7656` | I2P SAM 协议（Core 内部使用）|
| NTCP2 | `127.0.0.1:9321` | NTCP2 传输（内部）|
| SSU2 | `127.0.0.1:9322` | SSU2 传输（内部）|

**这些端口不暴露给其他模块**，仅 I2P 模块内部使用。

---

## 7. 安全模型

### 7.1 加密

| 层级 | 协议 | 说明 |
|------|------|------|
| 隧道加密 | AES-256-GCM | 隧道内所有流量加密 |
| 端到端加密 | ECIES-X25519 | 消息内容端到端加密，只有收发双方能解密 |
| 身份隐藏 | I2P Destination | 类似洋葱地址，无法关联到真实身份 |

### 7.2 隐私保护

| 威胁 | 保护措施 |
|------|---------|
| 流量分析 | 隧道流量加密，无法识别内容 |
| 入站连接追踪 | Destination 是加密标识，无法反向追踪 |
| 中继节点监控 | 端到端加密，中间节点看不到明文 |
| 时序分析 | 隧道定期更换（默认 10 分钟）|

---

## 8. I2P vs Tor 使用场景

| 场景 | 推荐 | 说明 |
|------|------|------|
| 访问普通网站 | Tor | I2P 主要面向内部服务 |
| 托管匿名服务 | I2P | Destination 更隐蔽 |
| 即时消息（I2P 内）| I2P | 端到端加密，原生支持 |
| 匿名文件传输 | I2P | datagram 隧道适合 |
| 对抗高封锁网络 | I2P | 协议特征更不明显 |
| 跨境通信 | Tor | 节点覆盖更广 |

---

## 9. manifest.json

```json
{
  "manifestVersion": "1.0",
  "module": {
    "id": "i2p",
    "name": "I2P Protocol",
    "nameZh": "I2P 协议",
    "version": "0.1.0",
    "description": "I2P anonymous network protocol wrapper",
    "descriptionZh": "I2P 匿名网络协议封装",
    "icon": "modules/icons/i2p.png"
  },
  "developer": {
    "name": "Cloudfin Team",
    "email": "dev@cloudfin.io",
    "website": "https://cloudfin.io",
    "github": "https://github.com/indivisible2025/cloudfin-mod-i2p"
  },
  "license": {
    "spdx": "AGPL-3.0",
    "name": "GNU Affero General Public License v3.0",
    "url": "https://www.gnu.org/licenses/agpl-3.0.html",
    "commercial": false
  },
  "links": {
    "source": "https://github.com/indivisible2025/cloudfin-mod-i2p",
    "documentation": "https://docs.cloudfin.io/modules/i2p",
    "bugReport": "https://github.com/indivisible2025/cloudfin-mod-i2p/issues",
    "changelog": "https://github.com/indivisible2025/cloudfin-mod-i2p/blob/main/CHANGELOG.md"
  },
  "compatibility": {
    "coreMinVersion": "0.1.0",
    "coreMaxVersion": "0.9.9",
    "platforms": ["linux", "macos", "android", "windows"]
  },
  "dependencies": {
    "native": ["i2pd"],
    "rust": ["tokio", "serde"],
    "system": []
  },
  "permissions": {
    "canCall": ["p2p", "crdt", "crypto"],
    "canSubscribe": ["p2p", "crdt"],
    "canAccessConfig": false
  },
  "security": {
    "sandboxed": true,
    "networkAccess": "module_only",
    "fileAccess": "module_private"
  },
  "build": {
    "output": "modules/libcloudfin_i2p.so",
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

## 10. 审查清单

```
□ Module trait 全部 8 个方法已实现
□ 9 个 I2P 方法已定义（含 send_message / proxy 供其他模块调用）
□ 所有方法的 JSON Schema 完整
□ 隧道机制（入站/出站）正确说明
□ Destination 概念正确
□ SAM/NTCP2/SSU2 端口标注为内部实现
□ send_message / proxy 定义为外部接口（通过 Core 调用）
□ 事件推送 9 种类型完整
□ 模块状态机 6 个状态正确
□ I2P vs Tor 对比已说明
□ 适用范围/情况已列出
□ manifest.json 所有必填字段完整
□ 许可证为 AGPL-3.0（传染性）
□ 安全模型（端到端加密）已说明
```

---

*本文档由 墨尘 🌙 编写*  
*最后更新：2026-04-03*
