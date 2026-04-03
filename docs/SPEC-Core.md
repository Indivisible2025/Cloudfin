# Cloudfin Core 技术规格

> 版本：v0.1.0  
> 日期：2026-04-03  
> 状态：**草案**

## 1. 概述

Cloudfin Core 是整个框架的核心守护进程，负责：
- 模块加载与管理
- HTTP API 服务
- 配置管理
- 日志管理
- 模块间协调

## 2. 架构

```
┌────────────────────────────────────────┐
│           Cloudfin Core (Daemon)          │
│                                          │
│  ┌─────────────┐  ┌─────────────────┐  │
│  │ HTTP API   │  │  Plugin Manager  │  │
│  │  (Axum)    │  │  (libloading)   │  │
│  └─────────────┘  └────────┬────────┘  │
│                             │            │
│  ┌─────────────┐  ┌────────▼────────┐  │
│  │   Config   │  │  Plugin Common   │  │
│  │  Manager   │  │    (Trait)      │  │
│  └─────────────┘  └─────────────────┘  │
└────────────────────────────────────────┘
```

## 3. 技术栈

| 组件 | 技术 |
|------|------|
| HTTP Server | Axum 0.7 |
| Async Runtime | Tokio |
| Dynamic Loading | libloading |
| Serialization | serde + serde_json |
| Logging | tracing + tracing-subscriber |

## 4. 目录结构

```
core/
├── Cargo.toml
└── src/
    ├── main.rs          # 入口
    ├── lib.rs           # 核心库
    ├── api/             # HTTP API handlers
    ├── config/          # 配置管理
    ├── logging/         # 日志
    └── plugins/         # 插件管理

plugins/common/
├── Cargo.toml
└── src/
    ├── lib.rs
    ├── module.rs        # Module trait
    └── manifest.rs      # Manifest 格式
```

## 5. 模块系统

### 5.1 Module Trait

所有 Cloudfin 模块必须实现 `Module` trait：

```rust
pub trait Module: Send + Sync {
    fn id(&self) -> &str;
    fn name(&self) -> &str;
    fn version(&self) -> &str;
    fn init(&mut self, config: Value) -> anyhow::Result<()>;
    fn start(&mut self) -> anyhow::Result<()>;
    fn stop(&mut self) -> anyhow::Result<()>;
    fn status(&self) -> ModuleHealth;
}
```

### 5.2 Module State

```
Uninitialized → Initialized → Running → Stopped
                    ↓
                  Error
```

### 5.3 Module Manifest

每个模块目录必须包含 `manifest.json`：

```json
{
  "id": "p2p",
  "name": "P2P Module",
  "version": "0.1.0",
  "description": "P2P networking module",
  "author": "Cloudfin Team",
  "rust_version": "1.70+",
  "dependencies": {},
  "permissions": ["Network"],
  "api_version": "v1"
}
```

## 6. 配置

配置文件：`~/.config/cloudfin/config.json`

```json
{
  "device_id": "uuid-v4",
  "device_name": "my-device",
  "listen_host": "127.0.0.1",
  "listen_port": 19001,
  "modules_dir": "./modules"
}
```

## 7. 构建

```bash
# 开发构建
cargo build --workspace

# 发布构建
cargo build --release --workspace

# 运行
./target/release/cloudfin-core
```
