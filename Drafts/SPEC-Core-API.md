# Cloudfin Core API 规格

> 版本：v0.1.0-draft  
> 日期：2026-04-03  
> 状态：**草案 — 待审查**  
> 协议：WebSocket + JSON

---

## 1. 概述

### 1.1 协议选择理由

| 方案 | 结论 |
|------|------|
| REST (HTTP + JSON) | ❌ 淘汰：每个操作独立连接，频繁场景 overhead 高，无双向通道 |
| gRPC + Protobuf | ❌ 淘汰：依赖重，调试复杂，不适合 UI 层高频交互 |
| **WebSocket + JSON** | ✅ 采用：双向通道、低 overhead、调试友好、适合本地 IPC |

**最终选择：WebSocket + JSON**

### 1.2 适用场景

- **UI 层 ↔ Core 层**：同一设备，本地通信（Android/iOS/macOS/Linux 均适用）
- **Core 提供 CLI**：可通过同一 WebSocket 连接或 Unix Socket 访问

### 1.3 设计原则

```
一条 WebSocket 连接 = 一个会话（Session）
会话保持，永久连接
请求/响应复用同一通道
Core 可主动推送事件到 UI
```

---

## 2. 连接建立

### 2.1 连接地址

| 平台 | 地址 |
|------|------|
| Android / iOS | `ws://localhost:19001/ws` |
| Linux / macOS / Windows | `ws://localhost:19001/ws` 或 `ws://unix:/run/cloudfin.sock/ws` |
| CLI 工具 | `cloudfinctl status`（内部连接 19001）|

### 2.2 首次连接流程

```
UI                          Core
 │                           │
 │──── CONNECT ──────────────▶│  建立 WebSocket 连接
 │                           │
 │◀─── HELLO ────────────────│  Core 返回版本、能力集
 │                           │
 │──── AUTH ─────────────────▶│  UI 发送认证（设备密钥）
 │                           │
 │◀─── AUTH_OK / AUTH_FAIL ──│  Core 返回认证结果
 │                           │
 │       连接就绪              │
 │                           │
```

### 2.3 HELLO 消息格式

```json
{
  "type": "HELLO",
  "version": "0.1.0",
  "capabilities": ["core", "modules", "events"],
  "sessionId": "s_abc123"
}
```

### 2.4 AUTH 消息格式

```json
{
  "type": "AUTH",
  "deviceId": "android_xxx",
  "deviceKey": "key_xxx",       // 预共享密钥或设备证书
  "appVersion": "1.0.0.debug014"
}
```

**认证响应**：

成功：
```json
{
  "type": "AUTH_OK",
  "sessionId": "s_abc123",
  "expiresAt": null             // 本地连接不过期
}
```

失败：
```json
{
  "type": "AUTH_FAIL",
  "reason": "INVALID_KEY",
  "code": 401
}
```

---

## 3. 消息格式

### 3.1 通用帧格式

所有消息均为 JSON，WebSocket 文本帧传输。

```json
{
  "id": "msg_123456",           // 消息 ID，UI 生成，用于匹配响应
  "type": "REQUEST",            // REQUEST | RESPONSE | EVENT | ERROR
  "action": "core.status",      // 操作标识
  "payload": { ... },            // 请求参数（REQUEST）
  "result": { ... },             // 返回结果（RESPONSE）
  "error": { ... }               // 错误信息（ERROR）
}
```

### 3.2 消息类型

| 类型 | 方向 | 说明 |
|------|------|------|
| REQUEST | UI → Core | 请求操作，需要响应 |
| RESPONSE | Core → UI | 操作结果，匹配 REQUEST 的 id |
| EVENT | Core → UI | 主动推送，Core 主动发出 |
| ERROR | Core → UI | 操作失败，匹配 REQUEST 的 id |

### 3.3 请求/响应示例

**请求**：
```json
{
  "id": "msg_000001",
  "type": "REQUEST",
  "action": "core.status",
  "payload": {}
}
```

