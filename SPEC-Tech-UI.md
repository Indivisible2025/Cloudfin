# Cloudfin UI 技术文档

**版本：** v1.0
**日期：** 2026-04-05
**状态：** Draft（待评审）
**级别：** L1（路线级）

---

## 1. UI 框架选型

### 1.1 跨平台策略对比

| 策略 | 代表技术 | 优点 | 缺点 | 适用场景 |
|------|---------|------|------|---------|
| **平台原生** | Kotlin+Compose / Swift+SwiftUI / C#+WinUI3 | 性能最优、体验最原生、系统契合度高 | 多平台需多套代码、维护成本高 | 追求原生体验的移动端/桌面端 |
| **跨平台框架** | Flutter / React Native | 一套代码多平台、热更新、生态丰富 | 性能损耗、UI 一致性随平台风格差异 | 快速迭代、UI 复杂度中等 |
| **Web 封装** | Tauri (Rust+Web) / Electron | 开发效率高、UI 灵活 | 包体积大、内存占用高 | 桌面端为主、不在意包体积 |

**选型结论：平台原生优先**

- Android / iOS / Desktop 三个平台各自使用原生技术栈
- UI 专注用户体验，Core 专注跨平台逻辑
- 通过统一的 WebSocket 协议与 Core 通信，协议层与平台无关

### 1.2 各平台技术选型

#### Android

| 层次 | 技术选型 | 说明 |
|------|---------|------|
| 语言 | Kotlin 1.9+ | 现代 Android 一线语言 |
| UI 框架 | Jetpack Compose | Google 主推、声明式、现代化 |
| 状态管理 | StateFlow + ViewModel | 响应式、与 Compose 深度集成 |
| WebSocket | OkHttp 4.x | 稳定、协程支持、Kotlin 友好 |
| 依赖注入 | Hilt | Google 官方、与 Compose 契合 |
| 网络序列化 | Kotlinx Serialization | JSON 解析、高性能 |

#### iOS

| 层次 | 技术选型 | 说明 |
|------|---------|------|
| 语言 | Swift 5.9+ | 现代 iOS 开发语言 |
| UI 框架 | SwiftUI | Apple 主推、声明式、现代化 |
| 状态管理 | @Observable (iOS 17+) / Combine | Swift 原生响应式 |
| WebSocket | URLSession WebSocket | 系统内置、稳定 |
| 依赖注入 | SwiftUI Environment | 原生 DI 方式 |
| 网络序列化 | Codable | Swift 原生序列化 |

#### Desktop (Windows / macOS / Linux)

| 层次 | 技术选型 | 说明 |
|------|---------|------|
| 框架 | Tauri 2.x | Rust 后端 + Web 前端、包体积小 |
| 前端 UI | React 18 + TypeScript | 成熟生态、组件丰富 |
| 状态管理 | Zustand / Jotai | 轻量、TypeScript 友好 |
| 桌面运行时 | WebView2 (Win) / WKWebView (macOS/Linux) | 系统原生渲染引擎 |
| 构建工具 | Vite | 快速热更新、开发体验好 |

### 1.3 选型理由总结

```
┌─────────────────────────────────────────────────────┐
│                    UI Layer                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────┐  │
│  │ Android   │  │   iOS    │  │  Desktop (Tauri) │  │
│  │ Compose   │  │ SwiftUI  │  │  React + TS       │  │
│  │ Kotlin    │  │  Swift   │  │  Rust backend     │  │
│  └─────┬─────┘  └────┬─────┘  └────────┬─────────┘  │
│        │              │                 │            │
│        └──────────────┼─────────────────┘            │
│                       │ WebSocket (统一协议)          │
│        ┌──────────────▼─────────────────┐            │
│        │          Core (Rust)           │            │
│        │   连接管理 / 路由 / 模块调度    │            │
│        └────────────────────────────────┘            │
└─────────────────────────────────────────────────────┘
```

- **移动端**：原生性能 + 原生体验，WebSocket 通信保持协议统一
- **桌面端**：Tauri 平衡性能与开发效率，Rust 后端与 Core 技术栈一致
- **协议统一**：所有平台 WebSocket 消息格式一致，Core 无需区分 UI 来源

---

## 2. UI ↔ Core 通信

### 2.1 WebSocket 客户端实现

#### Android (Kotlin + OkHttp)

```kotlin
// CloudfinWebSocket.kt
class CloudfinWebSocket(
    private val url: String = "ws://127.0.0.1:18432"
) {
    private var client: OkHttpClient? = null
    private var ws: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 回调
    var onMessage: ((CloudfinMessage) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    fun connect() {
        client = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(url)
            .build()

        ws = client?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onConnected?.invoke()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = Json.decodeFromString<CloudfinMessage>(text)
                onMessage?.invoke(msg)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch { handleReconnect() }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onDisconnected?.invoke()
            }
        })
    }

    fun send(message: CloudfinMessage) {
        val json = Json.encodeToString(CloudfinMessage.serializer(), message)
        ws?.send(json)
    }

    fun disconnect() {
        ws?.close(1000, "Client disconnect")
        client?.dispatcher?.executorService?.shutdown()
    }
}
```

