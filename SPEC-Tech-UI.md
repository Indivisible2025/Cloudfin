# Cloudfin UI 技术文档

**版本：** v1.0
**日期：** 2026-04-05
**状态：** Draft（待评审）
**级别：** L3（路线级）

> 本文档为 Cloudfin UI 层技术规格，基于 L0（SPEC-Design.md）、L1（SPEC-Tech-Core.md）和 L2（SPEC-Impl-Core.md）驱动。UI 为独立进程，通过 WebSocket 与 Core 通信，可选任意平台原生技术栈实现。

---

## 1. UI 框架选型

### 1.1 跨平台策略

UI 与 Core **严格分离**，UI 是独立进程。Core 只暴露 WebSocket 一个接口，UI 无需了解 Core 内部实现，只需遵循 Core API 协议。

**技术选型原则：**

| 原则 | 说明 |
|------|------|
| 平台原生优先 | 每个平台使用该平台最主流的 UI 框架，确保最佳用户体验 |
| 渲染逻辑统一 | 所有平台共享相同的 UI 状态模型和 WebSocket 通信逻辑，便于维护 |
| 可复用层抽离 | 业务逻辑（状态管理、WebSocket 通信）跨平台复用，平台特定层仅负责渲染 |

**平台技术栈汇总：**

| 平台 | 推荐框架 | 说明 |
|------|---------|------|
| Android | Kotlin + Jetpack Compose | 声明式 UI，Google 官方推荐 |
| iOS | Swift + SwiftUI | Apple 声明式 UI，与系统深度集成 |
| Desktop (Linux/macOS) | Qt / GTK / Electron | 跨桌面统一，或使用平台官方框架 |
| Desktop (Windows) | WinUI 3 / Qt / Electron | Windows 11 官方设计语言 |

### 1.2 Android：Kotlin + Jetpack Compose

**理由：** 声明式 UI 与 Compose 契合度高，Google 官方持续投入，生态成熟。

**最低版本：** Android API 26（Android 8.0），覆盖率 >95%。

**架构建议（MVVM）：**

```
UI Layer (Composable)
    ↓ observe
ViewModel (StateFlow)
    ↓ call
Repository (CloudfinRepository)
    ↓ WebSocket
Core (ws://127.0.0.1:19001/ws)
```

**关键依赖：**

```kotlin
// build.gradle.kts (Android)
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.compose.ui:compose-ui:1.6.0")
    implementation("com.squareup.okhttp:okhttp:4.12.0") // WebSocket 客户端
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}
```

**WebSocket 客户端封装（Kotlin）：**

```kotlin
class CloudfinWebSocket(
    private val scope: CoroutineScope,
    private val serverUri: String = "ws://127.0.0.1:19001/ws"
) {
    private var webSocket: WebSocket? = null
    private val _messages = MutableSharedFlow<WsMessage>(replay = 0)
    val messages: SharedFlow<WsMessage> = _messages

    private val client = OkHttpClient.Builder()
        .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun connect() {
        val request = Request.Builder().url(serverUri).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = Json.decodeFromString<WsMessage>(text)
                scope.launch { _messages.emit(msg) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch { reconnect() }
            }
        })
    }

    suspend fun send(message: WsMessage): String {
        val requestId = UUID.randomUUID().toString()
        val outgoing = message.copy(requestId = requestId)
        webSocket?.send(Json.encodeToString(WsMessage.serializer(), outgoing))
        return requestId
    }
}
```

### 1.3 iOS：Swift + SwiftUI

**理由：** SwiftUI 是 Apple 主推声明式 UI，与 iOS 系统无缝集成，开发体验最佳。

**最低版本：** iOS 16.0，覆盖最新系统特性。

**架构建议（MVVM + Combine）：**

```
View (SwiftUI)
    ↓ @StateObject / @ObservedObject
ViewModel (@Observable)
    ↓
CloudfinRepository (async/await)
    ↓ URLSession WebSocket
Core (ws://127.0.0.1:19001/ws)
```

**WebSocket 客户端封装（Swift）：**

