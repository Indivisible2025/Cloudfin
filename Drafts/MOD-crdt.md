# Cloudfin CRDT 模块规格

> 版本：v0.1.0-draft  
> 日期：2026-04-03  
> 状态：**草案 — 待审查**  
> 依赖：SPEC-Plugin-Interface.md（插件接口）

---

## 1. 模块概述

### 1.1 什么是 CRDT 模块

CRDT = **Conflict-free Replicated Data Type**（无冲突复制数据类型）

CRDT 模块 = **让多个设备之间的数据自动保持同步，且永远不会冲突**。

类比：
- **没有 CRDT**：你用手机改了一个文件，同时用电脑也改了同一个文件 → 冲突了 → 手动解决
- **有 CRDT**：你用手机改了一个文件，同时用电脑也改了 → 系统自动合并两人的修改 → 没有冲突

### 1.2 模块基本信息

| 属性 | 值 |
|------|---|
| 模块 ID | `crdt` |
| 模块名称 | CRDT Sync |
| 模块名称（中文）| CRDT 同步引擎 |
| 版本 | 0.1.0 |
| 许可证 | 商业授权 |
| 开发者 | Cloudfin Team |
| 依赖 | automerge（Rust CRDT 库）或 yjs |

### 1.3 CRDT 工作原理

```
场景：两台设备（手机、电脑）同步同一个文件

初始状态：手机和电脑都有文件 V1（相同版本）

手机修改了文件 → V2
      │
      ▼
CRDT 模块记录这次修改（A=[增:第3行"hello"]）
      │
      ▼
修改通过 Core 广播给电脑
      │
      ▼
电脑收到修改，自动应用到自己本地
      │
      ▼
电脑文件也变成了 V2，和手机一样 ✅
```

**CAVEAT**：CRDT 不等于"所有冲突都能自动解决"。CRDT 解决的是**语义上可合并的冲突**，比如：
- 两人同时在文件不同位置修改 → ✅ 自动合并
- 两人同时删除了同一个文件 → ✅ 合并为"已删除"
- 两人同时修改了同一行同一个字 → ⚠️ 两个修改都会保留（最终显示取决于应用层）

### 1.4 适用范围

**适合使用 CRDT 的场景**：

| 场景 | 说明 | 效果 |
|------|------|------|
| **多设备文件同步** | 手机、电脑、平板同时编辑同一个文件 | 自动合并，无冲突 |
| **实时协作编辑** | 多人同时编辑同一份文档 | 每个人看到实时同步 |
| **离线优先应用** | 网络断开时继续编辑，联网后自动同步 | 最终一致性 |
| **去中心化数据** | 无中央服务器，各设备直接同步 | 无单点故障 |
| **联系人/日程同步** | 多设备间同步个人数据 | 自动合并，不丢失 |

**不适合使用 CRDT 的场景**：

| 场景 | 原因 | 替代 |
|------|------|------|
| **强一致性要求** | CRDT 保证最终一致性，不保证立即一致 | 传统数据库 + 分布式锁 |
| **事务性操作** | CRDT 不能保证"要么全改，要么全不改" | 数据库事务 |
| **大量二进制文件** | CRDT 适合文本/小数据，不适合 GB 级大文件 | rsync + 版本控制 |
| **需要人工解决冲突** | 某些语义冲突无法自动处理 | 人工审核 + 合并工具 |

**典型使用流程**：

```
场景：手机和电脑同步"笔记"文件夹

第一步：启动 CRDT 模块
  └─ modules.load { moduleId: "crdt" }

第二步：注册要同步的文件夹
  └─ modules.call crdt.register { path: "/sync/notes", type: "folder" }
       └─ CRDT 模块扫描文件夹，建立初始快照

第三步：修改文件
  └─ 手机修改了 notes/test.md
       └─ CRDT 模块记录修改 diff
       └─ 通过 Core 广播给电脑

第四步：同步完成
  └─ 电脑收到修改，自动应用
       └─ 两台设备文件完全一致 ✅
```

### 1.5 技术选型

