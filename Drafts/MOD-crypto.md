# Cloudfin Crypto 模块规格

> 版本：v0.1.0-draft  
> 日期：2026-04-03  
> 状态：**草案 — 待审查**  
> 依赖：SPEC-Plugin-Interface.md（插件接口）

---

## 1. 模块概述

### 1.1 什么是 Crypto 模块

Crypto 模块 = **为通信过程提供端到端加密和解密**，保护数据安全和隐私。

类比：
- **不用 Crypto 模块**：你发消息 → 明文传输 →可能被窃听
- **用 Crypto 模块**：你发消息 → 加密 → 密文传输 → 只有收件人能解密

### 1.2 模块基本信息

| 属性 | 值 |
|------|---|
| 模块 ID | `crypto` |
| 模块名称 | Cryptography |
| 模块名称（中文）| 加密模块 |
| 版本 | 0.1.0 |
| 许可证 | Apache-2.0 |
| 开发者 | Cloudfin Team |
| 依赖 | ring / x25519-dalek / aes-gcm / chacha20poly1305 |

### 1.3 功能概览

```
┌──────────────────────────────────────────────────────────┐
│                     Crypto 模块                           │
│                                                          │
│  密钥管理 ──▶ 加密/解密 ──▶ 数字签名 ──▶ 密钥交换        │
│      │                                                │   │
│      │    对称加密：速度快，适合大数据                 │   │
│      │    非对称加密：安全，适合小数据                 │   │
│      │    混合加密：两者结合，适合所有场景             │   │
│      │                                                │   │
│      └───── X25519 密钥交换 + ChaCha20Poly1305 ─────────┘  │
└──────────────────────────────────────────────────────────┘
```

### 1.4 适用范围

**适合使用 Crypto 的场景**：

| 场景 | 说明 | 效果 |
|------|------|------|
| **端到端加密通信** | P2P/Tor/I2P 传输的数据需要内容加密 | 只有收发双方能解密 |
| **文件加密存储** | CRDT 同步的文件需要加密存储 | 即使设备丢失，数据也不泄露 |
| **身份认证** | 设备之间需要验证对方身份 | 中间人攻击无法得逞 |
| **密钥交换** | 安全地在设备之间传递密钥 | 即使被截获也无法解密 |
| **数据完整性验证** | 验证数据是否被篡改 | 数字签名保证 authenticity |

**Crypto 模块不是**：

| 不是 | 说明 |
|------|------|
| 替代 TLS/WireGuard | 这些是链路层加密，Crypto 是应用层端到端加密 |
| 替代 HTTPS | HTTPS 保护浏览器到服务器，Crypto 保护应用内数据 |
| 密码存储方案 | 不负责密码的哈希存储（那是 Keychain/Keystore）|

**Crypto 模块做什么**：

| 功能 | 说明 |
|------|------|
| 端到端加密 | 应用数据从发送方加密到接收方，全程密文 |
| 密钥管理 | 生成、分发、存储、轮换密钥 |
| 数字签名 | 验证数据来源和完整性 |
| 安全随机数 | 生成加密安全的随机数 |

### 1.5 技术选型

| 组件 | 技术 | 说明 |
|------|------|------|
| 对称加密 | ChaCha20Poly1305 | 现代流加密，高性能，无侧信道 |
| 非对称加密 | X25519 | 椭圆曲线密钥交换 |
| 数字签名 | Ed25519 | 高性能签名算法 |
| 哈希 | BLAKE3 | 快速哈希，比 SHA-256 快 10 倍 |
| 随机数 | ring::rand | 加密安全的随机数生成器 |
| 密钥派生 | HKDF | 从主密钥派生会话密钥 |

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
    │         │ starting │──────────────────▶│ error │─┘    │
    │         └────┬─────┘                  └──────┘       │
    │              │                                        │
    │              ▼                                        │
    │         ┌──────────┐        stop          ┌────────┐ │
    └─────────│  running │───────────────────────▶│ stopped │─┘
              └────┬─────┘                       └────────┘
                   │
                   │ 密钥初始化完成
                   ▼
              ┌──────────┐
              │   ready  │ ← 可进行加密/解密
              └──────────┘