```swift
import Foundation
import Combine

actor CloudfinWebSocket: NSObject {
    private var webSocketTask: URLSessionWebSocketTask?
    private let session: URLSession
    private var pendingRequests: [String: CheckedContinuation<WsMessage, Error>] = [:]

    let messageStream = PassthroughSubject<WsMessage, Never>()

    init() {
        self.session = URLSession(
            configuration: .default,
            delegate: nil,
            delegateQueue: OperationQueue()
        )
        super.init()
    }

    func connect() async throws {
        guard let url = URL(string: "ws://127.0.0.1:19001/ws") else {
            throw WSError.invalidURL
        }
        webSocketTask = session.webSocketTask(with: url)
        webSocketTask?.resume()
        await startReceiving()
        try await sendPingLoop()
    }

    private func startReceiving() async {
        guard let task = webSocketTask else { return }
        do {
            let message = try await task.receive()
            if case .string(let text) = message {
                let wsMessage = try JSONDecoder().decode(WsMessage.self, from: Data(text.utf8))
                messageStream.send(wsMessage)
            }
            await startReceiving()
        } catch {
            // 重连逻辑在调用方处理
        }
    }

    func send(_ message: WsMessage) async throws -> String {
        let requestId = UUID().uuidString
        var outgoing = message
        outgoing.requestId = requestId

        let data = try JSONEncoder().encode(outgoing)
        try await webSocketTask?.send(.data(data))

        return requestId
    }

    private func sendPingLoop() async throws {
        while !Task.isCancelled {
            try await Task.sleep(nanoseconds: 30_000_000_000) // 30s
            try await webSocketTask?.sendPing { error in
                if let error = error {
                    print("Ping failed: \(error)")
                }
            }
        }
    }
}
```

### 1.4 Desktop：平台原生优先

| 平台 | 推荐框架 | 备选 |
|------|---------|------|
| Linux | Qt 6 (QML/Compose) 或 GTK4 (libadwaita) | Electron |
| macOS | SwiftUI（与 iOS 共用大部分代码）| Qt |
| Windows | WinUI 3 或 Qt 6 | Electron |

**Electron 备选方案：** 若需要快速跨平台一致体验，可用 Electron（Chromium + Node.js），但需注意打包体积和内存占用。

**跨平台共享层（Dart/Flutter 备选路径）：**

若团队熟悉 Dart/Flutter，也可使用 Flutter 实现全平台 UI，通过 FFI 调用 Core（Rust）或 WebSocket 连接 Core。优点是 UI 一致性高，缺点是失去平台原生体验。

---

## 2. UI ↔ Core 通信

### 2.1 WebSocket 连接（ws://127.0.0.1:19001/ws）

Core **仅暴露 WebSocket 一个端点**，UI 与 Core 之间所有通信均通过此连接。

| 项目 | 值 |
|------|---|
| 地址 | `ws://127.0.0.1:19001/ws` |
| 协议 | WebSocket（HTTP/1.1 升级）|
| 序列化 | JSON |
| 心跳 | 30 秒 ping / 60 秒无响应判断开 |
| 重连策略 | 指数退避（1s → 2s → 4s → max 30s）|
| 请求超时 | 5 秒 |

**连接建立流程（UI 侧）：**

```
UI 启动
  → 检测 Core 是否监听 19001 端口（TCP探测）
  → 未监听 → 显示"核心未安装"页面
  → 已监听 → 建立 WebSocket 连接
  → 连接成功 → 发送 core.info 获取版本
  → 渲染左侧栏五分栏
```

### 2.2 连接生命周期

**连接状态机（UI 侧）：**

```
Disconnected
    │
    │ connect()
    ▼
Connecting
    │
    │ onOpen
    ▼
Connected ────────────────────────► onClose / onError
    │                                      │
    │ ◄────── 指数退避重连 ──────────────►  │
    ▼                                      ▼
Receiving                          Disconnected
```

**Core 推送的连接时事件序列：**

```
连接建立（WebSocket onOpen）
  → 立即收到 status_snapshot（自动推送）
  → UI 根据 snapshot 渲染初始状态
  → 进入正常事件循环（config_changed / module_health 等）
```

**Core 向 UI 推送的 Event 列表（L1 §2.3）：**

| Event | 触发时机 |
|-------|---------|
| `status_snapshot` | 连接时自动推送 + 定期（每 60s）|
| `config_changed` | 配置热更新后广播 |
| `module_health` | 模块健康状态变化时广播 |
| `module_event` | 模块主动推送的消息 |