#### iOS (Swift + URLSession)

```swift
// CloudfinWebSocket.swift
import Foundation
import Combine

class CloudfinWebSocket: NSObject, URLSessionWebSocketDelegate {
    private var session: URLSession!
    private var webSocketTask: URLSessionWebSocketTask?
    private let url: URL

    var onMessage: ((CloudfinMessage) -> Void)?
    var onConnected: (() -> Void)?
    var onDisconnected: (() -> Void)?

    init(url: URL = URL(string: "ws://127.0.0.1:18432")!) {
        self.url = url
        super.init()
        let config = URLSessionConfiguration.default
        config.waitsForConnectivity = true
        session = URLSession(configuration: config, delegate: self, delegateQueue: .main)
    }

    func connect() {
        webSocketTask = session.webSocketTask(with: url)
        webSocketTask?.resume()
        receiveMessage()
        onConnected?()
    }

    private func receiveMessage() {
        webSocketTask?.receive { [weak self] result in
            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    if let data = text.data(using: .utf8),
                       let msg = try? JSONDecoder().decode(CloudfinMessage.self, from: data) {
                        self?.onMessage?(msg)
                    }
                case .data(let data):
                    if let msg = try? JSONDecoder().decode(CloudfinMessage.self, from: data) {
                        self?.onMessage?(msg)
                    }
                @unknown default:
                    break
                }
                self?.receiveMessage()
            case .failure:
                self?.handleReconnect()
            }
        }
    }

    func send(_ message: CloudfinMessage) {
        guard let data = try? JSONEncoder().encode(message),
              let string = String(data: data, encoding: .utf8) else { return }
        webSocketTask?.send(.string(string)) { error in
            if let error = error {
                print("Send error: \(error)")
            }
        }
    }

    func disconnect() {
        webSocketTask?.cancel(with: .goingAway, reason: nil)
    }

    // MARK: - URLSessionWebSocketDelegate
    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didOpenWithProtocol protocol: String?) {
        // Connected
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didCloseWith closeCode: URLSessionWebSocketTask.CloseCode, reason: Data?) {
        onDisconnected?()
    }
}
```

#### Desktop (TypeScript + WebSocket)

```typescript
// cloudfin-ws.ts
type MessageHandler = (msg: CloudfinMessage) => void;
type VoidHandler = () => void;

class CloudfinWebSocket {
  private ws: WebSocket | null = null;
  private url: string;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 10;
  private reconnectDelay = 1000;

  onMessage: MessageHandler | null = null;
  onConnected: VoidHandler | null = null;
  onDisconnected: VoidHandler | null = null;

  constructor(url = 'ws://127.0.0.1:18432') {
    this.url = url;
  }

  connect(): void {
    this.ws = new WebSocket(this.url);

    this.ws.onopen = () => {
      this.reconnectAttempts = 0;
      this.onConnected?.();
    };

    this.ws.onmessage = (event) => {
      try {
        const msg: CloudfinMessage = JSON.parse(event.data);
        this.onMessage?.(msg);
      } catch (e) {
        console.error('Failed to parse message:', e);
      }
    };

    this.ws.onclose = () => {
      this.onDisconnected?.();
      this.handleReconnect();
    };

    this.ws.onerror = (error) => {
      console.error('WebSocket error:', error);
    };
  }

  send(message: CloudfinMessage): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
    }
  }

  disconnect(): void {
    this.ws?.close(1000, 'Client disconnect');
    this.ws = null;
  }

  private handleReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('Max reconnect attempts reached');
      return;
    }
    const delay = Math.min(
      this.reconnectDelay * Math.pow(2, this.reconnectAttempts),
      30000
    );
    this.reconnectAttempts++;
    setTimeout(() => this.connect(), delay);
  }
}
```

### 2.2 请求响应模式

#### 消息格式定义

```typescript
// 统一消息结构
interface CloudfinMessage {
  type: 'request' | 'response' | 'push' | 'error';
  action?: string;          // 请求的动作名，如 "module.list"
  request_id?: string;      // 请求唯一 ID，用于匹配响应
  data?: unknown;           // 载荷
  error?: string | null;    // 错误信息
}

// 请求示例
const request: CloudfinMessage = {
  type: 'request',
  action: 'module.list',
  request_id: 'uuid-v4-xxx',
  data: {}
};

// 响应示例
const response: CloudfinMessage = {
  type: 'response',
  request_id: 'uuid-v4-xxx',
  data: { modules: [...] },
  error: null
};

// 推送示例（服务端主动通知）
const push: CloudfinMessage = {
  type: 'push',
  action: 'status_update',
  data: { core_status: 'running', module_id: 'p2p', status: 'connected' }
};
```

#### 请求管理器实现 (Android 示例)

