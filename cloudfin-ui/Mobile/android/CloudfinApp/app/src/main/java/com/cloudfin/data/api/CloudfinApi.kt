package com.cloudfin.data.api

import com.cloudfin.model.Config
import com.cloudfin.model.CoreStatus
import com.cloudfin.model.ModuleInfo
import com.cloudfin.model.ModuleStatus
import com.cloudfin.model.PeerInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Cloudfin WebSocket API Client
 * 单一 WebSocket 连接，复用于所有请求（请求+推送）
 */
class CloudfinApi(
    private val baseWsUrl: String = "ws://127.0.0.1:19001/ws"
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // 0 = 不超时，长连接
        .build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 请求 Future（请求ID → CompletableFuture）
    private val pendingRequests = mutableMapOf<String, CompletableFuture<JSONObject>>()

    // 推送 Flow（UI 订阅实时更新）
    private val _events = MutableSharedFlow<CloudfinEvent>(replay = 0)
    val events: SharedFlow<CloudfinEvent> = _events.asSharedFlow()

    // 连接状态
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    sealed class CloudfinEvent {
        data class StatusUpdate(val status: CoreStatus) : CloudfinEvent()
        data class ModuleStatusChanged(val moduleId: String, val status: String) : CloudfinEvent()
        data class PeerConnected(val peer: PeerInfo) : CloudfinEvent()
        data class PeerDisconnected(val peerId: String) : CloudfinEvent()
        data class SyncUpdate(val docId: String, val status: String) : CloudfinEvent()
        data class Error(val message: String) : CloudfinEvent()
    }

    // 连接
    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.CONNECTED) {
            return
        }
        _connectionState.value = ConnectionState.CONNECTING

        val request = Request.Builder()
            .url(baseWsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    handleMessage(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.ERROR
                scope.launch {
                    _events.emit(CloudfinEvent.Error(t.message ?: "Connection failed"))
                }
            }
        })
    }

    // 断开
    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    // 处理收到的消息（推送 or 响应）
    private suspend fun handleMessage(text: String) {
        try {
            val msg = JSONObject(text)
            val type = msg.optString("type")

            when (type) {
                // 服务器推送
                "status_update" -> {
                    val data = msg.getJSONObject("data")
                    _events.emit(CloudfinEvent.StatusUpdate(parseCoreStatus(data)))
                }
                "module_status_changed" -> {
                    val moduleId = msg.getString("module_id")
                    val status = msg.getString("status")
                    _events.emit(CloudfinEvent.ModuleStatusChanged(moduleId, status))
                }
                "peer_connected" -> {
                    _events.emit(CloudfinEvent.PeerConnected(parsePeer(msg.getJSONObject("peer"))))
                }
                "peer_disconnected" -> {
                    _events.emit(CloudfinEvent.PeerDisconnected(msg.getString("peer_id")))
                }
                "sync_update" -> {
                    _events.emit(CloudfinEvent.SyncUpdate(
                        msg.getString("doc_id"),
                        msg.getString("status")
                    ))
                }

                // 请求响应（带请求ID）
                "response" -> {
                    val requestId = msg.optString("request_id")
                    if (requestId.isNotEmpty() && pendingRequests.containsKey(requestId)) {
                        pendingRequests.remove(requestId)?.complete(msg.optJSONObject("data") ?: JSONObject())
                    }
                }

                // 错误响应
                "error" -> {
                    val requestId = msg.optString("request_id")
                    if (requestId.isNotEmpty() && pendingRequests.containsKey(requestId)) {
                        pendingRequests.remove(requestId)?.completeExceptionally(
                            Exception(msg.optString("error", "Unknown error"))
                        )
                    } else {
                        _events.emit(CloudfinEvent.Error(msg.optString("error", "Unknown error")))
                    }
                }
            }
        } catch (e: Exception) {
            _events.emit(CloudfinEvent.Error("Parse error: ${e.message}"))
        }
    }

    // ========== 公开 API（全部走 WebSocket）==========

    suspend fun getCoreStatus(): Result<CoreStatus> = withContext(Dispatchers.IO) {
        try {
            val requestId = java.util.UUID.randomUUID().toString()
            val request = JSONObject().apply {
                put("type", "request")
                put("action", "get_core_status")
                put("request_id", requestId)
            }
            val future = CompletableFuture<JSONObject>()
            pendingRequests[requestId] = future

            webSocket?.send(request.toString())

            val response = withTimeout(5000L) { future.await() }
            Result.success(parseCoreStatus(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getModules(): Result<List<ModuleInfo>> = withContext(Dispatchers.IO) {
        try {
            val requestId = java.util.UUID.randomUUID().toString()
            val request = JSONObject().apply {
                put("type", "request")
                put("action", "get_modules")
                put("request_id", requestId)
            }
            val future = CompletableFuture<JSONObject>()
            pendingRequests[requestId] = future

            webSocket?.send(request.toString())

            val response = withTimeout(5000L) { future.await() }
            val modules = response.getJSONArray("modules")
            val list = mutableListOf<ModuleInfo>()
            for (i in 0 until modules.length()) {
                list.add(parseModule(modules.getJSONObject(i)))
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadModule(moduleId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val requestId = java.util.UUID.randomUUID().toString()
            val request = JSONObject().apply {
                put("type", "request")
                put("action", "load_module")
                put("request_id", requestId)
                put("data", JSONObject().put("module_id", moduleId))
            }
            val future = CompletableFuture<JSONObject>()
            pendingRequests[requestId] = future

            webSocket?.send(request.toString())
            withTimeout(5000L) { future.await() }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unloadModule(moduleId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val requestId = java.util.UUID.randomUUID().toString()
            val request = JSONObject().apply {
                put("type", "request")
                put("action", "unload_module")
                put("request_id", requestId)
                put("data", JSONObject().put("module_id", moduleId))
            }
            val future = CompletableFuture<JSONObject>()
            pendingRequests[requestId] = future

            webSocket?.send(request.toString())
            withTimeout(5000L) { future.await() }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getConfig(): Result<Config> = withContext(Dispatchers.IO) {
        try {
            val requestId = java.util.UUID.randomUUID().toString()
            val request = JSONObject().apply {
                put("type", "request")
                put("action", "get_config")
                put("request_id", requestId)
            }
            val future = CompletableFuture<JSONObject>()
            pendingRequests[requestId] = future

            webSocket?.send(request.toString())
            val response = withTimeout(5000L) { future.await() }
            Result.success(parseConfig(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateConfig(config: Config): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val requestId = java.util.UUID.randomUUID().toString()
            val request = JSONObject().apply {
                put("type", "request")
                put("action", "update_config")
                put("request_id", requestId)
                put("data", JSONObject().apply {
                    config.deviceName?.let { put("device_name", it) }
                    config.listenHost?.let { put("listen_host", it) }
                    config.listenPort?.let { put("listen_port", it) }
                })
            }
            val future = CompletableFuture<JSONObject>()
            pendingRequests[requestId] = future

            webSocket?.send(request.toString())
            withTimeout(5000L) { future.await() }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== 解析方法 ==========

    private fun parseCoreStatus(data: JSONObject): CoreStatus {
        return CoreStatus(
            version = data.getString("version"),
            uptimeSecs = data.getLong("uptime_secs"),
            deviceId = data.getString("device_id"),
            modules = emptyList() // 从 get_modules 获取
        )
    }

    private fun parseModule(data: JSONObject): ModuleInfo {
        val statusStr = data.optString("status", "STOPPED")
        val status = try {
            ModuleStatus.valueOf(statusStr.uppercase())
        } catch (e: Exception) {
            ModuleStatus.STOPPED
        }
        return ModuleInfo(
            id = data.getString("id"),
            name = data.optString("name", data.getString("id")),
            version = data.optString("version", "unknown"),
            status = status,
            connectedPeers = data.optInt("connected_peers", 0)
        )
    }

    private fun parseConfig(data: JSONObject): Config {
        return Config(
            deviceName = data.optString("device_name"),
            deviceId = data.optString("device_id"),
            listenHost = data.optString("listen_host"),
            listenPort = data.optInt("listen_port")
        )
    }

    private fun parsePeer(data: JSONObject): PeerInfo {
        return PeerInfo(
            id = data.getString("id"),
            name = data.optString("name", "Unknown"),
            addr = data.getString("addr"),
            latencyMs = data.optInt("latency_ms", -1).takeIf { it >= 0 },
            connectedAt = data.optLong("connected_at", 0)
        )
    }
}

// 需要在 build.gradle 添加
// implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0")