### 2.3 请求响应模式（request_id）

所有请求均为 **JSON-RPC 风格**，使用 `request_id` 关联请求与响应。

**通用请求格式：**

```json
{
  "type": "request",
  "action": "<action_name>",
  "request_id": "uuid-v4",
  "data": {}
}
```

**成功响应格式：**

```json
{
  "type": "response",
  "request_id": "uuid-v4",
  "data": { ... }
}
```

**错误响应格式（L2 §2.3）：**

```json
{
  "type": "error",
  "request_id": "uuid-v4",
  "error": {
    "code": "ERR_MODULE_NOT_FOUND",
    "message": "Module 'cloudfin.foo' is not loaded"
  }
}
```

**Core 主动推送（无 request_id）：**

```json
{
  "type": "event",
  "event": "module.health_changed",
  "data": {
    "id": "cloudfin.p2p",
    "health": "Degraded",
    "reason": "Peer connection lost"
  }
}
```

**核心 API Actions（L2 §2.2）：**

| Action | 方向 | 说明 |
|--------|------|------|
| `core.info` | C→S | 查询 Core 版本信息（连接后首个调用）|
| `module.list` | C→S | 列出已加载模块 |
| `module.load` | C→S | 加载指定模块 |
| `module.unload` | C→S | 卸载指定模块 |
| `module.call` | C→S | 调用模块方法 |
| `module.status` | C→S | 获取模块状态 |
| `config.get` | C→S | 获取配置项 |
| `config.set` | C→S | 设置配置项 |

**错误码速查表（L2 §2.3）：**

| 错误码 | 含义 |
|--------|------|
| `ERR_MODULE_NOT_FOUND` | 模块不存在 |
| `ERR_MODULE_ALREADY_LOADED` | 模块已加载 |
| `ERR_METHOD_NOT_FOUND` | 模块方法不存在 |
| `ERR_INVALID_PARAMS` | 参数无效 |
| `ERR_INTERNAL` | 内部错误 |
| `ERR_TIMEOUT` | 操作超时 |

### 2.4 心跳与重连

**心跳机制（L0 §3.2）：**

- UI 每 30 秒向 Core 发送 `ping`（通过 WebSocket ping 帧）
- Core 自动回复 `pong`（WebSocket 协议层）
- 若 60 秒内未收到任何响应，判定连接断开

**自定义应用层心跳（备用）：**

```json
{
  "type": "request",
  "action": "ping",
  "request_id": "ui_ping_001",
  "data": {}
}
```

**指数退避重连（UI 侧实现）：**

```kotlin
// Kotlin 示例
class ReconnectPolicy {
    private var attempts = 0
    private val maxDelay = 30_000L // 30s

    fun nextDelayMillis(): Long {
        val delay = minOf(1000L * (1 shl attempts), maxDelay)
        attempts++
        return delay
    }

    fun reset() { attempts = 0 }
}
```

```swift
// Swift 示例
struct ReconnectPolicy {
    private var attempts = 0
    private let maxDelay: UInt64 = 30_000_000_000 // 30s in ns

    mutating func nextDelay() -> UInt64 {
        let delay = min(UInt64(pow(2.0, Double(attempts))) * 1_000_000_000, maxDelay)
        attempts += 1
        return delay
    }

    mutating func reset() { attempts = 0 }
}
```

**重连触发时机：**

| 触发条件 | 动作 |
|---------|------|
| WebSocket `onClose` | 启动指数退避重连 |
| WebSocket `onFailure` | 启动指数退避重连 |
| 应用从后台恢复（移动端）| 立即重连一次 |
| 手动"重新连接"按钮 | 立即重连，复位退避计数器 |

### 2.5 主题系统（三模式，顺序固定）

**三种主题模式（L0 §2.4）：**

| 序号（固定）| 模式 | key | 说明 |
|-----------|------|-----|------|
| 1 | 系统默认 | `"system"` | 跟随操作系统设置，安装后默认 |
| 2 | 亮色模式 | `"light"` | 强制亮色 |
| 3 | 暗色模式 | `"dark"` | 强制暗色 |