```kotlin
// RequestManager.kt
class RequestManager(private val ws: CloudfinWebSocket) {
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<CloudfinMessage>>()

    suspend fun <T> call(action: String, data: Any = {}): Result<T> {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<CloudfinMessage>()

        pendingRequests[requestId] = deferred

        ws.send(CloudfinMessage(
            type = "request",
            action = action,
            request_id = requestId,
            data = data
        ))

        return try {
            val response = withTimeout(5000) { deferred.await() }
            if (response.error != null) {
                Result.failure(Exception(response.error))
            } else {
                @Suppress("UNCHECKED_CAST")
                Result.success(response.data as T)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            pendingRequests.remove(requestId)
        }
    }

    fun handleResponse(response: CloudfinMessage) {
        response.request_id?.let { id ->
            pendingRequests[id]?.complete(response)
        }
    }
}
```

### 2.3 心跳与重连

#### 心跳机制

| 参数 | 值 | 说明 |
|------|-----|------|
| 心跳间隔 | 30 秒 | 客户端每 30 秒发送 ping |
| 超时判定 | 60 秒 | 60 秒无响应判定为断开 |
| 重连最大次数 | 10 次 | 超过后停止自动重连 |
| 初始重连延迟 | 1 秒 | 指数退避，最大 30 秒 |
| 请求超时 | 5 秒 | 单次请求等待响应的超时 |

#### 重连流程图

```
连接断开
    │
    ▼
等待 1s ──→ 尝试连接
    │           │
    │      连接成功？
    │      ┌────┴────┐
    │      ↓         ↓
    │   是         否
    │    │         │
    │    │    重试次数 ≥ 10？
    │    │    ┌────┴────┐
    │    │    ↓         ↓
    │    │ 是         否
    │    │  │         │
    │    │ 停止    延迟 = min(1s * 2^n, 30s)
    │   重连    等待后重试
    │    │
    ▼    ▼
重连成功 → 恢复所有挂起的请求
```

### 2.4 状态管理

#### Core 状态树

```typescript
// core-state.ts - Core 推送给 UI 的完整状态
interface CoreState {
  core: {
    status: 'running' | 'stopped' | 'error';
    version: string;
    uptime: number; // 秒
    modules: Record<string, ModuleState>;
  };
  network: {
    peer_id: string | null;
    connected_peers: number;
    latency_ms: number;
  };
  settings: {
    theme: 'system' | 'light' | 'dark';
    language: string;
    auto_start: boolean;
  };
}

interface ModuleState {
  id: string;
  name: string;
  status: 'loaded' | 'running' | 'stopped' | 'error';
  health: 'healthy' | 'degraded' | 'unhealthy';
  manifest: ModuleManifest;
}
```

#### Android 状态流 (StateFlow)

```kotlin
// CoreViewModel.kt
class CoreViewModel(
    private val ws: CloudfinWebSocket,
    private val requestManager: RequestManager
) : ViewModel() {

    private val _coreState = MutableStateFlow<CoreState?>(null)
    val coreState: StateFlow<CoreState?> = _coreState.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    init {
        ws.onMessage = { msg -> handleMessage(msg) }
        ws.onConnected = { _connectionState.value = ConnectionState.CONNECTED }
        ws.onDisconnected = { _connectionState.value = ConnectionState.DISCONNECTED }
    }

    private fun handleMessage(msg: CloudfinMessage) {
        when (msg.type) {
            "push" -> {
                if (msg.action == "status_update") {
                    // 合并状态更新
                    _coreState.value = _coreState.value?.merge(msg.data) ?: msg.data as CoreState
                }
            }
            "response" -> requestManager.handleResponse(msg)
        }
    }

    fun connect() = ws.connect()
}

enum class ConnectionState {
    CONNECTING, CONNECTED, DISCONNECTED, RECONNECTING
}
```

#### iOS 状态流 (@Observable)

```swift
// CoreViewModel.swift
import Foundation
import Combine

@Observable
class CoreViewModel {
    var coreState: CoreState?
    var connectionState: ConnectionState = .disconnected

    private var ws: CloudfinWebSocket?
    private var requestManager: RequestManager?

    func connect() {
        ws = CloudfinWebSocket()
        requestManager = RequestManager(ws: ws!)

        ws?.onConnected = { [weak self] in
            self?.connectionState = .connected
        }

        ws?.onDisconnected = { [weak self] in
            self?.connectionState = .disconnected
        }

        ws?.onMessage = { [weak self] msg in
            self?.handleMessage(msg)
        }

        ws?.connect()
    }

    private func handleMessage(_ msg: CloudfinMessage) {
        switch msg.type {
        case "push":
            if msg.action == "status_update", let data = msg.data {
                coreState = data as? CoreState
            }
        case "response":
            requestManager?.handleResponse(msg)
        default:
            break
        }
    }
}

enum ConnectionState {
    case connecting, connected, disconnected, reconnecting
}
```

---

## 3. 主题系统

### 3.1 三种模式

| 模式 | 值 | 说明 |
|------|-----|------|
| 系统默认 | `system` | 跟随操作系统设置 |
| 亮色 | `light` | 强制使用亮色主题 |
| 暗色 | `dark` | 强制使用暗色主题 |

### 3.2 系统默认检测

#### Android