**成功响应**：
```json
{
  "id": "msg_000001",
  "type": "RESPONSE",
  "action": "core.status",
  "result": {
    "status": "running",
    "version": "0.1.0",
    "uptime": 3600,
    "modules": ["p2p", "tor", "i2p"]
  }
}
```

**错误响应**：
```json
{
  "id": "msg_000001",
  "type": "ERROR",
  "action": "core.status",
  "error": {
    "code": 404,
    "message": "MODULE_NOT_FOUND",
    "detail": "module 'tor' is not loaded"
  }
}
```

---

## 4. Core API 路由

### 4.1 路由列表

| action | 方法 | 说明 |
|--------|------|------|
| `core.status` | GET | 获取 Core 运行状态 |
| `core.info` | GET | 获取 Core 版本信息 |
| `core.shutdown` | POST | 关闭 Core |
| `core.reload` | POST | 重载配置 |
| `config.get` | GET | 获取配置项 |
| `config.set` | PATCH | 修改配置项 |
| `modules.list` | GET | 列出已加载模块 |
| `modules.load` | POST | 加载模块 |
| `modules.unload` | DELETE | 卸载模块 |
| `modules.call` | POST | 调用模块方法 |
| `session.list` | GET | 列出所有会话 |
| `session.kill` | DELETE | 强制关闭会话 |

---

### 4.2 core.status

**描述**：获取 Core 运行状态快照

**REQUEST**：
```json
{
  "id": "msg_000001",
  "type": "REQUEST",
  "action": "core.status",
  "payload": {}
}
```

**RESPONSE**：
```json
{
  "id": "msg_000001",
  "type": "RESPONSE",
  "result": {
    "status": "running",          // running | stopped | error
    "version": "0.1.0",
    "uptime": 3600,               // 秒
    "startedAt": "2026-04-03T10:00:00Z",
    "modules": {
      "p2p": { "status": "running", "version": "0.1.0" },
      "tor": { "status": "stopped", "version": "0.1.0" },
      "i2p": { "status": "stopped", "version": "0.1.0" }
    },
    "network": {
      "listening": [{ "protocol": "ws", "address": "localhost:19001" }],
      "connections": 2
    }
  }
}
```

---

### 4.3 core.info

**描述**：获取 Core 元信息（版本、作者、许可证、链接、发布时间），用于 UI 显示

**RESPONSE**：
```json
{
  "id": "msg_000001",
  "type": "RESPONSE",
  "result": {
    "name": "Cloudfin Core",
    "version": "0.1.0",
    "developer": "Cloudfin Team",
    "license": "AGPL-3.0",
    "licenseUrl": "https://www.gnu.org/licenses/agpl-3.0.html",
    "website": "https://cloudfin.io",
    "github": "https://github.com/indivisible2025/Cloudfin",
    "publishedAt": "2026-01-01T00:00:00Z",
    "updatedAt": "2026-04-03T00:00:00Z",
    "commit": "b37cc7a",
    "rustVersion": "1.94.1",
    "protocolVersion": "1.0",
    "buildAt": "2026-04-03T10:00:00Z"
  }
}
```

---

### 4.4 core.shutdown

**描述**：安全关闭 Core（等待模块卸载完成）

**REQUEST**：
```json
{
  "id": "msg_000002",
  "type": "REQUEST",
  "action": "core.shutdown",
  "payload": {
    "graceful": true,             // true = 等待模块卸载，false = 强制关闭
    "timeout": 5000               // 毫秒，超时强制关闭
  }
}
```

**RESPONSE**：
```json
{
  "id": "msg_000002",
  "type": "RESPONSE",
  "result": {
    "shutdown": true,
    "elapsed": 1200
  }
}
```

---

### 4.5 core.reload

**描述**：重载 Core 配置（不重启模块）