**主题存储：** 存储在 UI 本地偏好设置，非 Core 配置。

**平台实现差异：**

| 平台 | 系统主题监听 | 强制主题 API |
|------|------------|-------------|
| Android | `Configuration.uiMode` | `AppCompatDelegate.setDefaultNightMode()` |
| iOS | `UITraitCollection.current` | `UIUserInterfaceStyle` |
| Linux (Qt) | QPalette / GTK Settings | Qt Stylesheet 切换 |
| macOS | `NSApp.effectiveAppearance` | `NSApp.appearance` |
| Windows | `UISettings.GetColorValue` | `ApplicationTheme` |

**UI 状态结构（主题相关字段）：**

```json
{
  "ui": {
    "theme": "system",
    "version": "v20260405.001",
    "platform": "android",
    "arch": "arm64-v8a"
  }
}
```

**CSS 变量方案（统一所有平台）：**

```css
:root {
  --bg-primary: #ffffff;
  --bg-secondary: #f5f5f5;
  --text-primary: #1a1a1a;
  --accent: #007AFF;
}

[data-theme="dark"] {
  --bg-primary: #1a1a1a;
  --bg-secondary: #2d2d2d;
  --text-primary: #ffffff;
  --accent: #0A84FF;
}
```

---

## 3. 模块页面动态渲染

### 3.1 manifest 解析

每个模块 ZIP 包包含两个文件（L0 §6.2）：

- **`{模块名}.so`**：二进制插件
- **`{模块名}.so.json`**：模块 manifest

**manifest JSON Schema（L2 §4）：**

```json
{
  "id": "cloudfin.p2p",
  "name": "P2P Networking",
  "version": "0.1.0",
  "description": "去中心化 P2P 网络模块",
  "author": "Cloudfin Team",
  "platform": ["android", "linux", "macos", "ios", "windows"],
  "permissions": ["network", "file_system"],
  "api_version": "^1.0",
  "ui": {
    "cards": [
      {
        "type": "info",
        "title": "节点信息",
        "fields": [
          { "key": "peer_id", "label": "Peer ID", "type": "text" },
          { "key": "listen_addr", "label": "监听地址", "type": "text" },
          { "key": "connected_peers", "label": "已连接节点", "type": "number" }
        ]
      },
      {
        "type": "action",
        "title": "操作",
        "actions": [
          { "label": "连接新节点", "method": "dial", "params": {} },
          { "label": "刷新状态", "method": "status", "params": {} }
        ]
      },
      {
        "type": "form",
        "title": "配置",
        "fields": [
          { "key": "max_peers", "label": "最大节点数", "type": "number", "default": 50 },
          { "key": "enable_upnp", "label": "启用 UPnP", "type": "toggle", "default": true }
        ]
      }
    ]
  }
}
```

**manifest 解析流程（UI 侧）：**

```
模块列表数据（来自 module.list）
  → 对每个模块
      → 加载 {模块名}.so.json
      → 校验 JSON Schema（L2 §4）
      → 提取 ui.cards[]
      → 渲染对应卡片
```

**manifest Schema 校验（伪代码）：**

```kotlin
fun parseManifest(json: String): Result<ModuleManifest> {
    return runCatching {
        val schema = JsonSchema.parse(manifestSchemaJson)
        val manifest = Json.decodeFromString<ModuleManifest>(json)
        if (!schema.validate(manifest)) {
            Result.failure(ManifestValidationError)
        } else {
            Result.success(manifest)
        }
    }
}
```

### 3.2 Cards 渲染规则

UI 根据 `manifest.ui.cards[]` 动态渲染卡片，无需为每个模块硬编码 UI。

**card 类型定义：**

| 类型 | 渲染控件 | 说明 |
|------|---------|------|
| `info` | 只读文本列表 | 显示模块静态/运行时信息 |
| `action` | 按钮组 | 触发模块方法调用（`module.call`）|
| `form` | 输入控件组 | 修改模块配置（`config.set`）|
| `chart` | 图表组件 | 显示模块统计数据（未来扩展）|

**info card 渲染规则：**

```json
{
  "type": "info",
  "title": "节点信息",
  "fields": [
    { "key": "peer_id", "label": "Peer ID", "type": "text" },
    { "key": "listen_addr", "label": "监听地址", "type": "text" },
    { "key": "connected_peers", "label": "已连接节点", "type": "number" }
  ]
}
```