```kotlin
// ThemeManager.kt
@Composable
fun rememberThemeMode(): ThemeMode {
    val systemTheme = LocalPlatformConfiguration.current.platformStyle.platformTheme

    // 从本地存储读取用户设置，默认为 system
    val prefs = LocalContext.current.getSharedPreferences("cloudfin", MODE_PRIVATE)
    val savedTheme = prefs.getString("theme_mode", "system") ?: "system"

    return when (savedTheme) {
        "light" -> ThemeMode.LIGHT
        "dark" -> ThemeMode.DARK
        else -> if (systemTheme == PlatformTheme.DARK) ThemeMode.DARK else ThemeMode.LIGHT
    }
}

// Jetpack Compose 中的应用
@Composable
fun CloudfinApp() {
    val themeMode = rememberThemeMode()

    MaterialTheme(
        colorScheme = when (themeMode) {
            ThemeMode.LIGHT -> LightColorScheme
            ThemeMode.DARK -> DarkColorScheme
            ThemeMode.SYSTEM -> if (isSystemDark()) DarkColorScheme else LightColorScheme
        }
    ) {
        // App content
    }
}
```

#### iOS

```swift
// ThemeManager.swift
enum ThemeMode: String {
    case system, light, dark
}

class ThemeManager: ObservableObject {
    @Published var themeMode: ThemeMode = .system

    init() {
        themeMode = ThemeManager.loadSavedTheme()
    }

    private static func loadSavedTheme() -> ThemeMode {
        let saved = UserDefaults.standard.string(forKey: "cloudfin_theme_mode") ?? "system"
        return ThemeMode(rawValue: saved) ?? .system
    }

    var colorScheme: ColorScheme? {
        switch themeMode {
        case .system:
            return nil // SwiftUI 会自动跟随系统
        case .light:
            return .light
        case .dark:
            return .dark
        }
    }
}

// SwiftUI 应用
struct CloudfinApp: App {
    @StateObject private var themeManager = ThemeManager()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(themeManager.colorScheme)
        }
    }
}
```

#### Desktop (React)

```tsx
// ThemeContext.tsx
import React, { createContext, useContext, useState, useEffect } from 'react';
import { useSystemTheme } from './hooks/useSystemTheme';

type ThemeMode = 'system' | 'light' | 'dark';

interface ThemeContextValue {
  mode: ThemeMode;
  setMode: (mode: ThemeMode) => void;
  resolvedTheme: 'light' | 'dark';
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const systemTheme = useSystemTheme();
  const [mode, setMode] = useState<ThemeMode>(() => {
    return (localStorage.getItem('cloudfin_theme_mode') as ThemeMode) || 'system';
  });

  const resolvedTheme = mode === 'system'
    ? systemTheme
    : mode;

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', resolvedTheme);
  }, [resolvedTheme]);

  return (
    <ThemeContext.Provider value={{ mode, setMode, resolvedTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

// useSystemTheme hook
function useSystemTheme(): 'light' | 'dark' {
  const [theme, setTheme] = useState<'light' | 'dark'>(() => {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  });

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = (e: MediaQueryListEvent) => {
      setTheme(e.matches ? 'dark' : 'light');
    };
    mediaQuery.addEventListener('change', handler);
    return () => mediaQuery.removeEventListener('change', handler);
  }, []);

  return theme;
}
```

### 3.3 主题持久化

| 平台 | 存储方式 | Key | 默认值 |
|------|---------|-----|--------|
| Android | SharedPreferences | `cloudfin_theme_mode` | `"system"` |
| iOS | UserDefaults | `cloudfin_theme_mode` | `"system"` |
| Desktop | localStorage | `cloudfin_theme_mode` | `"system"` |

### 3.4 首次启动行为

首次启动时，主题模式默认跟随系统设置：

1. 应用读取系统当前是亮色还是暗色
2. 将 `"system"` 作为默认值存入本地存储
3. UI 根据检测到的系统主题渲染对应颜色
4. 用户可以在设置中手动覆盖

---

## 4. 模块页面动态渲染

### 4.1 Manifest UI 解析

模块 Manifest 中的 `ui` 字段定义该模块在 UI 中的展示形式：

```json
{
  "id": "cloudfin.p2p",
  "name": "P2P 通信模块",
  "version": "0.1.0",
  "platform": ["android", "ios", "linux", "windows", "macos"],
  "permissions": ["Network"],
  "dependencies": [],
  "ui": {
    "cards": [
      {
        "id": "info",
        "type": "info",
        "title": "节点信息",
        "fields": [
          { "key": "peer_id", "label": "Peer ID", "type": "text" },
          { "key": "connected_peers", "label": "已连接节点", "type": "number" }
        ]
      },
      {
        "id": "actions",
        "type": "actions",
        "title": "操作",
        "buttons": [
          { "action": "p2p.connect", "label": "连接新节点", "icon": "link" },
          { "action": "p2p.disconnect_all", "label": "断开所有", "icon": "unlink", "destructive": true }
        ]
      },
      {
        "id": "settings",
        "type": "settings",
        "title": "高级设置",
        "fields": [
          { "key": "nat_traversal", "label": "NAT 穿透", "type": "toggle", "default": true },
          { "key": "max_peers", "label": "最大连接数", "type": "number", "default": 10, "min": 1, "max": 100 },
          { "key": "relay_enabled", "label": "启用中继", "type": "toggle", "default": false }
        ]
      }
    ]
  }
}
```

