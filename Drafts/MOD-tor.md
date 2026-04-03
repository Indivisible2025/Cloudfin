# Cloudfin Tor 模块规格

> 版本：v0.1.0-draft  
> 日期：2026-04-03  
> 状态：**草案 — 待审查**  
> 依赖：SPEC-Plugin-Interface.md（插件接口）

---

## 1. 模块概述

### 1.1 什么是 Tor 模块

Tor 模块 = **通过 Tor 匿名网络进行通信**，隐藏真实 IP，保护隐私。

类比：
- **不用 Tor**：你上网 → 网站直接看到你的 IP 地址 → 知道你在哪、是谁
- **用 Tor**：你上网 → 经过 3 层加密节点跳转 → 网站只能看到最后一个节点的 IP → 无法追踪你

### 1.2 模块基本信息

| 属性 | 值 |
|------|---|
| 模块 ID | `tor` |
| 模块名称 | Tor Protocol |
| 模块名称（中文）| Tor 协议 |
| 版本 | 0.1.0 |
| 许可证 | AGPL-3.0 |
| 开发者 | Cloudfin Team |
| 依赖 | arti（Rust Tor 实现）|

### 1.3 Tor 工作原理

```
┌─────────────────────────────────────────────────────────────┐
│                      Tor 匿名网络                            │
│                                                              │
│  你的设备 ──▶ Entry Node（入口节点）─▶ Middle Node（中间节点） │
│                              │                               │
│                         加密层                               │
│                              │                               │
│                         Exit Node（出口节点）                 │
│                              │                               │
│                         目标网站                             │
│                                                              │
│  特点：                                                      │
│  ├─ 三层加密（每经过一个节点剥一层）                          │
│  ├─ 每个节点只知道上一个和下一个，不知道完整路径              │
│  └─ 出口节点能看到明文流量，但不知道是谁发的                   │
└─────────────────────────────────────────────────────────────┘
```

### 1.4 适用范围

**适合使用 Tor 的场景**：

| 场景 | 说明 | 效果 |
|------|------|------|
| **隐藏 IP 地址** | 上网不希望被追踪 | 网站只能看到 Tor 出口节点 IP |
| **访问被封锁的网站** | 某些网站在某些地区无法访问 | 通过 Tor 绕过封锁 |
| **隐私保护通信** | 需要匿名发送消息 | 接收方不知道你的真实 IP |
| **对抗网络审查** | ISP 或政府封锁了某些网站 | Tor 桥接可以绕过封锁 |
| **P2P 的底层传输** | P2P 模块需要隐私保护 | Tor 为 P2P 提供匿名通道 |

**不适合使用 Tor 的场景**：

| 场景 | 原因 | 替代 |
|------|------|------|
| **高速下载** | Tor 节点带宽有限，速度慢 | 直接连接或 VPN |
| **视频/直播** | 延迟高、带宽低 | 直接连接 |
| **需要稳定连接** | Tor 电路可能中断 | VPN 或直接连接 |
| **大文件传输** | 速度太慢 | P2P 直连或云存储 |
| **本地局域网使用** | 局域网不需要匿名 | 直接连接或 P2P |
| **在中国大陆使用** | Tor 被 GFW 封锁，需要桥接且不稳定 | I2P（更隐蔽）|

**典型使用流程**：

```
场景：通过 Tor 浏览器访问被封锁的网站

第一步：启动 Tor 模块
  └─ modules.load { moduleId: "tor" }
       ├─ 下载 Tor 网络信息（引导节点列表）
       ├─ 建立 Entry Guard（长期入口节点）
       └─ 连接成功 → SOCKS5 代理就绪（127.0.0.1:9050）

第二步：配置应用使用 Tor SOCKS 代理
  └─ P2P / 其他模块 → 连接 127.0.0.1:9050

第三步：访问目标网站
  └─ 目标网站只能看到 Tor Exit Node 的 IP
       └─ 不知道是你发的
```