- `type: "text"` → `Text` / `Label` 控件
- `type: "number"` → `NumberLabel` / 只读数字
- `type: "badge"` → 状态徽章（如模块健康状态）

**action card 渲染规则：**

```json
{
  "type": "action",
  "title": "操作",
  "actions": [
    { "label": "连接新节点", "method": "dial", "params": {} },
    { "label": "刷新状态", "method": "status", "params": {} }
  ]
}
```

- 每个 action 渲染为一个按钮
- 点击 → 调用 `module.call { id: "cloudfin.xxx", method: "dial", params: {} }`
- 加载中状态：按钮禁用 + loading spinner
- 错误：显示 snackbar / toast

**form card 渲染规则：**

```json
{
  "type": "form",
  "title": "配置",
  "fields": [
    { "key": "max_peers", "label": "最大节点数", "type": "number", "default": 50 },
    { "key": "enable_upnp", "label": "启用 UPnP", "type": "toggle", "default": true },
    { "key": "boot_nodes", "label": "引导节点", "type": "text", "multiline": true }
  ]
}
```

- `type: "number"` → `NumberField` / `Stepper`
- `type: "toggle"` → `Switch` / `Checkbox`
- `type: "text"` → `TextField`（multiline=true 时为 TextArea）
- 修改后 → 用户主动保存 → `config.set { key: "modules.xxx.max_peers", value: 100 }`

**模块健康状态徽章：**

| Health | 颜色 | 含义 |
|--------|------|------|
| `Healthy` | 绿色 | 正常运行 |
| `Degraded(reason)` | 黄色 | 可恢复异常 |
| `Unhealthy(reason)` | 红色 | 严重故障 |
| `Unknown` | 灰色 | 尚未检查 |

---

## 4. 核心页面技术实现

### 4.1 OS/架构检测

**UI 启动时自动检测（L0 §2.2 未安装状态）：**

**Kotlin 实现：**

```kotlin
object SystemInfo {
    val os: String = System.getProperty("os.name").lowercase()  // "linux" / "android" / "mac os x" / "windows"
    val arch: String = System.getProperty("os.arch")          // "amd64" / "aarch64" / "arm64"
    val version: String = System.getProperty("os.version")

    fun detectCloudfinArch(): String = when {
        os.contains("android") && arch == "arm64" -> "arm64-v8a"
        os.contains("linux") && arch == "amd64" -> "amd64"
        os.contains("linux") && (arch == "aarch64" || arch == "arm64") -> "arm64"
        os.contains("mac") && arch == "aarch64" -> "arm64"
        os.contains("windows") && arch == "amd64" -> "amd64"
        else -> "$arch"
    }

    fun detectOS(): CloudfinOS = when {
        os.contains("android") -> CloudfinOS.Android
        os.contains("linux") -> CloudfinOS.Linux
        os.contains("mac") -> CloudfinOS.MacOS
        os.contains("windows") -> CloudfinOS.Windows
        else -> CloudfinOS.Unknown
    }
}

enum class CloudfinOS { Android, Linux, MacOS, Windows, Unknown }
```

**Swift 实现：**

```swift
import Foundation
import System

struct SystemInfo {
    static var os: String {
        #if os(iOS)
        return "ios"
        #elseif os(macOS)
        return "macos"
        #elseif os(Linux)
        return "linux"
        #elseif os(Windows)
        return "windows"
        #else
        return "unknown"
        #endif
    }

    static var arch: String {
        let architecture = System.arch.rawValue
        switch architecture {
        case .x86_64: return "amd64"
        case .aarch64: return "arm64"
        default: return String(describing: architecture)
        }
    }

    static func cloudfinArch() -> String {
        if os == "ios" || os == "macos" {
            return arch == "aarch64" ? "arm64" : arch
        }
        if os == "linux" {
            return arch == "aarch64" ? "arm64" : arch
        }
        return arch
    }
}
```

**Core 安装包命名规范（L0 §7.4）：**

```
Cloudfin-Core-{系统}-{架构}-v{年}{月}{日}-{序号}.{格式}
```