### 4.2 卡片渲染规则

每张卡片根据 `type` 渲染：

| type | 说明 | 容器样式 |
|------|------|---------|
| `info` | 信息展示，只读 | 卡片 + 字段列表 |
| `actions` | 操作按钮，触发 Core 调用 | 卡片 + 按钮组 |
| `settings` | 可编辑设置项 | 卡片 + 表单控件 |

#### 动态卡片渲染组件 (Android Compose)

```kotlin
// ModulePage.kt
@Composable
fun ModulePage(
    moduleState: ModuleState,
    onAction: (String, Map<String, Any>) -> Unit
) {
    val manifest = moduleState.manifest
    val uiConfig = manifest.ui ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = manifest.name,
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        uiConfig.cards.forEach { card ->
            when (card.type) {
                "info" -> InfoCard(card, moduleState)
                "actions" -> ActionsCard(card, onAction)
                "settings" -> SettingsCard(card, moduleState, onAction)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun InfoCard(card: UICard, moduleState: ModuleState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(card.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            card.fields.forEach { field ->
                val value = moduleState.data?.get(field.key)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(field.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        value?.toString() ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ActionsCard(card: UICard, onAction: (String, Map<String, Any>) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(card.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                card.buttons.forEach { button ->
                    Button(
                        onClick = { onAction(button.action, {}) },
                        colors = if (button.destructive == true) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        Text(button.label)
                    }
                }
            }
        }
    }
}
```

### 4.3 类型映射表

`settings` 卡片中的 `fields` 根据 `type` 映射到对应的 UI 控件：

| type 值 | Android (Compose) | iOS (SwiftUI) | Desktop (React) | 说明 |
|---------|-------------------|---------------|-----------------|------|
| `text` | `OutlinedTextField` | `TextField` | `<input type="text">` | 单行文本输入 |
| `number` | `OutlinedTextField` + `KeyboardType.Number` | `TextField` + `.keyboardType(.numberPad)` | `<input type="number">` | 数字输入 |
| `toggle` | `Switch` | `Toggle` | `<input type="checkbox">` | 开关 |
| `select` | `var expanded by remember { mutableStateOf(false) }` + `DropdownMenu` | `Picker` | `<select>` | 下拉选择 |
| `slider` | `Slider` | `Slider` | `<input type="range">` | 范围滑块 |
| `password` | `OutlinedTextField` + `VisualTransformation` | `SecureField` | `<input type="password">` | 密码输入 |
| `textarea` | `OutlinedTextField` + `maxLines = 5` | `TextEditor` | `<textarea>` | 多行文本 |
| `datetime` | `DatePicker` / `TimePicker` | `DatePicker` | `<input type="datetime-local">` | 日期时间 |

---

## 5. 安装引导页面

### 5.1 系统检测

#### Android

```kotlin
object SystemDetector {
    fun getOS(): String = "android"

    fun getVersion(): String {
        return Build.VERSION.RELEASE
    }

    fun getArchitecture(): String {
        return Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    }

    fun getDownloadUrl(): Pair<String, String> {
        val arch = getArchitecture()
        val base = "https://github.com/cloudfin/cloudfin/releases"
        val filename = when (arch) {
            "arm64-v8a" -> "cloudfin-android-arm64-v8a.apk"
            "armeabi-v7a" -> "cloudfin-android-armeabi-v7a.apk"
            "x86_64" -> "cloudfin-android-x86_64.apk"
            else -> "cloudfin-android-arm64-v8a.apk"
        }
        return "$base/download/latest/$filename" to "$base/download/latest/$filename"
    }
}
```

#### iOS

```swift
struct SystemDetector {
    static var os: String { "ios" }

    static var version: String {
        UIDevice.current.systemVersion
    }

    static var deviceModel: String {
        var systemInfo = utsname()
        uname(&systemInfo)
        let machineMirror = Mirror(reflecting: systemInfo.machine)
        let identifier = machineMirror.children.reduce("") { identifier, element in
            guard let value = element.value as? Int8, value != 0 else { return identifier }
            return identifier + String(UnicodeScalar(UInt8(value)))
        }
        return identifier
    }

    // iOS App 通过 App Store 或 TestFlight 分发，此处返回 App Store 链接
    static var downloadUrl: URL? {
        URL(string: "https://apps.apple.com/app/cloudfin/id123456789")
    }
}
```

#### Desktop

