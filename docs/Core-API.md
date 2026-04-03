# Cloudfin Core HTTP API

> 版本：v1  
> Base URL: http://127.0.0.1:19001

## 通用响应格式

```json
{
  "ok": true,
  "data": { ... },
  "error": null
}
```

错误时：
```json
{
  "ok": false,
  "data": null,
  "error": "error message"
}
```

---

## Core

### GET /api/core/status

获取 Core 状态

**响应**
```json
{
  "ok": true,
  "data": {
    "version": "0.1.0",
    "uptime_secs": 3600,
    "modules": {},
    "config": {
      "device_id": "uuid",
      "device_name": "cloudfin",
      "listen_host": "127.0.0.1",
      "listen_port": 19001,
      "modules_dir": "./modules"
    }
  }
}
```

---

## Config

### GET /api/config

获取配置

**响应**
```json
{
  "ok": true,
  "data": {
    "device_id": "uuid",
    "device_name": "cloudfin",
    "listen_host": "127.0.0.1",
    "listen_port": 19001,
    "modules_dir": "./modules"
  }
}
```

### PATCH /api/config

更新配置

**请求体**
```json
{
  "device_name": "new-name",
  "listen_host": "0.0.0.0",
  "listen_port": 19001,
  "modules_dir": "/opt/cloudfin/modules"
}
```

---

## Modules

### GET /api/modules

列出所有已注册的模块

**响应**
```json
{
  "ok": true,
  "data": [
    {
      "id": "p2p",
      "name": "P2P Module",
      "path": "./modules/cloudfin-p2p.so",
      "status": "loaded",
      "registered_at": "2026-04-03T12:00:00Z"
    }
  ]
}
```

### POST /api/modules

注册新模块

**请求体**
```json
{
  "id": "p2p",
  "name": "P2P Module",
  "path": "./modules/cloudfin-p2p.so",
  "port": 19101
}
```

### DELETE /api/modules/:id

注销模块

---

## Plugins

### POST /api/plugins/load

动态加载插件

**请求体**
```json
{
  "path": "/path/to/module.so"
}
```

### DELETE /api/plugins/unload/:id

卸载插件

---

## 错误码

| HTTP Status | error | 含义 |
|-------------|--------|------|
| 400 | bad request | 请求格式错误 |
| 404 | not found | 资源不存在 |
| 500 | internal error | 服务器内部错误 |