**REQUEST**：
```json
{
  "id": "msg_000003",
  "type": "REQUEST",
  "action": "core.reload",
  "payload": {}
}
```

**RESPONSE**：
```json
{
  "id": "msg_000003",
  "type": "RESPONSE",
  "result": {
    "reloaded": true,
    "changes": ["config.theme", "config.language"]
  }
}
```

---

### 4.6 config.get

**描述**：获取配置项

**REQUEST**：
```json
{
  "id": "msg_000004",
  "type": "REQUEST",
  "action": "config.get",
  "payload": {
    "key": "core.theme"           // 省略 key 返回全部
  }
}
```

**RESPONSE**：
```json
{
  "id": "msg_000004",
  "type": "RESPONSE",
  "result": {
    "key": "core.theme",
    "value": "dark"
  }
}
```

**批量获取**：
```json
{
  "id": "msg_000004",
  "type": "REQUEST",
  "action": "config.get",
  "payload": {}
}
```

**RESPONSE**：
```json
{
  "id": "msg_000004",
  "type": "RESPONSE",
  "result": {
    "core.theme": "dark",
    "core.language": "zh-CN",
    "core.startupModule": ["p2p"],
    "network.listen": "localhost:19001"
  }
}
```

---

### 4.7 config.set

**描述**：修改配置项（持久化到磁盘）

**REQUEST**：
```json
{
  "id": "msg_000005",
  "type": "REQUEST",
  "action": "config.set",
  "payload": {
    "key": "core.theme",
    "value": "light"
  }
}
```

**RESPONSE**：
```json
{
  "id": "msg_000005",
  "type": "RESPONSE",
  "result": {
    "key": "core.theme",
    "previousValue": "dark",
    "value": "light",
    "persisted": true
  }
}
```

---

### 4.8 modules.list

**描述**：列出所有可用模块（含状态）

**REQUEST**：
```json
{
  "id": "msg_000006",
  "type": "REQUEST",
  "action": "modules.list",
  "payload": {}
}
```

**RESPONSE**：
```json
{
  "id": "msg_000006",
  "type": "RESPONSE",
  "result": {
    "core": {
      "version": "0.1.0",
      "name": "Cloudfin Core",
      "developer": "Cloudfin Team",
      "license": "AGPL-3.0",
      "website": "https://cloudfin.io",
      "github": "https://github.com/indivisible2025/Cloudfin",
      "publishedAt": "2026-01-01T00:00:00Z",
      "updatedAt": "2026-04-03T00:00:00Z"
    },
    "modules": [
      {
        "id": "p2p",
        "name": "P2P Network",
        "nameZh": "P2P 网络",
        "version": "0.1.0",
        "status": "running",
        "publishedAt": "2026-01-15T00:00:00Z",
        "updatedAt": "2026-03-20T12:30:00Z",
        "description": "P2P node discovery and connection",
        "author": {
          "name": "Cloudfin Team",
          "github": "https://github.com/indivisible2025"
        },
        "license": {
          "spdx": "AGPL-3.0",
          "name": "Affero General Public License v3.0",
          "commercial": false
        },
        "links": {
          "source": "https://github.com/indivisible2025/cloudfin-mod-p2p",
          "documentation": "https://docs.cloudfin.io/modules/p2p"
        }
      },
      {
        "id": "tor",
        "name": "Tor Protocol",
        "nameZh": "Tor 协议",
        "version": "0.1.0",
        "status": "loaded",
        "publishedAt": "2026-02-01T00:00:00Z",
        "updatedAt": "2026-03-15T08:00:00Z",
        "description": "Tor protocol wrapper based on Arti",
        "author": {
          "name": "Cloudfin Team",
          "github": "https://github.com/indivisible2025"
        },
        "license": {
          "spdx": "AGPL-3.0",
          "name": "Affero General Public License v3.0",
          "commercial": false
        },
        "links": {
          "source": "https://github.com/indivisible2025/cloudfin-mod-tor"
        }
      }
    ]
  }
}
```

