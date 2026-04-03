# Cloudfin Android UI 规格

> 版本：v0.1.0-draft  
> 日期：2026-04-03  
> 状态：**草案 — 待完善**  
> 依赖：SPEC-UI-Common.md（跨平台通用设计）

---

## 1. 概述

本文档定义 **Android 平台**的 UI 适配规范，补充 SPEC-UI-Common.md 中的通用设计。

---

## 2. 技术栈

| 组件 | 技术 | 说明 |
|------|------|------|
| **UI 框架** | Jetpack Compose | Google 官方推荐 |
| **语言** | Kotlin | 与 Android 系统集成 |
| **最低版本** | API 26 (Android 8.0) | 覆盖 95%+ 设备 |
| **目标版本** | API 34 (Android 14) | 最新特性 |

---

## 3. Android 特定限制

### 3.1 系统权限

| 权限 | 用途 | 申请时机 |
|------|------|---------|
| `INTERNET` | 网络通信 | 安装时自动授予 |
| `FOREGROUND_SERVICE` | 后台服务 | 首次启动 Core 时申请 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | 特定前台服务 | 需要时申请 |
| `POST_NOTIFICATIONS` | 通知 | 首次启动后申请（可选）|
| `READ_EXTERNAL_STORAGE` | 读取文件 | 同步功能需要 |
| `WRITE_EXTERNAL_STORAGE` | 写入文件 | 同步功能需要 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 忽略电池优化 | 保持后台运行需要 |

### 3.2 后台限制

| 限制 | 影响 | 解决方案 |
|------|------|---------|
| 后台执行限制 | Core 可能被系统杀死 | 使用 Foreground Service + 通知 |
| 省电模式 | 可能限制后台 | 引导用户加入白名单 |
| 应用待机 | 可能限制后台 | 使用 WorkManager 进行定期同步 |

### 3.3 系统组件适配

| 组件 | Android 规范 | 说明 |
|------|-------------|------|
| **导航栏** | Material Design 3 | 底部 NavigationBar |
| **状态栏** | 系统状态栏 | 深色/浅色主题适配 |
| **系统弹窗** | Activity Recognition Permission | 步数等权限 |
| **返回键** | Back Gesture | 支持手势和按键 |
| **多窗口** | 分屏模式 | 支持分屏操作 |

---

## 4. Android 特有组件

### 4.1 通知渠道

| 渠道 ID | 名称 | 说明 |
|---------|------|------|
| `core_service` | Cloudfin Core | Core 后台运行通知 |
| `sync` | 同步通知 | 同步进度和状态 |
| `connection` | 连接通知 | P2P/Tor/I2P 连接状态 |

### 4.2 Foreground Service 通知

Core 作为前台服务运行时，显示持久通知：
```
┌─────────────────────────────────────────┐
│  [云鳞图标]                               │
│                                          │
│  Cloudfin Core 正在运行                  │
│  已连接 P2P + Tor                        │
│                                          │
│  [断开]                    [打开 App]    │
└─────────────────────────────────────────┘
```

---

## 5. 与 SPEC-UI-Common.md 的差异

### 5.1 需要适配的部分

| 通用设计 | Android 适配 |
|---------|-------------|
| 底部导航 | Material 3 NavigationBar |
| 状态卡片 | Material Card |
| 按钮样式 | Material 3 Button |
| 对话框 | Material 3 AlertDialog |
| 加载指示器 | CircularProgressIndicator |
| 下拉刷新 | PullRefreshIndicator |

### 5.2 Android 独有的功能

| 功能 | 说明 |
|------|------|
| 系统电池白名单 | 引导用户加入白名单 |
| 自启动管理 | 引导用户开启自启动权限 |
| 应用通知管理 | 引导用户开启通知权限 |
| 省电管理 | 引导用户关闭省电优化 |

---

## 6. 交互适配

### 6.1 手势操作

| 手势 | 操作 |
|------|------|
| 左滑卡片 | 显示快捷操作（断开、设置）|
| 下拉 | 刷新页面 |
| 长按模块卡片 | 显示模块详情弹窗 |

### 6.2 系统按钮

| 按钮 | 处理方式 |
|------|---------|
| 返回键 | 返回上一页面，最后页面时最小化到后台 |
| Home 键 | Core 继续后台运行 |
| 多任务键 | 显示任务列表，Cloudfin 显示为单个任务 |

---

## 7. 待完善项

```
□ 详细的 Material Design 3 组件规范
□ Android 12+ Splash Screen 规范
□ Android 13+ 通知权限适配
□ Android 14+ 后台 foreground service 限制
□ 折叠屏设备适配
□ Android Auto 支持（未来）
```

---

## 8. 审查清单

```
□ 技术栈已定义（Compose + Kotlin）
□ 系统权限清单已列出
□ 后台限制及解决方案已说明
□ Foreground Service 通知已定义
□ Material Design 3 适配已说明
□ 手势操作已定义
□ 与通用设计的差异已列出
□ 待完善项已标记
```

---

*本文档由 墨尘 🌙 编写*  
*最后更新：2026-04-03*