| 平台 | 架构标识 | 下载格式 |
|------|---------|---------|
| Android | `arm64-v8a` | `.apk` |
| Linux | `amd64` / `arm64` | `.tar.gz` |
| macOS | `arm64` / `amd64` | `.tar.gz` |
| Windows | `amd64` | `.exe` |

### 4.2 自动化安装流程（GitHub / Gitee）

**未安装状态检测（L0 §2.2）：**

```
UI 启动
  → 尝试连接 ws://127.0.0.1:19001/ws
  → 连接失败（或超时 2s）
  → 判定 Core 未安装
  → 渲染"核心未安装"页面
```

**安装渠道选择：**

| 渠道 | 适用场景 | 仓库示例 |
|------|---------|---------|
| GitHub | 海外用户 / 开源用户 | `cloudfin/cloudfin-releases` |
| Gitee | 国内用户 / 快速下载 | `cloudfin/cloudfin-releases` (Gitee 镜像) |

**自动化安装流程（L0 §7.1）：**

```
用户点击"🚀 一键安装核心"
  → 显示渠道选择对话框（GitHub / Gitee）
  → 用户选择渠道
  → UI 获取系统信息（OS + 架构）
  → 拼接下载 URL：
      GitHub: https://github.com/{org}/cloudfin-releases/releases/latest/download/Cloudfin-Core-{OS}-{Arch}.tar.gz
      Gitee:  https://gitee.com/{org}/cloudfin-releases/releases/download/latest/Cloudfin-Core-{OS}-{Arch}.tar.gz
  → 下载文件（显示进度条）
  → 解析压缩包
  → 移动二进制到安装目录
  → 验证签名（可选）
  → 启动 Core 进程
  → 等待 WebSocket 就绪（超时 10s）
  → 连接成功 → 刷新页面进入已安装状态
```

**下载与安装实现（Kotlin）：**

```kotlin
suspend fun installCore(渠道: InstallChannel, 系统: CloudfinOS, 架构: String): Result<Unit> {
    val filename = "Cloudfin-Core-${OS}-${Arch}-v${DATE}.tar.gz"
    val url = when (渠道) {
        InstallChannel.GITHUB -> "https://github.com/cloudfin/releases/releases/latest/download/$filename"
        InstallChannel.GITEE -> "https://gitee.com/cloudfin/releases/releases/download/latest/$filename"
    }

    // 1. 下载
    val bytes = downloadWithProgress(url) // 流式下载，显示进度

    // 2. 验证（检查文件头 magic number）
    if (!verifyTarGz(bytes)) {
        return Result.failure(InstallError.InvalidPackage)
    }

    // 3. 解压到临时目录
    val tempDir = Files.createTempDirectory("cloudfin-install")
    extractTarGz(bytes, tempDir)

    // 4. 安装到目标目录（需要 root/管理员权限）
    val installDir = determineInstallDir() // Linux: /usr/local/bin 或 ~/.local/bin
    moveToInstallDir(tempDir, installDir)

    // 5. 写入 PID 文件路径（Linux/macOS）
    writeDaemonInfo(installDir)

    return Result.success(Unit)
}
```

**下载进度显示：**

```kotlin
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val percent: Int = ((bytesDownloaded.toDouble() / totalBytes) * 100).toInt()
)
```

### 4.3 权限处理（Linux / macOS root）

**安装时的权限请求（L0 §7.1）：**

> "安装过程需要管理员权限（如需密码会弹窗提示）"

**Linux：**

- 检测是否 root：`geteuid() == 0`
- 非 root 安装：提示输入 `sudo` 密码，调用 `pkexec`（PolicyKit）或 `sudo`
- 自安装（非系统目录）：写入 `~/.local/bin/` 不需要 root

**macOS：**

- 检测是否 root
- 非 root：调用 `osascript` 执行 `AuthorizationExecuteWithPrivileges` 或使用 `sudo`（需用户输入密码）
- 推荐使用 `helper tool` 机制（`SMJobBless`）实现无密码安装

**Kotlin 实现：**