### 1.5 技术选型

| 组件 | 技术 | 说明 |
|------|------|------|
| Tor 核心 | arti | Rust 实现的 Tor 协议，比官方 Go 版更安全 |
| SOCKS5 代理 | arti client | 提供标准 SOCKS5 接口，其他模块通过它上网 |
| 目录服务器 | 官方 Tor 目录 | 获取网络节点信息 |
| 桥接 | obfs4 / Snowflake | 混淆协议，绕过封锁 |

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
    │         │ booting  │─────────────────▶│ error │─┘    │
    │         └────┬─────┘                  └──────┘       │
    │              │                                        │
    │              ▼                                        │
    │         ┌──────────┐        bootstrap 失败            │
    │         │ bootstrap │◀──────────────────────┐         │
    │         └────┬─────┘                        │         │
    │              │ bootstrap 成功                │         │
    │              ▼                               │         │
    │         ┌──────────┐        stop            │         │
    └─────────│  running │───────────────────────▶│ stopped │
              └────┬─────┘                        └─────────┘
                   │
                   │ Tor 电路建立
                   ▼
              ┌──────────┐
              │ circuits │ ← SOCKS5 代理可使用
              └──────────┘
```

### 2.1 状态说明

| 状态 | 说明 |
|------|------|
| `stopped` | 模块未启动 |
| `booting` | 正在启动，从目录服务器获取网络信息 |
| `bootstrap` | 正在建立初始连接（连接 Entry Guard）|
| `running` | 运行中，SOCKS5 代理就绪 |
| `circuits` | 运行中，已有活跃电路，可代理流量 |
| `error` | 发生错误 |

### 2.2 Bootstrap 过程

```
Bootstrap 是 Tor 模块启动时必经的过程：

1. 下载目录（3-5 秒）
   └─ 从目录服务器获取所有节点信息

2. 选择 Entry Guard（入口守卫）
   └─ 选择 1-2 个长期信任的入口节点
   └─ 避免流量被主动攻击

3. 建立初始电路（3-10 秒）
   └─ Entry Guard → Middle → Exit
   └─ 建立 3 层加密隧道

4. SOCKS5 代理就绪
   └─ 127.0.0.1:9050 开始接受连接