---

### 4.9 modules.load

**描述**：加载指定模块（动态加载 .so/.dylib）

**REQUEST**：
```json
{
  "id": "msg_000007",
  "type": "REQUEST",
  "action": "modules.load",
  "payload": {
    "moduleId": "tor",
    "config": {                   // 模块特定配置
      "bridges": ["obfs4..."],
      "port": 9050
    }
  }
}
```

**RESPONSE**：
```json
{
  "id": "msg_000007",
  "type": "RESPONSE",
  "result": {
    "moduleId": "tor",
    "status": "running",
    "loadTime": 1500
  }
}
```

**ERROR**：
```json
{
  "id": "msg_000007",
  "type": "ERROR",
  "error": {
    "code": 400,
    "message": "MODULE_ALREADY_LOADED",
    "detail": "module 'tor' is already loaded"
  }
}
```

---

### 4.10 modules.unload

**描述**：卸载指定模块（安全卸载，等待现有请求处理完成）

**REQUEST**：
```json
{
  "id": "msg_000008",
  "type": "REQUEST",
  "action": "modules.unload",
  "payload": {
    "moduleId": "tor",
    "graceful": true,
    "timeout": 5000
  }
}
```

**RESPONSE**：
```json
{
  "id": "msg_000008",
  "type": "RESPONSE",
  "result": {
    "moduleId": "tor",
    "unloaded": true,
    "elapsed": 200
  }
}
```

---

### 4.11 modules.call

**描述**：调用模块提供的 API 方法（模块间通信标准接口）

**REQUEST**：
```json
{
  "id": "msg_000009",
  "type": "REQUEST",
  "action": "modules.call",
  "payload": {
    "moduleId": "p2p",
    "method": "connect",
    "args": {
      "peerId": "node_abc123",
      "address": "/ip4/192.168.1.100/tcp/19000"
    }
  }
}
```

**RESPONSE**：
```json
{
  "id": "msg_000009",
  "type": "RESPONSE",
  "result": {
    "connected": true,
    "peerId": "node_abc123",
    "latency": 32
  }
}
```

**模块方法前缀规则**：
- `p2p.*` → P2P 模块方法
- `tor.*` → Tor 模块方法
- `i2p.*` → I2P 模块方法
- `crdt.*` → CRDT 模块方法
- `crypto.*` → 加密模块方法
- `storage.*` → 存储模块方法

---

### 4.12 session.list

**描述**：列出所有活跃会话

**RESPONSE**：
```json
{
  "id": "msg_000010",
  "type": "RESPONSE",
  "result": {
    "sessions": [
      {
        "sessionId": "s_abc123",
        "type": "ui",              // ui | cli | module
        "platform": "android",
        "appVersion": "1.0.0.debug014",
        "connectedAt": "2026-04-03T10:00:00Z",
        "lastActive": "2026-04-03T10:05:00Z",
        "remoteAddr": "localhost"
      }
    ]
  }
}
```

---

### 4.13 session.kill

**描述**：强制关闭指定会话

**REQUEST**：
```json
{
  "id": "msg_000011",
  "type": "REQUEST",
  "action": "session.kill",
  "payload": {
    "sessionId": "s_abc123"
  }
}
```

**RESPONSE**：
```json
{
  "id": "msg_000011",
  "type": "RESPONSE",
  "result": {
    "sessionId": "s_abc123",
    "killed": true
  }
}
```

---

## 5. 事件推送（EVENT）

Core 主动推送事件到 UI，无需 UI 请求。

### 5.1 事件格式

```json
{
  "id": "evt_123456",
  "type": "EVENT",
  "event": "module.status_changed",
  "payload": {
    "moduleId": "tor",
    "previousStatus": "running",
    "status": "stopped",
    "reason": "user_request"
  },
  "timestamp": "2026-04-03T10:10:00Z"
}
```

### 5.2 事件类型

