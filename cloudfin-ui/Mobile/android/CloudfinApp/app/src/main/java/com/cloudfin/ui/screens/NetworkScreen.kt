package com.cloudfin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cloudfin.model.PeerInfo
import com.cloudfin.model.P2pState
import com.cloudfin.ui.components.cardBorder
import com.cloudfin.ui.components.cardColors
import com.cloudfin.ui.components.cardElevation
import com.cloudfin.ui.components.cardShape
import com.cloudfin.ui.components.titleTextColor
import com.cloudfin.ui.theme.ThemeMode
import com.cloudfin.ui.theme.WallpaperConfig

@Composable
fun NetworkScreen(
    p2pState: P2pState?,
    peers: List<PeerInfo>,
    isLoading: Boolean,
    onAddPeer: () -> Unit,
    onRefresh: () -> Unit,
    themeMode: ThemeMode,
    isWallpaperMode: Boolean = false,
    wallpaperConfig: WallpaperConfig?
) {
    val isDarkTheme = themeMode != ThemeMode.LIGHT

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // P2P 状态卡片
        item {
            P2pStatusCard(p2pState, isWallpaperMode, isDarkTheme)
        }

        // 节点列表标题（普通文字，无卡片）
        item {
            Text(
                "节点列表",
                style = MaterialTheme.typography.titleMedium,
                color = titleTextColor(isWallpaperMode),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // 空状态
        if (peers.isEmpty() && !isLoading) {
            item {
                EmptyPeerCard("暂无连接节点", isWallpaperMode, isDarkTheme)
            }
        }

        // 节点卡片
        items(peers, key = { it.id }) { peer ->
            PeerCard(peer = peer, isWallpaperMode = isWallpaperMode, isDarkTheme = isDarkTheme)
        }

        // 操作按钮（直接放，不单独成卡）
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = onAddPeer, modifier = Modifier.weight(1f)) {
                    Text("+ 添加节点")
                }
                OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("刷新")
                }
            }
        }
    }
}

@Composable
private fun EmptyPeerCard(message: String, isWallpaperMode: Boolean, isDarkTheme: Boolean) {
    val colors = cardColors(isWallpaperMode, isDarkTheme)
    val borderColor = cardBorder(isWallpaperMode)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        border = if (borderColor != null) BorderStroke(1.dp, borderColor) else null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(message, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun P2pStatusCard(state: P2pState?, isWallpaperMode: Boolean, isDarkTheme: Boolean) {
    val colors = cardColors(isWallpaperMode, isDarkTheme)
    val borderColor = cardBorder(isWallpaperMode)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        border = if (borderColor != null) BorderStroke(1.dp, borderColor) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📶", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Text("P2P 网络", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "状态",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        if (state != null) "已连接 ${state.connectedPeers} 个节点" else "未连接",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "监听地址",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        state?.listeningAddr ?: "--",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun PeerCard(peer: PeerInfo, isWallpaperMode: Boolean, isDarkTheme: Boolean) {
    val colors = cardColors(isWallpaperMode, isDarkTheme)
    val borderColor = cardBorder(isWallpaperMode)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        border = if (borderColor != null) BorderStroke(1.dp, borderColor) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(peer.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    peer.addr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (peer.latencyMs != null) {
                    Text(
                        "延迟: ${peer.latencyMs}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            peer.latencyMs < 50 -> Color(0xFF4CAF50)
                            peer.latencyMs < 100 -> Color(0xFFFFC107)
                            else -> Color(0xFFF44336)
                        }
                    )
                }
            }
            IconButton(onClick = { /* 断开连接 */ }) {
                Icon(Icons.Default.Close, contentDescription = "断开")
            }
        }
    }
}