总耗时：5-15 秒（取决于网络）
```

---

## 3. 配置结构

### 3.1 模块配置

```json
{
  "modules": {
    "tor": {
      "enabled": false,
      "config": {
        "socksPort": 9050,
        "controlPort": 9051,
        "dataDirectory": "/data/data/com.cloudfin/files/tor",
        "bridges": {
          "enabled": false,
          "type": "obfs4",
          "bridges": [
            "obfs4 1.2.3.4:443 ..."
          ]
        },
        "entryGuards": {
          "enabled": true,
          "persist": true,
          "minQuantity": 1,
          "maxQuantity": 3
        },
        "circuits": {
          "maxCircuits": 10,
          "maxHopTimeout": 60000,
          "idleTimeout": 1800000
        },
        "traffic": {
          "maxDownload": 0,
          "maxUpload": 0,
          "throttle": false
        },
        "dns": {
          "enable": true,
          "servers": ["127.0.0.1", "10.191.0.1"]
        }
      }
    }
  }
}
```

### 3.2 配置字段详解

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `socksPort` | Integer | `9050` | SOCKS5 代理端口 |
| `controlPort` | Integer | `9051` | 控制端口（用于控制命令）|
| `dataDirectory` | String | 平台默认 | Tor 数据目录（节点信息、密钥等）|
| `bridges.enabled` | Boolean | `false` | 是否启用桥接 |
| `bridges.type` | String | `"obfs4"` | 桥接混淆类型 |
| `bridges.bridges` | Array | `[]` | 桥接地址列表 |
| `entryGuards.enabled` | Boolean | `true` | 启用入口守卫 |
| `entryGuards.persist` | Boolean | `true` | 持久化入口守卫（加快重启速度）|
| `circuits.maxCircuits` | Integer | `10` | 最大同时电路数 |
| `circuits.maxHopTimeout` | Integer | `60000` | 单跳超时（毫秒）|
| `circuits.idleTimeout` | Integer | `1800000` | 空闲电路超时（毫秒）|
| `traffic.maxDownload` | Integer | `0` | 最大下载速度（0=不限）|
| `traffic.maxUpload` | Integer | `0` | 最大上传速度（0=不限）|
| `dns.enable` | Boolean | `true` | 通过 Tor DNS 解析 |

---

## 4. 方法注册表

### 4.1 支持的方法

| 方法 | 说明 | 参数 | 返回 |
|------|------|------|------|
| `connect` | 启动 Tor 并建立初始电路 | — | { connected, socksPort } |
| `disconnect` | 关闭 Tor 连接 | — | { disconnected } |
| `status` | 获取 Tor 连接状态 | — | { status, bootstrapProgress } |
| `new_circuit` | 建立新的 Tor 电路 | exitCountry | { circuitId, exitCountry } |
| `circuit_info` | 获取当前电路信息 | — | { circuits: [...] } |
| `set_proxy` | 配置上级代理（可选）| proxyUrl | { set: true } |
| `bridge_add` | 添加桥接 | bridge | { added: true } |
| `bridge_remove` | 移除桥接 | bridge | { removed: true } |
| `dns_query` | 通过 Tor 执行 DNS 查询 | hostname | { addresses: [...] } |

### 4.2 方法详细定义

#### connect

**描述**：启动 Tor 模块，建立初始连接

**参数**：无

**返回值**：
```json
{
  "connected": true,
  "socksPort": 9050,
  "controlPort": 9051,
  "bootstrapTime": 8500
}
```

**错误**：
| code | message | 说明 |
|------|---------|------|
| 500 | BOOTSTRAP_FAILED | 无法连接到 Tor 网络（网络问题）|
| 501 | BRIDGE_REQUIRED | 需要桥接才能连接（被封锁）|

---

#### disconnect

**描述**：关闭 Tor 连接

**返回值**：
```json
{
  "disconnected": true,
  "uptime": 3600
}
```

---

#### status

**描述**：获取 Tor 连接状态和引导进度

**返回值**：
```json
{
  "status": "circuits",
  "moduleStatus": "running",
  "bootstrapProgress": 100,
  "socksPort": 9050,
  "controlPort": 9051,
  "entryGuard": {
    "fingerprint": "ABCD1234...",
    "nickname": "Unnamed",
    "establishedAt": "2026-04-03T10:00:00Z"
  },
  "uptime": 3600,
  "traffic": {
    "bytesRead": 1024000,
    "bytesWritten": 2048000
  }
}
```

**status 状态值**：
| 值 | 说明 |
|------|------|
| `stopped` | Tor 未运行 |
| `booting` | 正在启动 |
| `bootstrap` | 正在引导（bootstrapProgress 0-100）|
| `running` | 运行中（但可能还没有活跃电路）|
| `circuits` | 有活跃电路，SOCKS5 可用 |
| `error` | 发生错误 |

---

#### new_circuit

**描述**：建立新的 Tor 电路（可选出口国家）

**参数**：
```json
{
  "exitCountry": "DE"
}
```

**exitCountry**：可选，目标出口节点的国家代码（ISO 3166-1 alpha-2）

**返回值**：
```json
{
  "circuitId": "circuit_001",
  "exitCountry": "DE",
  "exitIp": "1.2.3.4",
  "established": true
}
```

**错误**：
| code | message | 说明 |
|------|---------|------|
| 404 | COUNTRY_NOT_AVAILABLE | 该国家没有可用的出口节点 |

---

#### circuit_info

**描述**：获取所有活跃电路的详细信息

**返回值**：
```json
{
  "circuits": [
    {
      "circuitId": "circuit_001",
      "path": [
        { "node": "EntryGuard1", "ip": "1.2.3.4", "country": "US", "role": "guard" },
        { "node": "MiddleNode1", "ip": "5.6.7.8", "country": "DE", "role": "middle" },
        { "node": "ExitNode1", "ip": "9.10.11.12", "country": "NL", "role": "exit" }
      ],
      "createdAt": "2026-04-03T10:00:00Z",
      "status": "active",
      "bytesRead": 1024,
      "bytesWritten": 2048
    }
  ],
  "count": 1
}
```

---

#### set_proxy

**描述**：配置上级代理（Tor 通过这个代理连接网络）

**参数**：
```json
{
  "proxyUrl": "socks5://127.0.0.1:1080"
}
```

**proxyUrl**：代理地址，支持 socks5/http

**返回值**：
```json
{
  "set": true,
  "proxyUrl": "socks5://127.0.0.1:1080"
}
```

---

#### bridge_add

**描述**：添加 Tor 桥接（用于绕过封锁）

**参数**：
```json
{
  "bridge": "obfs4 1.2.3.4:443 ..."
}
```

**返回值**：
```json
{
  "added": true,
  "bridge": "obfs4 ...",
  "bridgesTotal": 3
}
```

**支持的桥接类型**：
| 类型 | 说明 |
|------|------|
| `obfs4` | 混淆协议，抗深度包检测 |
| `snowflake` | 基于 WebRTC，难以封锁 |
| `meek-azure` | 通过 Azure 云服务代理 |
| `vanilla` | 无混淆的标准桥接 |

---

#### dns_query

**描述**：通过 Tor 网络执行 DNS 查询

**参数**：
```json
{
  "hostname": "example.com"
}
```

**返回值**：
```json
{
  "hostname": "example.com",
  "addresses": [
    "93.184.216.34",
    "2606:2800:220:1::248:1893"
  ],
  "throughTor": true
}
```

---

## 5. 事件

### 5.1 Tor 模块推送的事件

| 事件 | 说明 | 触发时机 |
|------|------|---------|
| `tor.started` | Tor 模块启动 | 模块状态变为 running |
| `tor.stopped` | Tor 模块停止 | 模块状态变为 stopped |
| `tor.error` | Tor 模块错误 | 发生可捕获错误 |
| `tor.bootstrap_progress` | 引导进度更新 | 0%, 25%, 50%, 75%, 100% |
| `tor.circuit_established` | 新电路建立 | 成功建立新电路 |
| `tor.circuit_closed` | 电路关闭 | 某电路被关闭或超时 |
| `tor.circuit_failed` | 电路建立失败 | 建立电路失败 |
| `tor.entry_guard_changed` | 入口守卫变更 | 换了新的入口节点 |
| `tor.traffic_threshold` | 流量达到阈值 | 超过配置的流量限制 |

### 5.2 事件格式

```json
{
  "type": "EVENT",
  "event": "tor.bootstrap_progress",
  "payload": {
    "progress": 75,
    "message": "Establishing a circuit..."
  },
  "timestamp": "2026-04-03T10:00:00Z"
}
```

---

## 6. SOCKS5 代理接口

### 6.1 代理地址

| 协议 | 地址 | 说明 |
|------|------|------|
| SOCKS5 | `127.0.0.1:9050` | 标准 Tor SOCKS5 代理（默认）|
| SOCKS5 | `127.0.0.1:9051` | 控制端口 |
| HTTP | `127.0.0.1:9052` | HTTP 代理（可选）|
| DNS | `127.0.0.1:9053` | DNS 解析（启用 dns.enable 时）|

### 6.2 使用方式

其他模块通过 SOCKS5 代理使用 Tor：

```
P2P 模块想通过 Tor 发送数据：