```

### 2.1 状态说明

| 状态 | 说明 |
|------|------|
| `stopped` | 模块未启动 |
| `starting` | 启动中，加载密钥库 |
| `running` | 运行中，密钥库就绪 |
| `ready` | 运行中，可进行加密/解密操作 |
| `error` | 发生错误 |

---

## 3. 配置结构

### 3.1 模块配置

```json
{
  "modules": {
    "crypto": {
      "enabled": true,
      "config": {
        "algorithm": {
          "symmetric": "chacha20poly1305",
          "asymmetric": "x25519",
          "signature": "ed25519",
          "hash": "blake3"
        },
        "keyManagement": {
          "storage": "keychain",
          "autoRotate": true,
          "rotateInterval": 2592000,
          "keyDerivation": "hkdf"
        },
        "randomness": {
          "source": "system"
        }
      }
    }
  }
}
```

### 3.2 配置字段详解

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `algorithm.symmetric` | String | `chacha20poly1305` | 对称加密算法 |
| `algorithm.asymmetric` | String | `x25519` | 非对称密钥交换 |
| `algorithm.signature` | String | `ed25519` | 数字签名算法 |
| `keyManagement.storage` | String | `keychain` | 密钥存储位置 |
| `keyManagement.autoRotate` | Boolean | `true` | 是否自动轮换密钥 |
| `keyManagement.rotateInterval` | Integer | `2592000` | 密钥轮换间隔（秒），默认 30 天 |
| `keyManagement.keyDerivation` | String | `hkdf` | 密钥派生函数 |

---

## 4. 方法注册表

### 4.1 支持的方法

| 方法 | 说明 | 参数 | 返回 |
|------|------|------|------|
| `generate_key` | 生成新的加密密钥 | type | { keyId, publicKey } |
| `encrypt` | 加密数据 | keyId, plaintext, aad | { ciphertext, nonce } |
| `decrypt` | 解密数据 | keyId, ciphertext, nonce, aad | { plaintext } |
| `sign` | 对数据签名 | keyId, data | { signature } |
| `verify` | 验证签名 | publicKey, data, signature | { valid: true/false } |
| `key_exchange` | 生成密钥交换材料 | localKeyId | { publicKey, ephemeralKey } |
| `derive_key` | 从主密钥派生会话密钥 | masterKey, info | { derivedKey } |
| `delete_key` | 删除密钥 | keyId | { deleted: true } |
| `list_keys` | 列出所有密钥 | — | { keys: [...] } |
| `encrypt_for_peer` | **通过 Core 调用**：端到端加密数据给指定对等方 | peerPublicKey, plaintext | { ciphertext, nonce, ephemeralPublicKey } |
| `decrypt_from_peer` | **通过 Core 调用**：解密来自指定对等方的数据 | peerPublicKey, ephemeralPublicKey, ciphertext, nonce | { plaintext } |
| `seal_box` | **通过 Core 调用**：简易加密（类似 NaCl secretbox）| peerPublicKey, plaintext | { ciphertext, nonce } |
| `open_seal` | **通过 Core 调用**：简易解密 | senderPublicKey, ciphertext, nonce | { plaintext } |

### 4.2 方法详细定义

#### generate_key

**描述**：生成新的加密密钥

**参数**：
```json
{
  "type": "x25519"
}
```

**type**：`x25519`（非对称密钥，用于密钥交换）或 `ed25519`（签名密钥）

**返回值**：
```json
{
  "keyId": "key_abc123",
  "type": "x25519",
  "publicKey": "base64_encoded_public_key",
  "createdAt": "2026-04-03T10:00:00Z",
  "expiresAt": null
}
```

---

#### encrypt

**描述**：使用对称密钥加密数据

**参数**：
```json
{
  "keyId": "key_abc123",
  "plaintext": "base64_encoded_data",
  "aad": "base64_encoded_additional_data"
}
```

**aad**：可选，附加认证数据（不加密，但会包含在签名中）

**返回值**：
```json
{
  "ciphertext": "base64_encoded_ciphertext",
  "nonce": "base64_encoded_nonce",
  "algorithm": "chacha20poly1305"
}
```

---

#### decrypt

**描述**：使用对称密钥解密数据

**参数**：
```json
{
  "keyId": "key_abc123",
  "ciphertext": "base64_encoded_ciphertext",
  "nonce": "base64_encoded_nonce",
  "aad": "base64_encoded_additional_data"
}
```

**返回值**：
```json
{
  "plaintext": "base64_encoded_plaintext",
  "algorithm": "chacha20poly1305"
}
```

**错误码**：
| code | message | 说明 |
|------|---------|------|
| 400 | INVALID_KEY | 密钥不存在或已过期 |
| 401 | DECRYPT_FAILED | 解密失败（密文被篡改或密钥错误）|

---

#### encrypt_for_peer（模块间通信接口）

**描述**：端到端加密数据，只有指定对等方能解密（通过 Core 调用）

**调用方式**（其他模块通过 Core 调用）：
```json
{
  "id": "msg_000001",
  "type": "REQUEST",
  "action": "modules.call",
  "payload": {
    "moduleId": "crypto",
    "method": "encrypt_for_peer",
    "args": {
      "peerPublicKey": "base64_peer_public_key",
      "plaintext": "base64_data_to_encrypt"
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
    "ciphertext": "base64_encrypted_data",
    "nonce": "base64_nonce",
    "ephemeralPublicKey": "base64_ephemeral_key",
    "algorithm": "x25519+chacha20poly1305"
  }
}
```

**原理**：
```
发送方：
1. 生成临时密钥对（ephemeral key）
2. 用临时私钥 + 对方公钥 → 共享密钥（X25519）
3. 用共享密钥 + ChaCha20Poly1305 加密数据
4. 把密文 + 临时公钥 + nonce 发给接收方

接收方：
1. 用临时公钥 + 自己私钥 → 同样的共享密钥
2. 解密数据
```

**特点**：
- 每个消息使用不同的临时密钥（前向安全）
- 接收方不需要事先知道发送方的密钥
- 中间人无法解密（不知道临时私钥）

---

#### decrypt_from_peer（模块间通信接口）

**描述**：解密来自指定对等方的加密数据

**调用方式**：
```json
{
  "id": "msg_000002",
  "type": "REQUEST",
  "action": "modules.call",
  "payload": {
    "moduleId": "crypto",
    "method": "decrypt_from_peer",
    "args": {
      "peerPublicKey": "base64_peer_public_key",
      "ephemeralPublicKey": "base64_ephemeral_key",
      "ciphertext": "base64_encrypted_data",
      "nonce": "base64_nonce"
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
    "plaintext": "base64_decrypted_data"
  }
}
```

---

#### seal_box / open_seal

**描述**：简易加密接口，类似 NaCl secretbox，适合不想关心密钥交换细节的场景

**seal_box 参数**：
```json
{
  "peerPublicKey": "base64_recipient_public_key",
  "plaintext": "base64_data"
}
```

**seal_box 返回**：
```json
{
  "ciphertext": "base64_ciphertext_with_nonce_prefixed"
}
```

**open_seal 参数**：
```json
{
  "senderPublicKey": "base64_sender_public_key",
  "ciphertext": "base64_ciphertext_with_nonce_prefixed"
}
```

**open_seal 返回**：
```json
{
  "plaintext": "base64_decrypted_data"
}
```

---

#### sign / verify

**描述**：数字签名，用于验证数据来源和完整性

**sign 参数**：
```json
{
  "keyId": "key_sign_001",
  "data": "base64_data_to_sign"
}
```

**sign 返回**：
```json
{
  "signature": "base64_ed25519_signature"
}
```

**verify 参数**：
```json
{
  "publicKey": "base64_public_key",
  "data": "base64_original_data",
  "signature": "base64_signature_to_verify"
}
```

**verify 返回**：
```json
{
  "valid": true
}
```

---

## 5. 事件

### 5.1 Crypto 模块推送的事件

| 事件 | 说明 | 触发时机 |
|------|------|---------|
| `crypto.started` | Crypto 模块启动 | 模块状态变为 running |
| `crypto.stopped` | Crypto 模块停止 | 模块状态变为 stopped |
| `crypto.error` | Crypto 模块错误 | 发生可捕获错误 |
| `crypto.key_generated` | 新密钥生成 | generate_key 成功 |
| `crypto.key_rotated` | 密钥轮换 | 自动或手动轮换密钥 |
| `crypto.key_deleted` | 密钥删除 | delete_key 成功 |
| `crypto.operation_failed` | 操作失败 | encrypt/decrypt/sign 失败 |

### 5.2 事件格式

```json
{
  "type": "EVENT",
  "event": "crypto.key_rotated",
  "payload": {
    "keyId": "key_abc123",
    "previousKeyId": "key_previous",
    "reason": "scheduled"
  },
  "timestamp": "2026-04-03T10:00:00Z"
}
```

---

## 6. 模块间通信（通过 Core）

> **核心原则**：所有模块间通信必须通过 Core，`modules.call` 是唯一入口。

### 6.1 通信架构

```
P2P 模块              Core                    Crypto 模块
    │                  │                         │
    │ ──▶ modules.call │ ──▶ module.call() ────▶│
    │                  │                         │
    │                  │◀── result ─────────────│
    │◀── result ───────│                         │
```

**使用场景**：
- P2P 模块：加密传输的数据内容
- Tor/I2P 模块：端到端加密，不依赖传输层的加密
- CRDT 模块：加密同步的文件内容
- 任何需要加密数据的模块

### 6.2 典型使用流程

**场景：P2P 模块通过 Crypto 模块加密传输数据**

```
第一步：交换密钥
───────────────────────────────────────

手机 P2P ──▶ Crypto 模块
  └─ generate_key { type: "x25519" }
       └─ 生成密钥对，返回 publicKey

手机把 publicKey 分享给电脑（通过 P2P 模块）
电脑也做同样操作，拿到手机的 publicKey

第二步：发送方加密
───────────────────────────────────────

手机 P2P ──▶ Core ──▶ Crypto 模块
  └─ encrypt_for_peer {
       peerPublicKey: "电脑的公钥",
       plaintext: "要发的数据"
    }
       └─ 返回 { ciphertext, nonce, ephemeralPublicKey }

手机 P2P 发送：ciphertext + nonce + ephemeralPublicKey 给电脑

第三步：接收方解密
───────────────────────────────────────

电脑 P2P ──▶ Core ──▶ Crypto 模块
  └─ decrypt_from_peer {
       peerPublicKey: "手机的公钥",
       ephemeralPublicKey: "刚才的临时公钥",
       ciphertext: "收到的密文",
       nonce: "刚才的nonce"
    }
       └─ 返回 { plaintext }
```

---

## 7. 安全模型

### 7.1 加密算法安全级别

| 算法 | 安全级别 | 说明 |
|------|---------|------|
| ChaCha20Poly1305 | 256-bit | 现代流加密，无已知弱点 |
| X25519 | 256-bit | 椭圆曲线密钥交换 |
| Ed25519 | 256-bit | 高性能签名算法 |
| BLAKE3 | 256-bit | 快速哈希，适合 HMAC |

### 7.2 安全特性

| 特性 | 说明 |
|------|------|
| 前向安全 | 每个消息使用临时密钥，即使长期密钥泄露也不影响历史消息 |
| 抗中间人 | 密钥交换基于 ECDH，中间人无法获取共享密钥 |
| 认证加密 | AEAD 模式，同时加密和完整性验证，无法篡改 |
| 抗侧信道 | ChaCha20 不易受时序攻击影响 |
| 密钥轮换 | 自动定期轮换密钥，减少密钥泄露风险 |

### 7.3 密钥存储

| 平台 | 存储位置 |
|------|---------|
| Android | Android Keystore（硬件支持）|
| iOS | iOS Keychain（硬件支持）|
| Linux | systemd-cryptenroll 或 pass |
| macOS | Keychain |
| Windows | DPAPI 或 Credential Manager |

---

## 8. manifest.json

```json
{
  "manifestVersion": "1.0",
  "module": {
    "id": "crypto",
    "name": "Cryptography",
    "nameZh": "加密模块",
    "version": "0.1.0",
    "description": "End-to-end encryption, key management, and digital signatures",
    "descriptionZh": "端到端加密、密钥管理和数字签名",
    "icon": "modules/icons/crypto.png"
  },
  "developer": {
    "name": "Cloudfin Team",
    "email": "dev@cloudfin.io",
    "website": "https://cloudfin.io",
    "github": "https://github.com/indivisible2025/cloudfin-mod-crypto"
  },
  "license": {
    "spdx": "Apache-2.0",
    "name": "Apache License 2.0",
    "url": "https://www.apache.org/licenses/LICENSE-2.0",
    "commercial": true
  },
  "links": {
    "source": "https://github.com/indivisible2025/cloudfin-mod-crypto",
    "documentation": "https://docs.cloudfin.io/modules/crypto",
    "bugReport": "https://github.com/indivisible2025/cloudfin-mod-crypto/issues",
    "changelog": "https://github.com/indivisible2025/cloudfin-mod-crypto/blob/main/CHANGELOG.md"
  },
  "compatibility": {
    "coreMinVersion": "0.1.0",
    "coreMaxVersion": "0.9.9",
    "platforms": ["linux", "macos", "android", "windows"]
  },
  "dependencies": {
    "native": [],
    "rust": ["ring", "x25519-dalek", "chacha20poly1305", "blake3", "hkdf", "rand"],
    "system": []
  },
  "permissions": {
    "canCall": ["p2p", "tor", "i2p", "crdt", "storage"],
    "canSubscribe": [],
    "canAccessConfig": false
  },
  "security": {
    "sandboxed": true,
    "networkAccess": "none",
    "fileAccess": "none"
  },
  "build": {
    "output": "modules/libcloudfin_crypto.so",
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
□ 11 个 Crypto 方法已定义（含 encrypt_for_peer / decrypt_from_peer 供其他模块调用）
□ 所有方法的 JSON Schema 完整
□ 加密算法（ChaCha20Poly1305 / X25519 / Ed25519 / BLAKE3）已说明
□ 前向安全特性已说明
□ 密钥存储（Keychain/Keystore）平台适配已说明
□ 事件推送 7 种类型完整
□ 模块状态机 5 个状态正确
□ 适用范围/情况已列出
□ encrypt_for_peer / decrypt_from_peer / seal_box / open_seal 定义为外部接口
□ manifest.json 所有必填字段完整
□ 许可证为 Apache-2.0（允许商用）
```

---

*本文档由 墨尘 🌙 编写*  
*最后更新：2026-04-03*