| event | 说明 | 触发时机 |
|-------|------|---------|
| `core.started` | Core 启动完成 | Core daemon 启动成功后 |
| `core.shutdown` | Core 关闭 | Core 收到 shutdown 请求 |
| `module.loaded` | 模块加载完成 | modules.load 成功后 |
| `module.unloaded` | 模块卸载完成 | modules.unload 成功后 |
| `module.status_changed` | 模块状态变化 | 模块 running ↔ stopped |
| `module.error` | 模块内部错误 | 模块发生可捕获错误 |
| `session.connected` | 新会话连接 | 新 UI/CLI 连接建立 |
| `session.disconnected` | 会话断开 | UI/CLI 连接断开 |
| `config.changed` | 配置被修改 | config.set 成功 |
| `p2p.peer_connected` | P2P 节点连接 | P2P 模块：发现新节点 |
| `p2p.peer_disconnected` | P2P 节点断开 | P2P 模块：节点断开 |
| `sync.conflict` | 同步冲突 | CRDT 模块：检测到冲突 |
| `sync.complete` | 同步完成 | CRDT 模块：同步完成 |

---

## 6. 错误码

### 6.1 全局错误码

| code | message | 说明 |
|------|---------|------|
| 400 | BAD_REQUEST | 请求格式错误 |
| 401 | AUTH_REQUIRED | 未认证 |
| 401 | INVALID_KEY | 认证密钥无效 |
| 403 | FORBIDDEN | 无权限 |
| 404 | NOT_FOUND | 资源不存在 |
| 404 | MODULE_NOT_FOUND | 模块不存在 |
| 404 | METHOD_NOT_FOUND | 模块方法不存在 |
| 409 | MODULE_ALREADY_LOADED | 模块已加载 |
| 409 | MODULE_NOT_LOADED | 模块未加载 |
| 500 | INTERNAL_ERROR | Core 内部错误 |
| 503 | MODULE_ERROR | 模块执行错误 |
| 504 | MODULE_TIMEOUT | 模块方法调用超时 |

---

## 7. 状态机

### 7.1 Core 状态

```
                  ┌─────────────────────────────────────┐
                  │                                     │
                  ▼                                     │
              ┌──────┐                                  │
    ┌─────────│stopped│◀────────────────────────────┐   │
    │         └──────┘                              │   │
    │           ▲                                     │   │
    │           │ start                               │   │
    │           │                                     │   │
    │         ┌──────┐        error         ┌──────┐ │
    └─────────│running│────────────────────▶│error │─┘
              └──────┘                       └──────┘
                    ▲         stop                  │
                    └───────────────────────────────┘
```

### 7.2 模块状态

```
              ┌──────────┐
              │  stopped │◀─────────────────────────┐
              └────┬─────┘                           │
                   │ load                            │
                   ▼                                 │ unload
              ┌──────────┐                           │
              │  loaded  │───────────────────────────┤
              └────┬─────┘                           │
                   │ start                           │
                   ▼                                 │
              ┌──────────┐        stop        ┌──────┐
              │ running  │───────────────────▶ │stopped
              └────┬─────┘                     └──────┘
                   │
                   │ error
                   ▼
              ┌──────────┐
              │  error   │──┐
              └──────────┘  │ reload / restart
                            ▼
                       （回到之前状态）
```

---

## 8. 连接生命周期

### 8.1 正常断开

```
UI 关闭应用
     ↓
UI 发送 CLOSE 帧（WebSocket close）
     ↓
Core 标记 session 为 disconnecting
     ↓
Core 等待 UI 实际断开（TCP FIN）
     ↓
Core 清理 session
```

### 8.2 异常断开（网络故障）

```
网络中断 / UI Crash
     ↓
Core 检测到 TCP 连接断开（心跳超时）
     ↓
Core 等待重连（默认 30 秒）
     ↓
无重连 → Core 清理 session
     ↓
UI 重启 → 重新建立连接 → AUTH
```