P2P 模块 ──▶ 127.0.0.1:9050（SOCKS5）
                  │
                  │ 加密，发送到 Tor 网络
                  ▼
             Entry Guard ──▶ Middle ──▶ Exit Node
                                              │
                                              ▼
                                         目标服务器

优点：
├─ P2P 模块不需要知道 Tor 内部实现
├─ 只需要配置 SOCKS5 代理
└─ 可以随时切换到直接连接或其他模块
```

---

## 7. 安全模型

### 7.1 匿名性保护

| 威胁 | 保护措施 |
|------|---------|
| 流量分析 | Tor 使用 TLS 加密，外部无法识别流量内容 |
| 出口节点监控 | 出口节点只知道来源 IP，不知道用户身份 |
| 入口节点攻击 | Entry Guard 长期稳定，减少被攻击概率 |
| 电路分析 | 定期更换电路（默认 10 分钟）|
| DNS 泄露 | DNS 查询默认通过 Tor DNS 解析 |

### 7.2 安全限制

| 限制 | 说明 |
|------|------|
| 不保存连接日志 | Tor 设计上不保存日志 |
| 出口节点可看明文 | 通过 HTTPS 保护明文内容 |
| 某些协议可能泄露 IP | 建议配合 Crypto 模块使用端到端加密 |

---

## 8. manifest.json

```json
{
  "manifestVersion": "1.0",
  "module": {
    "id": "tor",
    "name": "Tor Protocol",
    "nameZh": "Tor 协议",
    "version": "0.1.0",
    "description": "Tor anonymous network protocol wrapper",
    "descriptionZh": "Tor 匿名网络协议封装",
    "icon": "modules/icons/tor.png"
  },
  "developer": {
    "name": "Cloudfin Team",
    "email": "dev@cloudfin.io",
    "website": "https://cloudfin.io",
    "github": "https://github.com/indivisible2025/cloudfin-mod-tor"
  },
  "license": {
    "spdx": "AGPL-3.0",
    "name": "GNU Affero General Public License v3.0",
    "url": "https://www.gnu.org/licenses/agpl-3.0.html",
    "commercial": false
  },
  "links": {
    "source": "https://github.com/indivisible2025/cloudfin-mod-tor",
    "documentation": "https://docs.cloudfin.io/modules/tor",
    "bugReport": "https://github.com/indivisible2025/cloudfin-mod-tor/issues",
    "changelog": "https://github.com/indivisible2025/cloudfin-mod-tor/blob/main/CHANGELOG.md"
  },
  "compatibility": {
    "coreMinVersion": "0.1.0",
    "coreMaxVersion": "0.9.9",
    "platforms": ["linux", "macos", "android", "windows"]
  },
  "dependencies": {
    "native": ["arti"],
    "rust": ["arti", "tokio", "serde"],
    "system": []
  },
  "permissions": {
    "canCall": ["p2p", "crypto"],
    "canSubscribe": ["p2p"],
    "canAccessConfig": false
  },
  "security": {
    "sandboxed": true,
    "networkAccess": "module_only",
    "fileAccess": "module_private"
  },
  "build": {
    "output": "modules/libcloudfin_tor.so",
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

## 9. 审查清单

```
□ Module trait 全部 8 个方法已实现
□ 9 个 Tor 方法已定义
□ 所有方法的 JSON Schema 完整
□ Bootstrap 流程正确（目录下载 → Entry Guard → 建立电路）
□ 桥接（obfs4/snowflake/meek）支持已定义
□ SOCKS5 代理接口已定义
□ 事件推送 9 种类型完整
□ 模块状态机 6 个状态正确
□ 配置字段完整且有默认值
□ manifest.json 所有必填字段完整
□ 许可证为 AGPL-3.0（传染性）
□ 匿名性保护措施已说明
□ 适用范围/情况已列出
```

---

*本文档由 墨尘 🌙 编写*  
*最后更新：2026-04-03*