```kotlin
suspend fun installWithElevatedPrivilege(
    packageBytes: ByteArray,
    installPath: Path
): Result<Process> {
    return when (Platform.os) {
        OS.LINUX -> {
            // Linux: 优先尝试写入用户目录（无需 root）
            if (canWriteToUserLocal()) {
                installToUserLocal(packageBytes, installPath)
            } else {
                // 需要 root，使用 pkexec 弹窗认证
                runWithPrivilege("pkexec", listOf("cp", "-r", "$tempDir/*", "$installPath"))
            }
        }
        OS.MACOS -> {
            if (ProcessHandle.current().uid() == 0) {
                installDirect(packageBytes, installPath)
            } else {
                // macOS: 使用 osascript 提权
                runWithPrivilege("osascript", listOf(
                    "-e", "do shell script \"cp -r $tempDir/* $installPath\" with administrator privileges"
                ))
            }
        }
        else -> installDirect(packageBytes, installPath)
    }
}
```

**Core 进程的启动与停止：**

```kotlin
// 启动 Core（守护进程模式）
fun startCoreDaemon(): Result<Process> {
    val process = ProcessBuilder("cloudfin-core", "run", "--daemon")
        .directory(installDir)
        .start()
    return if (process.isAlive) {
        // 写入 PID
        savePid(process.pid)
        Result.success(process)
    } else {
        Result.failure(CoreStartError)
    }
}

// 停止 Core
fun stopCoreDaemon(): Result<Unit> {
    val pid = loadPid() ?: return Result.failure(PidFileNotFound)
    ProcessBuilder("kill", "-INT", pid.toString()).start()
    waitForProcessExit(pid, timeoutSeconds = 30)
    return Result.success(Unit)
}
```

---

## 5. 平台差异说明

### 5.1 Android

| 项目 | 说明 |
|------|------|
| WebSocket | 使用 `OkHttp` 库，支持 pingInterval 自动心跳 |
| 后台保活 | 使用 `Foreground Service`，通知栏常驻图标 |
| 安装 | 下载 APK → PackageManager 安装，需用户确认"未知来源" |
| 数据目录 | `{InternalStorage}/Cloudfin/` |
| 权限 | `INTERNET`、`FOREGROUND_SERVICE`、`POST_NOTIFICATIONS` |

**Android 权限清单（AndroidManifest.xml）：**

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<!-- Android 13+ 通知权限 -->
```

**Android 后台服务配置：**

```xml
<service
    android:name=".CloudfinCoreService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

### 5.2 iOS

| 项目 | 说明 |
|------|------|
| WebSocket | 使用 `URLSession` 原生 WebSocket API（iOS 13+）|
| 后台模式 | Background Modes：Background fetch + Remote notifications |
| 安装 | App Store 审核，或 TestFlight / 企业分发 |
| 数据目录 | `Application Support/Cloudfin/` |
| 权限 | 无特殊权限，App Sandbox 内运行 |

**iOS Background Modes（Info.plist）：**

```xml
<key>UIBackgroundModes</key>
<array>
    <string>fetch</string>
    <string>remote-notification</string>
</array>
```

**iOS Keychain 存储敏感配置：**

```swift
import Security

class KeychainStorage {
    static func save(key: String, value: String) -> Bool {
        let data = Data(value.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecValueData as String: data
        ]
        SecItemDelete(query as CFDictionary)
        return SecItemAdd(query as CFDictionary, nil) == errSecSuccess
    }
}
```

### 5.3 Desktop

**Linux：**

| 项目 | 说明 |
|------|------|
| WebSocket | Qt WebSocket / `websocat` / 原生 |
| 安装 | `.tar.gz` 解压即用，或 `.deb` / `.AppImage` |
| 数据目录 | `~/.config/Cloudfin/` 或 `~/.local/share/Cloudfin/` |
| 启动方式 | Desktop Entry（`.desktop` 文件）支持开机自启 |
| root 权限 | 通常不需要（用户目录安装），除非系统级安装 |

**macOS：**

| 项目 | 说明 |
|------|------|
| WebSocket | `URLSession` WebSocket 或 Qt |
| 安装 | `.tar.gz` 解压，或 `.dmg` 挂载安装 |
| 数据目录 | `~/Library/Application Support/Cloudfin/` |
| 签名 | 需要 Apple 签名 + 公证（Notarization）才能运行 |
| 启动方式 | Login Items（系统偏好设置 → 用户 → 登录项）|

