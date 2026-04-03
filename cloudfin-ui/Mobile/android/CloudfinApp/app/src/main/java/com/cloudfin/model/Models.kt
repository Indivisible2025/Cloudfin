package com.cloudfin.model

data class CoreStatus(
    val version: String,
    val uptimeSecs: Long,
    val deviceId: String,
    val modules: List<ModuleInfo>
)

data class ModuleInfo(
    val id: String,
    val name: String,
    val version: String,
    val status: ModuleStatus,
    val connectedPeers: Int = 0
)

enum class ModuleStatus {
    RUNNING, STOPPED, ERROR
}

enum class ModuleAction {
    START, STOP, CONFIGURE
}

data class P2pState(
    val connectedPeers: Int = 0,
    val listeningAddr: String? = null,
    val peerId: String? = null
)

data class SyncDoc(
    val id: String,
    val name: String,
    val synced: Boolean,
    val updatedAt: Long
)

data class NetworkPeer(
    val id: String,
    val addr: String,
    val connected: Boolean
)

// ─── P2P ───────────────────────────────────────────────────────────────────

data class PeerInfo(
    val id: String,
    val name: String,
    val addr: String,
    val latencyMs: Int? = null,
    val connectedAt: Long = 0
)

// ─── Sync ──────────────────────────────────────────────────────────────────

data class SyncState(
    val status: String, // "connected" / "disconnected"
    val docCount: Int,
    val deviceCount: Int
)

enum class SyncStatus {
    SYNCED, SYNCING, CONFLICT
}

data class DocInfo(
    val id: String,
    val name: String,
    val sizeKb: Long,
    val lastModified: String,
    val syncStatus: SyncStatus
)

// ─── Config ─────────────────────────────────────────────────────────────────

data class Config(
    val deviceName: String? = null,
    val deviceId: String? = null,
    val listenHost: String? = null,
    val listenPort: Int? = null
)