### 8.3 心跳机制

```
连接建立后，双方每 30 秒互发 PING/PONG 帧

PING:
{ "type": "PING", "timestamp": 1712123456789 }

PONG:
{ "type": "PONG", "timestamp": 1712123456789 }

超过 60 秒无响应 → 判定为断开
```

---

## 9. 重连策略

### 9.1 UI 侧重连逻辑

```
首次连接失败
     ↓
等待 1 秒 → 重试
     ↓
失败 → 等待 2 秒 → 重试
     ↓
失败 → 等待 4 秒 → 重试
     ↓
（指数退避，上限 30 秒）
     ↓
超过 5 分钟 → 弹出错误提示（Core 未安装？）
```

### 9.2 Core 满载时的处理

```
Core 忙于处理高负载请求
     ↓
UI 请求超时（默认 30 秒）
     ↓
UI 收到 504 MODULE_TIMEOUT
     ↓
UI 可选择：重试 / 降级 / 提示用户
```

---

## 10. 安全性

### 10.1 本地认证

- 设备首次安装时，Core 生成随机 deviceKey
- deviceKey 存储在系统密钥链（Android Keystore / iOS Keychain）
- UI 启动时从密钥链读取 deviceKey 进行 AUTH
- 本地通信不走 TLS（localhost 无意义）

### 10.2 模块隔离

- 每个模块运行在独立沙箱中
- 模块只能通过 modules.call 访问 Core API
- 模块之间不能直接通信，必须通过 Core 路由

---

## 11. 配置存储格式

### 11.1 config.json 结构

```json
{
  "core": {
    "version": "0.1.0",
    "language": "zh-CN",
    "theme": "dark",
    "startupModule": ["p2p"],
    "logLevel": "info"
  },
  "network": {
    "listen": "localhost:19001",
    "unixSocket": "/run/cloudfin.sock",
    "maxConnections": 10,
    "pingInterval": 30
  },
  "modules": {
    "p2p": {
      "enabled": true,
      "port": 19000
    },
    "tor": {
      "enabled": false,
      "port": 9050
    },
    "i2p": {
      "enabled": false,
      "port": 7656
    }
  },
  "security": {
    "deviceId": "android_xxx",
    "deviceKey": "key_xxx"     // 已哈希存储
  }
}
```

---

## 12. 完整交互示例

### 场景：启动 Tor 模块并连接

```
阶段一：连接建立
───────────────────────────────────────

UI ──▶ Core  WS CONNECT
Core ─▶ UI   HELLO { version, sessionId }
UI ──▶ Core  AUTH { deviceId, deviceKey }
Core ─▶ UI   AUTH_OK { sessionId }

阶段二：查看模块状态
───────────────────────────────────────

UI ──▶ Core  REQUEST { id: "1", action: "modules.list" }
Core ─▶ UI   RESPONSE { id: "1", result: { modules: [p2p: running, tor: loaded] } }

阶段三：启动 Tor 模块
───────────────────────────────────────

UI ─-▶ Core  REQUEST { id: "2", action: "modules.load", payload: { moduleId: "tor" } }
Core ─▶ UI   EVENT { event: "module.loaded", payload: { moduleId: "tor" } }
Core ─▶ UI   RESPONSE { id: "2", result: { moduleId: "tor", status: "running" } }

阶段四：调用 Tor 模块方法
───────────────────────────────────────

UI ─-▶ Core  REQUEST { id: "3", action: "modules.call", payload: { moduleId: "tor", method: "connect", args: {} } }
Core ─▶ UI   RESPONSE { id: "3", result: { connected: true, socksPort: 9050 } }

阶段五：收到事件推送
───────────────────────────────────────

Core ─▶ UI   EVENT { event: "module.status_changed", payload: { moduleId: "tor", status: "running" } }

阶段六：连接断开
───────────────────────────────────────

UI ─-▶ Core  WS CLOSE
Core ─▶ UI   WS CLOSE (1000 Normal Closure)
     连接结束
```