| 组件 | 技术 | 说明 |
|------|------|------|
| CRDT 核心 | automerge / yjs | 成熟的 CRDT 实现库 |
| 传输层 | P2P 模块或 Tor/I2P 模块 | 通过其他模块传输同步数据 |
| 存储 | 本地 SQLite + 远程追加日志 | 记录操作历史，支持回滚 |
| 压缩 | Zstandard | 压缩同步数据，减少传输量 |
| 加密 | Crypto 模块 | 端到端加密同步数据 |

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
    └─────────│  running │───────────────────────▶│ stopped│─┘
              └────┬─────┘                       └────────┘
                   │
                   │ 至少一个活跃对等节点
                   ▼
              ┌──────────┐
              │ syncing  │ ← 正在同步数据
              └────┬─────┘
                   │
                   │ 同步完成
                   ▼
              ┌──────────┐
              │ synced   │ ← 数据一致
              └──────────┘
```

### 2.1 状态说明

| 状态 | 说明 |
|------|------|
| `stopped` | 模块未启动 |
| `starting` | 启动中，加载本地状态 |
| `running` | 运行中，监控文件系统变化 |
| `syncing` | 正在和至少一个对等节点同步数据 |
| `synced` | 所有对等节点数据一致 |
| `error` | 发生错误 |

---

## 3. 配置结构

### 3.1 模块配置

```json
{
  "modules": {
    "crdt": {
      "enabled": false,
      "config": {
        "syncDirectory": "/sync",
        "transport": {
          "type": "auto",
          "preferred": ["p2p", "tor", "i2p"]
        },
        "sync": {
          "autoSync": true,
          "syncInterval": 5000,
          "conflictResolution": "auto",
          "maxHistory": 1000
        },
        "storage": {
          "type": "sqlite",
          "path": "/data/data/com.cloudfin/files/crdt/state.db",
          "compression": true,
          "compressionLevel": 3
        },
        "security": {
          "encrypt": true,
          "keyId": "crdt_master_key"
        },
        "network": {
          "discovery": "auto",
          "knownPeers": [],
          "relayEnabled": true
        }
      }
    }
  }
}
```

### 3.2 配置字段详解

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `syncDirectory` | String | `/sync` | 要同步的根目录 |
| `transport.type` | String | `auto` | 传输类型：auto/p2p/tor/i2p |
| `transport.preferred` | Array | `["p2p","tor","i2p"]` | 传输协议优先级 |
| `sync.autoSync` | Boolean | `true` | 文件变化时自动同步 |
| `sync.syncInterval` | Integer | `5000` | 主动同步间隔（毫秒）|
| `sync.conflictResolution` | String | `auto` | 冲突解决策略 |
| `sync.maxHistory` | Integer | `1000` | 保留历史版本数量 |
| `storage.type` | String | `sqlite` | 存储类型 |
| `storage.compression` | Boolean | `true` | 是否压缩同步数据 |
| `security.encrypt` | Boolean | `true` | 是否加密同步数据 |
| `network.relayEnabled` | Boolean | `true` | 无直连时是否启用中继 |

---

## 4. 方法注册表

### 4.1 支持的方法

| 方法 | 说明 | 参数 | 返回 |
|------|------|------|------|
| `register` | 注册同步文件夹 | path, type | { registered: true, snapshotId } |
| `unregister` | 取消同步 | path | { unregistered: true } |
| `status` | 获取同步状态 | — | { status, syncedPaths, peers } |
| `sync_now` | 立即触发同步 | path | { synced: true, peersContacted } |
| `get_history` | 获取修改历史 | path, limit | { history: [...] } |
| `rollback` | 回滚到指定版本 | path, snapshotId | { rolledBack: true, newSnapshotId } |
| `peer_add` | 添加同步对等节点 | peerId, address | { added: true } |
| `peer_remove` | 移除对等节点 | peerId | { removed: true } |
| `list_peers` | 列出活跃对等节点 | — | { peers: [...] } |
| `get_snapshot` | 获取指定路径的当前快照 | path | { snapshotId, hash, data } |
| `send_update` | **通过 Core 调用**：发送更新给对等节点 | peerId, update | { sent: true } |
| `receive_update` | **通过 Core 调用**：接收对等节点的更新 | peerId, update | { applied: true } |

### 4.2 方法详细定义

#### register

**描述**：注册一个文件或文件夹到 CRDT 同步系统

**参数**：
```json
{
  "path": "/sync/notes",
  "type": "folder"
}
```

**type**：`file` 或 `folder`

**返回值**：
```json
{
  "registered": true,
  "snapshotId": "snap_abc123",
  "path": "/sync/notes",
  "hash": "sha256:xxx",
  "peers": []
}
```

---

#### sync_now

**描述**：立即触发指定路径的同步

**参数**：
```json
{
  "path": "/sync/notes"
}
```

**返回值**：
```json
{
  "synced": true,
  "path": "/sync/notes",
  "peersContacted": 2,
  "updatesExchanged": 5,
  "duration": 1200
}
```

---

#### get_history

**描述**：获取指定路径的修改历史

**参数**：
```json
{
  "path": "/sync/notes/test.md",
  "limit": 50
}
```

**返回值**：
```json
{
  "path": "/sync/notes/test.md",
  "history": [
    {
      "snapshotId": "snap_003",
      "timestamp": "2026-04-03T10:05:00Z",
      "author": "device_phone",
      "changeType": "modify",
      "hash": "sha256:xxx",
      "size": 1024
    },
    {
      "snapshotId": "snap_002",
      "timestamp": "2026-04-03T10:00:00Z",
      "author": "device_pc",
      "changeType": "modify",
      "hash": "sha256:yyy",
      "size": 512
    },
    {
      "snapshotId": "snap_001",
      "timestamp": "2026-04-03T09:00:00Z",
      "author": "device_phone",
      "changeType": "create",
      "hash": "sha256:zzz",
      "size": 256
    }
  ],
  "total": 3
}
```

---

#### rollback

**描述**：回滚到指定的历史版本

**参数**：
```json
{
  "path": "/sync/notes/test.md",
  "snapshotId": "snap_002"
}
```

**返回值**：
```json
{
  "rolledBack": true,
  "path": "/sync/notes/test.md",
  "fromSnapshotId": "snap_003",
  "toSnapshotId": "snap_002",
  "newSnapshotId": "snap_004",
  "hash": "sha256:yyy"
}
```

---

#### send_update（模块间通信接口）

**描述**：发送 CRDT 更新给指定对等节点（供其他模块调用）

**调用方式**（其他模块通过 Core 调用）：
```json
{
  "id": "msg_000001",
  "type": "REQUEST",
  "action": "modules.call",
  "payload": {
    "moduleId": "crdt",
    "method": "send_update",
    "args": {
      "peerId": "device_pc",
      "update": "base64_encoded_crdt_update",
      "path": "/sync/notes"
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
    "peerId": "device_pc",
    "updateHash": "sha256:xxx",
    "duration": 50
  }
}
```

---

#### receive_update（模块间通信接口）

**描述**：接收来自对等节点的 CRDT 更新（供其他模块调用）

**调用方式**：
```json
{
  "id": "msg_000002",
  "type": "REQUEST",
  "action": "modules.call",
  "payload": {
    "moduleId": "crdt",
    "method": "receive_update",
    "args": {
      "peerId": "device_pc",
      "update": "base64_encoded_crdt_update",
      "path": "/sync/notes"
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
    "applied": true,
    "peerId": "device_pc",
    "path": "/sync/notes",
    "changesMerged": 3,
    "conflicts": 0,
    "newSnapshotId": "snap_005"
  }
}
```

**错误码**：
| code | message | 说明 |
|------|---------|------|
| 404 | PATH_NOT_REGISTERED | 该路径未注册同步 |
| 409 | SYNC_CONFLICT | 有无法自动解决的冲突 |
| 500 | UPDATE_APPLY_FAILED | 更新应用失败 |
| 501 | CRDT_NOT_RUNNING | CRDT 模块未运行 |

---

## 5. 事件

### 5.1 CRDT 模块推送的事件

| 事件 | 说明 | 触发时机 |
|------|------|---------|
| `crdt.started` | CRDT 模块启动 | 模块状态变为 running |
| `crdt.stopped` | CRDT 模块停止 | 模块状态变为 stopped |
| `crdt.error` | CRDT 模块错误 | 发生可捕获错误 |
| `crdt.file_changed` | 文件被修改 | 本地或远程文件发生变化 |
| `crdt.synced` | 同步完成 | 指定路径与所有对等节点同步完成 |
| `crdt.syncing` | 开始同步 | 开始与对等节点交换数据 |
| `crdt.peer_connected` | 对等节点连接 | 新设备上线，开始同步 |
| `crdt.peer_disconnected` | 对等节点断开 | 设备离线 |
| `crdt.conflict_detected` | 检测到冲突 | 发生无法自动合并的冲突 |
| `crdt.conflict_resolved` | 冲突已解决 | 冲突已通过策略解决 |
| `crdt.storage_full` | 存储空间不足 | 本地存储空间不足 |

### 5.2 事件格式

```json
{
  "type": "EVENT",
  "event": "crdt.file_changed",
  "payload": {
    "path": "/sync/notes/test.md",
    "changeType": "modify",
    "author": "device_pc",
    "timestamp": "2026-04-03T10:00:00Z",
    "snapshotId": "snap_003"
  },
  "timestamp": "2026-04-03T10:00:00Z"
}
```

---

## 6. 模块间通信（通过 Core）

> **核心原则**：所有模块间通信必须通过 Core，`modules.call` 是唯一入口。

### 6.1 通信架构

```
┌──────────────────────────────────────────────────────────┐
│                   模块间同步数据流                        │
│                                                          │
│  P2P 模块 ──▶ Core ──▶ modules.call crdt.send_update    │
│                │                                        │
│                ▼                                        │
│           CRDT 模块 ──▶ 存储（SQLite）                   │
│                │                                        │
│                ▼                                        │
│  P2P 模块 ◀── Core ◀── modules.call crdt.receive_update │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

**传输模块（P2P/Tor/I2P）负责传输 CRDT 的更新数据，但不知道数据内容是什么**：
- P2P/Tor/I2P 只是搬运工
- CRDT 模块负责数据的序列化和反序列化
- Crypto 模块负责加密（如果启用了端到端加密）

### 6.2 sync_update 完整流程

```
手机（CRDT）想同步给电脑：

1. CRDT 模块生成 update（CRDT 格式的增量更新）
2. CRDT 模块调用 Core：modules.call crdt.send_update
3. Core 路由给 P2P/Tor/I2P 模块传输
4. 电脑端 P2P/Tor/I2P 收到 update
5. 传给电脑的 CRDT 模块：modules.call crdt.receive_update
6. CRDT 模块应用更新到本地 SQLite
7. 文件系统更新，通知 App
```

---

## 7. CRDT 数据结构

### 7.1 支持的 CRDT 类型

| 类型 | 说明 | 适用场景 |
|------|------|---------|
| OR-Set | 可观察集合 | 联系人列表、标签 |
| LWW-Register | 最后写入胜出 | 单值配置、用户名 |
| RGA | 相对无冲突复制数组 | 有序列表、文本 |
| RWDictionary | 读写字典 | 键值对数据 |
| Treedoc | 树形文档 | JSON/XML 结构化数据 |

### 7.2 文件同步格式

```json
{
  "type": "crdt_update",
  "version": "1.0",
  "path": "/sync/notes/test.md",
  "author": "device_phone",
  "timestamp": "1712123456789",
  "operations": [
    {
      "type": "insert",
      "position": { "line": 3, "column": 5 },
      "content": "hello world"
    },
    {
      "type": "delete",
      "position": { "line": 5, "column": 10 },
      "length": 3
    }
  ],
  "parent": "snap_002",
  "hash": "sha256:xxx"
}
```

---

## 8. 冲突处理

### 8.1 冲突类型

| 冲突类型 | 说明 | 处理策略 |
|---------|------|---------|
| **可自动合并** | 不同位置修改 | CRDT 自动合并 ✅ |
| **同一位置不同内容** | 两人改了同一处 | 最后写入胜出（LWW）或都保留 |
| **删除 vs 修改** | 一人删，一人改 | 删除优先（墓碑机制）|
| **文件夹冲突** | 文件夹名称冲突 | 自动重命名或合并 |

### 8.2 冲突解决策略

```json
{
  "conflictResolution": "auto"
}
```

| 策略 | 说明 |
|------|------|
| `auto` | CRDT 自动合并最大公约数 |
| `lww` | 最后写入胜出（时间戳判断）|
| `manual` | 人工解决（保留双方版本）|
| `keep_both` | 两个版本都保留（文件名加后缀）|

---

## 9. 安全模型

### 9.1 加密

| 层级 | 说明 |
|------|------|
| 传输加密 | 通过 P2P/Tor/I2P 的链路加密 |
| 端到端加密 | 通过 Crypto 模块加密，传输模块无法解密 |
| 本地存储加密 | 通过 Crypto 模块加密存储 |

### 9.2 隐私

- 传输模块只知道数据大小，不知道内容
- 每个设备有独立身份标识（Device ID）
- 可选：匿名模式（不暴露 Device ID）

---

## 10. manifest.json

```json
{
  "manifestVersion": "1.0",
  "module": {
    "id": "crdt",
    "name": "CRDT Sync",
    "nameZh": "CRDT 同步引擎",
    "version": "0.1.0",
    "description": "Conflict-free replicated data type synchronization engine",
    "descriptionZh": "无冲突复制数据类型同步引擎",
    "icon": "modules/icons/crdt.png"
  },
  "developer": {
    "name": "Cloudfin Team",
    "email": "dev@cloudfin.io",
    "website": "https://cloudfin.io",
    "github": "https://github.com/indivisible2025/cloudfin-mod-crdt"
  },
  "license": {
    "spdx": "COMMERCIAL",
    "name": "Commercial License",
    "url": "https://cloudfin.io/license",
    "commercial": true
  },
  "links": {
    "source": "https://github.com/indivisible2025/cloudfin-mod-crdt",
    "documentation": "https://docs.cloudfin.io/modules/crdt",
    "bugReport": "https://github.com/indivisible2025/cloudfin-mod-crdt/issues",
    "changelog": "https://github.com/indivisible2025/cloudfin-mod-crdt/blob/main/CHANGELOG.md"
  },
  "compatibility": {
    "coreMinVersion": "0.1.0",
    "coreMaxVersion": "0.9.9",
    "platforms": ["linux", "macos", "android", "windows"]
  },
  "dependencies": {
    "native": [],
    "rust": ["automerge", "tokio", "serde", "rusqlite", "zstd"],
    "system": []
  },
  "permissions": {
    "canCall": ["p2p", "tor", "i2p", "crypto", "storage"],
    "canSubscribe": ["p2p", "tor", "i2p"],
    "canAccessConfig": false
  },
  "security": {
    "sandboxed": true,
    "networkAccess": "module_only",
    "fileAccess": "module_public"
  },
  "build": {
    "output": "modules/libcloudfin_crdt.so",
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
□ 11 个 CRDT 方法已定义（含 send_update / receive_update 供其他模块调用）
□ 所有方法的 JSON Schema 完整
□ CRDT 数据结构（OR-Set / LWW-Register / RGA 等）已说明
□ 冲突处理策略（auto / lww / manual / keep_both）已定义
□ 事件推送 11 种类型完整
□ 模块状态机 6 个状态正确
□ 适用范围/情况已列出
□ 传输模块（P2P/Tor/I2P）角色正确（只传输，不解析内容）
□ manifest.json 所有必填字段完整
□ 许可证为商业授权
□ 安全模型（传输加密 + 端到端加密）已说明
```

---

*本文档由 墨尘 🌙 编写*  
*最后更新：2026-04-03*