```typescript
// system-detector.ts
interface SystemInfo {
  os: 'windows' | 'linux' | 'macos' | 'android' | 'ios';
  arch: 'amd64' | 'arm64' | 'armv7' | 'unknown';
  osVersion?: string;
}

function detectSystem(): SystemInfo {
  const userAgent = navigator.userAgent;
  const platform = navigator.platform.toLowerCase();

  // 浏览器环境检测（Desktop）
  if (platform.includes('win') || userAgent.includes('Windows')) {
    const arch = userAgent.includes('Win64') ? 'amd64' : 'x86';
    return { os: 'windows', arch };
  }

  if (platform.includes('mac') || userAgent.includes('Mac')) {
    const arch = userAgent.includes('ARM64') ? 'arm64' : 'amd64';
    return { os: 'macos', arch };
  }

  if (platform.includes('linux') || userAgent.includes('Linux')) {
    // 检测架构
    let arch = 'amd64';
    if (userAgent.includes('x86_64') || userAgent.includes('amd64')) arch = 'amd64';
    else if (userAgent.includes('aarch64') || userAgent.includes('arm64')) arch = 'arm64';
    else if (userAgent.includes('arm')) arch = 'armv7';
    return { os: 'linux', arch };
  }

  // 移动端
  if (userAgent.includes('Android')) {
    return { os: 'android', arch: detectAndroidArch() };
  }

  if (userAgent.includes('iPhone') || userAgent.includes('iPad')) {
    return { os: 'ios', arch: 'arm64' };
  }

  return { os: 'unknown', arch: 'unknown' };
}

function detectAndroidArch(): string {
  // Android WebView 可以通过 navigator.userAgent 获取架构信息
  const ua = navigator.userAgent;
  if (ua.includes('arm64')) return 'arm64';
  if (ua.includes('armeabi')) return 'armv7';
  if (ua.includes('x86_64')) return 'amd64';
  return 'arm64'; // 默认
}
```

### 5.2 架构检测

| 平台 | 架构检测方式 | 支持的架构 |
|------|------------|-----------|
| Android | `Build.SUPPORTED_ABIS` | `arm64-v8a`, `armeabi-v7a`, `x86_64` |
| iOS | `uname()` / 设备型号映射 | `arm64` |
| Windows | `navigator.userAgent` + 环境 | `amd64`, `x86` |
| macOS | `uname()` + Apple Silicon 检测 | `amd64` (Intel), `arm64` (Apple Silicon) |
| Linux | `uname -m` | `amd64`, `arm64`, `armv7` |

### 5.3 下载与安装流程

#### Desktop 下载流程