---

## 13. 与其他组件的关系

```
┌──────────────────────────────────────────────────────┐
│                     UI 层                             │
│   Android / iOS / Desktop                             │
│                                                      │
│   WebSocket Client                                    │
│   cloudfin_client.(kt/dart)                          │
└──────────────────────┬───────────────────────────────┘
                       │  ws://localhost:19001/ws
                       │  (JSON over WebSocket)
┌──────────────────────▼───────────────────────────────┐
│                   Cloudfin Core                       │
│                   (Rust + tokio)                      │
│                                                      │
│   ┌─────────┐  ┌─────────┐  ┌─────────┐             │
│   │ Router  │  │ Session │  │ Config  │             │
│   │ 模块路由  │  │ 管理     │  │ 存储     │             │
│   └─────────┘  └─────────┘  └─────────┘             │
│                                                      │
│   ┌─────────────────────────────────────────┐         │
│   │         Module Loader (.so/.dylib)      │         │
│   └─────────────────────────────────────────┘         │
│          │              │              │              │
│    ┌─────▼──┐    ┌─────▼──┐    ┌─────▼──┐          │
│    │  P2P   │    │  Tor   │    │  I2P   │          │
│    │ Module │    │ Module │    │ Module │          │
│    └────────┘    └────────┘    └────────┘          │
└──────────────────────────────────────────────────────┘
```

---

## 14. 实现要点

### 14.1 WebSocket 框架选型

**Rust 生态推荐**：
- `tokio-tungstenite`：异步 WebSocket，配合 tokio 使用
- `axum`：`axum::WebSocket` 支持，与 Core HTTP Server 共享端口

### 14.2 会话管理

- 每个 WebSocket 连接 = 一个 Session
- Session 存储：sessionId / type / platform / connectedAt / lastActive
- 使用 `HashMap<SessionId, Session>` 管理，Rust 中用 `DashMap` 实现线程安全

### 14.3 消息 ID 生成

- 格式：`msg_` + 6位递增数字 + 随机字符
- 示例：`msg_000001A`
- UI 侧生成请求 ID，Core 在响应中回传相同 ID

### 14.4 模块方法调用

- Core 维护模块方法注册表：`HashMap<ModuleId, HashMap<MethodName, ModuleMethod>>`
- `modules.call` 路由到对应模块
- 模块方法签名：`fn call(method: &str, args: Value) -> Result<Value, ModuleError>`

---

## 15. CLI 工具

### 15.1 cloudfinctl

Core 自带命令行工具，可通过同一 WebSocket 连接访问：

```bash
# 查看状态
cloudfinctl status

# 加载模块
cloudfinctl modules load tor

# 调用模块方法
cloudfinctl call p2p connect --peer node_xxx

# 查看日志
cloudfinctl logs --tail 50
```

### 15.2 交互模式

```bash
cloudfinctl shell
# 进入交互模式，可连续输入命令
```

---

## 16. 审查清单

每份实现必须对照以下检查：

```
□ WebSocket 连接建立流程正确（HELLO → AUTH → AUTH_OK/FAIL）
□ 所有 action 路由已实现（13 个）
□ 所有 EVENT 类型已定义（14 种）
□ 心跳机制实现（PING/PONG，30 秒间隔，60 秒超时）
□ 重连逻辑实现（指数退避，上限 30 秒）
□ 错误码完整（12 个错误码）
□ Session 管理（创建、追踪、清理）
□ 模块加载/卸载（安全卸载流程）
□ 配置持久化（修改后写入磁盘）
□ JSON 序列化/反序列化测试覆盖
□ 并发连接测试（10+ 并发 WebSocket 连接）
□ 异常断开测试（TCP 断开的 Session 清理）
□ CLI 工具所有子命令实现
```

---

*本文档由 墨尘 🌙 编写*  
*最后更新：2026-04-03*
