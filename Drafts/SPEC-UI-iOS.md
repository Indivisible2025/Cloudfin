# Cloudfin iOS UI 规格

> 版本：v0.1.0-draft  
> 日期：2026-04-03  
> 状态：**草案 — 待完善**  
> 依赖：SPEC-UI-Common.md（跨平台通用设计）

---

## 1. 概述

本文档定义 **iOS 平台**的 UI 适配规范，补充 SPEC-UI-Common.md 中的通用设计。

---

## 2. 技术栈

| 组件 | 技术 | 说明 |
|------|------|------|
| **UI 框架** | SwiftUI | Apple 官方推荐 |
| **语言** | Swift | 最新稳定版 |
| **最低版本** | iOS 16.0 | 覆盖 90%+ 设备 |
| **目标版本** | iOS 18.0 | 最新特性 |

---

## 3. iOS 特定限制

### 3.1 系统权限

| 权限 | Info.plist Key | 用途 | 申请时机 |
|------|---------------|------|---------|
| `网络` | — | 使用无需申请 | — |
| `本地网络` | `NSLocalNetworkUsageDescription` | P2P 发现 | 首次使用本地功能时 |
| `后台刷新` | `UIBackgroundModes` | 后台同步 | 系统设置 |
| `通知` | `UNUserNotificationCenter` | 推送通知 | 首次启动后 |

### 3.2 后台限制

| 限制 | 影响 | 解决方案 |
|------|------|---------|
| App 切换器冻结 | App 切换后暂停 | 使用 Background Tasks |
| 系统冻结后台 | iOS 优先冻结非活跃 App | 使用 BGTaskScheduler |
| 省电模式 | 限制后台网络 | 引导用户关闭低电量模式 |
| 沙盒限制 | 不能访问其他 App 数据 | 使用 App Group 共享数据 |

### 3.3 系统组件适配

| 组件 | iOS 规范 | 说明 |
|------|---------|------|
| **导航** | TabView + NavigationStack | 底部 Tab + 内嵌导航 |
| **手势** | iOS 手势系统 | 全局手势支持 |
| **Home 键** | — | Face ID 设备无 Home 键 |
| **多任务** | App Switcher | 支持 App Exposé |
| **灵动岛** | Dynamic Island | iPhone 14 Pro+ 支持 |

---

## 4. iOS 特有功能

### 4.1 App Group

用于 UI 和 Core 之间的数据共享：
```swift
// App Group ID
group.com.cloudfin.app
```

### 4.2 后台刷新

| 任务类型 | 用途 |
|---------|------|
| `BGAppRefreshTask` | 定期检查更新 |
| `BGProcessingTask` | 大量数据同步 |

### 4.3 Widget（未来扩展）

| Widget 类型 | 尺寸 | 用途 |
|-----------|------|------|
| 小号 | systemSmall | 显示 Core 状态 |
| 中号 | systemMedium | 显示模块连接状态 |
| 大号 | systemLarge | 显示同步进度 |

---

## 5. 与 SPEC-UI-Common.md 的差异

### 5.1 需要适配的部分

| 通用设计 | iOS 适配 |
|---------|---------|
| 底部导航 | SwiftUI TabView |
| 状态卡片 | 自定义 View |
| 按钮样式 | SwiftUI Button / Menu |
| 对话框 | SwiftUI .alert / .sheet |
| 加载指示器 | ProgressView |

### 5.2 iOS 独有的功能

| 功能 | 说明 |
|------|------|
| Face ID / Touch ID | Core 解锁验证（未来）|
| 实时活动 | Lock Screen 显示同步进度（iOS 16.1+）|
| 灵动岛 | 显示 Core 状态（iPhone 14 Pro+）|
| App Clips | 轻量级安装（未来）|

---

## 6. 交互适配

### 6.1 手势操作

| 手势 | 操作 |
|------|------|
| 左滑卡片 | 显示快捷操作（iOS 风格）|
| 下拉 | 刷新（native Pull to Refresh）|
| 长按 | 显示上下文菜单（contextMenu）|

### 6.2 系统按钮

| 按钮 | 处理方式 |
|------|---------|
| 返回 | NavigationStack 自动处理 |
| Home | App 进入后台，Core 继续运行 |
| 多任务 | App 显示在 App Switcher 中 |

---

## 7. App Store 审核注意事项

| 要求 | 说明 |
|------|------|
| 后台运行 | 必须有合理的后台用途（如 VOIP、地图导航）|
| 网络权限 | 必须说明为何需要本地网络权限 |
| 隐私清单 | 发布前填写隐私清单 |

---

## 8. 待完善项

```
□ 详细的 SwiftUI 组件规范
□ iOS 16+ 实时活动（Live Activities）规范
□ iOS 17+ App Intents 规范
□ iPad 多窗口适配
□ visionOS 支持（未来）
```

---

## 9. 审查清单

```
□ 技术栈已定义（SwiftUI + Swift）
□ 系统权限清单已列出
□ 后台限制及解决方案已说明
□ App Group 配置已说明
□ SwiftUI 适配已说明
□ 手势操作已定义
□ App Store 审核注意事项已列出
□ 待完善项已标记
```

---

*本文档由 墨尘 🌙 编写*  
*最后更新：2026-04-03*