```tsx
// InstallGuide.tsx
interface InstallGuideProps {
  systemInfo: SystemInfo;
}

function InstallGuide({ systemInfo }: InstallGuideProps) {
  const [downloading, setDownloading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const downloadUrls = {
    github: `https://github.com/cloudfin/cloudfin/releases/latest/cloudfin-${systemInfo.os}-${systemInfo.arch}.${systemInfo.os === 'windows' ? 'exe' : 'tar.gz'}`,
    gitee: `https://gitee.com/cloudfin/cloudfin/releases/latest/cloudfin-${systemInfo.os}-${systemInfo.arch}.${systemInfo.os === 'windows' ? 'exe' : 'tar.gz'}`
  };

  const handleDownload = async (source: 'github' | 'gitee') => {
    setDownloading(true);
    setError(null);

    try {
      const url = downloadUrls[source];
      // 使用 fetch + ReadableStream 显示下载进度
      const response = await fetch(url);
      const reader = response.body?.getReader();
      const contentLength = parseInt(response.headers.get('Content-Length') || '0');

      // 下载并保存文件
      // Desktop: 使用 Tauri 的 fs API 或 electron 的 dialog 保存
      const blob = await response.blob();
      downloadBlob(blob, `cloudfin-${systemInfo.os}-${systemInfo.arch}`);

    } catch (e) {
      setError(`下载失败: ${e.message}`);
    } finally {
      setDownloading(false);
    }
  };

  const handleInstall = async () => {
    // 调用 Tauri 命令执行安装
    // Linux/macOS: 执行安装脚本（需要 sudo）
    // Windows: 执行 MSI/EXE 安装包
    await invoke('install_cloudfin', { requiresRoot: systemInfo.os !== 'windows' });
  };

  return (
    <div className="install-guide">
      <h2>安装 Cloudfin</h2>
      <p>检测到您的系统: {systemInfo.os} ({systemInfo.arch})</p>

      <div className="download-section">
        <h3>选择下载源</h3>
        <div className="download-buttons">
          <button
            onClick={() => handleDownload('github')}
            disabled={downloading}
            className="btn-primary"
          >
            GitHub 下载
          </button>
          <button
            onClick={() => handleDownload('gitee')}
            disabled={downloading}
            className="btn-secondary"
          >
            Gitee 下载
          </button>
        </div>
      </div>

      {downloading && (
        <div className="progress">
          <div className="progress-bar" style={{ width: `${progress}%` }} />
          <span>下载中... {progress}%</span>
        </div>
      )}

      {error && <div className="error">{error}</div>}

      <button
        onClick={handleInstall}
        disabled={!isDownloadComplete}
        className="btn-install"
      >
        安装
      </button>
    </div>
  );
}
```

#### Android APK 安装流程

```kotlin
// Android: 调用系统安装意图
fun installApk(context: Context, apkFile: File) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // Android 8.0+ 需要未知来源权限
        if (!context.packageManager.canRequestPackageInstalls()) {
            // 引导用户开启未知来源权限
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            intent.data = Uri.parse("package:${context.packageName}")
            (context as Activity).startActivityForResult(intent, REQUEST_INSTALL_PERMISSION)
            return
        }
    }

    // 执行安装
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}
```

### 5.4 权限处理

#### Linux/macOS Root 权限

桌面端安装时，如果需要写入系统目录（如 `/usr/local/bin`），需要提升权限：

```rust
// Tauri: install command
#[tauri::command]
async fn install_cloudfin(requires_root: bool) -> Result<String, String> {
    if requires_root {
        // 检测是否已有 root 权限
        if !is_root() {
            // 提示用户输入密码，使用 pkexec 或 sudo
            let output = Command::new("pkexec")
                .arg("sh")
                .arg("-c")
                .arg("cp cloudfin /usr/local/bin/ && chmod +x /usr/local/bin/cloudfin")
                .output()
                .map_err(|e| e.to_string())?;

            if !output.status.success() {
                return Err("安装失败：权限不足".to_string());
            }
            return Ok("安装成功".to_string());
        }
    }

    // 直接安装到用户目录（不需要 root）
    let home = std::env::var("HOME").map_err(|_| "无法获取 home 目录")?;
    let dest = PathBuf::from(home).join(".local/bin/cloudfin");
    std::fs::copy("cloudfin", &dest).map_err(|e| e.to_string())?;
    Ok(format!("安装到 {:?}", dest))
}
```

#### Windows UAC

```rust
// Tauri: Windows 提权安装
#[tauri::command]
async fn install_cloudfin_windows() -> Result<String, String> {
    // Windows 下使用 elevated COM 或 PowerShell Start-Process -Verb RunAs
    let output = Command::new("powershell")
        .args([
            "-Command",
            "Start-Process -FilePath 'cloudfin-setup.exe' -ArgumentList '/S' -Verb RunAs -Wait"
        ])
        .output()
        .map_err(|e| e.to_string())?;

    if output.status.success() {
        Ok("安装成功".to_string())
    } else {
        Err("安装失败或用户取消".to_string())
    }
}
```

---

## 6. 平台差异说明

### 6.1 Android

| 方面 | 技术方案 | 说明 |
|------|--------|------|
| UI 框架 | Jetpack Compose | 声明式、现代、性能好 |
| 状态管理 | StateFlow + ViewModel | 与 Compose 深度集成 |
| WebSocket | OkHttp 4.x | 稳定、协程支持 |
| 序列化 | Kotlinx Serialization | 性能高、Kotlin 友好 |
| 依赖注入 | Hilt | Google 官方 DI |
| 本地存储 | DataStore Preferences | 主题等轻量配置 |
| 进程间通信 | AIDL（与 Core 分离时）| 本地绑定服务 |

**Android 特有注意事项：**

1. **后台保活**：Core 作为独立进程（`android:process=":core"`），UI 通过 Bound Service 通信
2. **通知**：Core 状态推送到前台通知，用户可快速查看
3. **省电**：避开厂商白名单，提示用户将 Cloudfin 加入电池优化豁免名单
4. **权限请求**：网络权限在 Manifest 中声明，运行时请求 `REQUEST_INSTALL_PACKAGES`（安装 APK）

### 6.2 iOS

| 方面 | 技术方案 | 说明 |
|------|--------|------|
| UI 框架 | SwiftUI | Apple 主推声明式 UI |
| 状态管理 | @Observable (iOS 17+) | Swift 原生响应式 |
| WebSocket | URLSession WebSocket | 系统内置，无需第三方 |
| 序列化 | Codable | Swift 原生序列化 |
| 本地存储 | UserDefaults | 主题等轻量配置 |
| Core 集成 | Embedded Framework | Core 编译为.framework 嵌入 App |

**iOS 特有注意事项：**

1. **后台模式**：使用 Background Modes（`background fetch` + `remote-notification`）保持 Core 连接
2. **沙盒限制**：Core 数据存储在 App Group 共享容器中
3. **签名**：Core 库需要与 App 使用相同开发者证书签名
4. **App Store**：分发走 App Store，Core 作为 Embedded Framework 不需要单独审核

### 6.3 Desktop (Tauri)

| 方面 | 技术方案 | 说明 |
|------|--------|------|
| 框架 | Tauri 2.x | Rust 后端 + Web 前端 |
| 前端 UI | React 18 + TypeScript | 成熟生态 |
| 状态管理 | Zustand | 轻量、TypeScript 友好 |
| 样式 | Tailwind CSS | 快速开发 |
| 构建 | Vite | 快速热更新 |
| 桌面集成 | Tauri API | 系统托盘、通知、窗口管理 |

**Tauri 特有架构：**

```
┌─────────────────────────────────────────────┐
│                  Tauri App                   │
│  ┌──────────────────────────────────────┐   │
│  │         WebView (WebView2/WKWebView)   │   │
│  │  ┌─────────────────────────────────┐  │   │
│  │  │      React Frontend (UI)         │  │   │
│  │  └──────────────┬──────────────────┘  │   │
│  │                 │ IPC (invoke)       │   │
│  │  ┌──────────────▼──────────────────┐  │   │
│  │  │      Tauri Rust Backend          │  │   │
│  │  │  ┌─────────────┬──────────────┐  │  │   │
│  │  │  │ WebSocket   │  File System │  │  │   │
│  │  │  │   Client    │    / Shell   │  │  │   │
│  │  │  └──────┬──────┴──────────────┘  │  │   │
│  │  └─────────┼────────────────────────┘  │   │
│  │            │                            │   │
│  └────────────┼────────────────────────────┘   │
│               │                               │
│        ┌──────▼──────┐                        │
│        │ Core (Rust) │ ← 同一进程或独立进程    │
│        │ 连接管理/路由│                        │
│        └─────────────┘                        │
└─────────────────────────────────────────────┘
```

**Tauri 命令示例：**

```rust
// src-tauri/src/main.rs
mod commands;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .invoke_handler(tauri::generate_handler![
            commands::install_cloudfin,
            commands::check_core_status,
            commands::send_ws_message
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

// src-tauri/src/commands.rs
#[tauri::command]
async fn check_core_status() -> Result<CoreStatus, String> {
    // 调用本地 Core 实例获取状态
    Ok(CoreStatus { running: true, version: "0.1.0".into() })
}

#[tauri::command]
async fn send_ws_message(action: String, data: Value) -> Result<Value, String> {
    // 通过 Unix Socket 或 TCP 发送消息到 Core
    Ok(json!({ "success": true }))
}
```

**跨平台打包配置：**

```json
// tauri.conf.json
{
  "productName": "Cloudfin",
  "version": "0.1.0",
  "bundle": {
    "active": true,
    "targets": "all",
    "icon": [
      "icons/32x32.png",
      "icons/128x128.png",
      "icons/128x128@2x.png",
      "icons/icon.icns",
      "icons/icon.ico"
    ],
    "windows": {
      "wix": {
        "language": "zh-CN"
      }
    },
    "linux": {
      "appimage": {
        "bundleMediaFramework": false
      }
    }
  }
}
```

---

## 附录 A：API 速查表

### Core → UI 推送消息类型

| action | 说明 | data 结构 |
|--------|------|---------|
| `status_update` | 整体状态推送 | `CoreState` |
| `module_added` | 模块安装 | `{ module: ModuleManifest }` |
| `module_removed` | 模块卸载 | `{ module_id: string }` |
| `module_status_changed` | 模块状态变化 | `{ module_id, status, health }` |
| `peer_connected` | 节点连接 | `{ peer_id, latency_ms }` |
| `peer_disconnected` | 节点断开 | `{ peer_id }` |
| `error` | 错误通知 | `{ code, message }` |

### UI → Core 请求动作

| action | 说明 | data | 响应 |
|--------|------|------|------|
| `core.status` | 获取 Core 状态 | `{}` | `CoreState` |
| `core.install` | 安装 Core | `{ source: 'github' \| 'gitee', platform, arch }` | `{ success: bool }` |
| `core.uninstall` | 卸载 Core | `{}` | `{ success: bool }` |
| `core.upgrade` | 升级 Core | `{ source: 'github' \| 'gitee' }` | `{ success: bool }` |
| `module.list` | 列出所有模块 | `{}` | `{ modules: ModuleManifest[] }` |
| `module.load` | 加载模块 | `{ module_id }` | `{ success: bool }` |
| `module.unload` | 卸载模块 | `{ module_id }` | `{ success: bool }` |
| `module.call` | 调用模块方法 | `{ module_id, method, params }` | `any` |
| `settings.get` | 获取设置 | `{ key: string }` | `any` |
| `settings.set` | 设置值 | `{ key, value }` | `{ success: bool }` |

---

## 附录 B：数据模型

### ModuleManifest

```typescript
interface ModuleManifest {
  id: string;               // "cloudfin.p2p"
  name: string;             // "P2P 通信模块"
  version: string;          // "0.1.0"
  platform: string[];       // ["android", "ios", "linux", "windows", "macos"]
  permissions: string[];    // ["Network"]
  dependencies: string[];   // []
  api_version: string;      // "^1.0"
  ui?: ModuleUI;            // UI 渲染配置
}

interface ModuleUI {
  cards: UICard[];
}

interface UICard {
  id: string;
  type: 'info' | 'actions' | 'settings';
  title: string;
  fields?: UIField[];       // info/settings 用
  buttons?: UIButton[];    // actions 用
}

interface UIField {
  key: string;
  label: string;
  type: 'text' | 'number' | 'toggle' | 'select' | 'slider' | 'password' | 'textarea' | 'datetime';
  default?: any;
  options?: string[];      // select 用
  min?: number;            // number/slider 用
  max?: number;            // number/slider 用
}

interface UIButton {
  action: string;           // 触发的 action 名称
  label: string;
  icon?: string;
  destructive?: boolean;   // 是否为危险操作
}
```

---

*文档版本：v1.0 | 状态：Draft | 最后更新：2026-04-05*