**Windows：**

| 项目 | 说明 |
|------|------|
| WebSocket | Qt WebSocket / WinRT WebSocket |
| 安装 | `.exe` Installer（NSIS / WiX）或 MSI |
| 数据目录 | `%APPDATA%\Cloudfin\` |
| root 权限 | 不需要（非系统级安装），UAC 仅在系统级安装时触发 |
| 开机自启 | 注册表 `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` |

**桌面通用架构建议：**

```
┌─────────────────────────────────────────────────┐
│                  Main Window                     │
│  ┌─────────┐  ┌──────────────────────────────┐  │
│  │ Sidebar │  │         Content Area         │  │
│  │ (Nav)   │  │                              │  │
│  │ 核心     │  │  (动态渲染模块页面)           │  │
│  │ 通信     │  │                              │  │
│  │ 加密     │  │                              │  │
│  │ 同步     │  │                              │  │
│  │ 设置     │  │                              │  │
│  └─────────┘  └──────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

---

## 附录 A：WebSocket 消息类型汇总

| type | 说明 | 方向 |
|------|------|------|
| `request` | 请求 | C→S |
| `response` | 成功响应 | S→C |
| `error` | 错误响应 | S→C |
| `event` | 服务端推送 | S→C |

## 附录 B：UI ↔ Core 交互时序图

```
UI                      Core                      用户
 │                       │                        │
 │──── connect() ───────►│                        │
 │◄─── status_snapshot ───│  (自动推送)             │
 │                       │                        │
 │──── core.info ───────►│                        │
 │◄─── response ─────────│                        │
 │                       │                        │
 │──── module.list ─────►│                        │
 │◄─── response ─────────│                        │
 │                       │                        │
 │                       │◄─── module_health ─────│ (模块状态变化)
 │◄─── event ────────────│                        │
 │                       │                        │
 │  [用户操作：点击模块]   │                        │
 │──── module.call ─────►│                        │
 │◄─── response ─────────│                        │
 │                       │                        │
 │  [用户操作：修改配置]   │                        │
 │──── config.set ──────►│                        │
 │◄─── config_changed ───│  (广播)                │
 │                       │                        │
 │  [网络断开]            │                        │
 │◄─── onClose ──────────│                        │
 │                       │                        │
 │  [指数退避重连]         │                        │
 │──── connect() ───────►│                        │
 │◄─── status_snapshot ───│  (重连成功)            │
```

## 附录 C：状态机汇总

### Core 连接状态（UI 侧）

```
Disconnected → Connecting → Connected → (Disconnected)
                                     ↗            ↘
                         指数退避重连                onClose/onError
```

### 模块生命周期（Core 侧，L1 §4.2）

```
Unloaded → Init → Start → Running → Stopping → Stopped → Unloaded
```

### 模块健康状态（L2 §3）

```
Healthy | Degraded(reason) | Unhealthy(reason) | Unknown
```

## 附录 D：UI 状态数据结构

```typescript
// TypeScript 类型定义（供各平台参考）
type WsMessageType = 'request' | 'response' | 'error' | 'event';

interface WsMessage {
  type?: WsMessageType;
  action?: string;
  event?: string;
  request_id: string;
  data?: unknown;
  error?: { code: string; message: string; details?: unknown };
}

interface CoreInfo {
  name: string;
  version: string;
  api_version: string;
}

interface ModuleInfo {
  id: string;
  name: string;
  version: string;
  health: ModuleHealth;
}

type ModuleHealth = 'Healthy' | { Degraded: string } | { Unhealthy: string } | 'Unknown';

interface StatusSnapshot {
  name: string;
  version: string;
  api_version: string;
  modules: ModuleInfo[];
}

interface UIState {
  core: {
    connected: boolean;
    info: CoreInfo | null;
  };
  modules: Map<string, ModuleInfo>;
  theme: 'system' | 'light' | 'dark';
  sidebar: {
    selectedSection: '核心' | '通信' | '加密' | '同步' | '设置';
    selectedModule: string | null;
  };
}
```

---

*本文档为 Cloudfin UI 技术路线级文档（L3），为各平台 UI 实现提供技术指导。所有实现级细节参见 L4 实现文档（如 SPEC-Impl-UI.md）。*
