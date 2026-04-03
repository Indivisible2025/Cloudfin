package com.cloudfin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cloudfin.model.CoreStatus
import com.cloudfin.model.ModuleInfo
import com.cloudfin.model.ModuleStatus
import com.cloudfin.ui.theme.ThemeMode
import com.cloudfin.ui.theme.WallpaperConfig

@Composable
fun StatusScreen(
    coreStatus: CoreStatus?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    themeMode: ThemeMode,
    wallpaperConfig: WallpaperConfig?
) {
    val backgroundColor = when (themeMode) {
        ThemeMode.WALLPAPER -> Color.Transparent
        ThemeMode.DARK -> Color(0xFF121212)
        else -> Color(0xFFFAFAFA)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        if (isLoading && coreStatus == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 状态卡片
                item {
                    StatusCard(coreStatus)
                }

                // 模块状态列表
                item {
                    Text(
                        "模块状态",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                coreStatus?.modules?.forEach { module ->
                    item {
                        ModuleStatusCard(module)
                    }
                }

                // 流量统计
                item {
                    TrafficCard()
                }
            }
        }
    }
}

@Composable
fun StatusCard(status: CoreStatus?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            if (status != null) Color.Green else Color.Red,
                            CircleShape
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (status != null) "运行中" else "未连接",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            if (status != null) {
                Spacer(Modifier.height(8.dp))
                Text("已运行: ${formatUptime(status.uptimeSecs)}")
                Text("设备ID: ${status.deviceId}")
            }
        }
    }
}

@Composable
fun ModuleStatusCard(module: ModuleInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(getModuleEmoji(module.id), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(module.name, style = MaterialTheme.typography.bodyMedium)
                    Text(module.version, style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (module.status == ModuleStatus.RUNNING) Color.Green else Color.Gray,
                            CircleShape
                        )
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    when (module.status) {
                        ModuleStatus.RUNNING -> "已连接"
                        ModuleStatus.STOPPED -> "已停止"
                        ModuleStatus.ERROR -> "错误"
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun TrafficCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("↑ 0 B", style = MaterialTheme.typography.titleLarge)
                Text("上传", style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("↓ 0 B", style = MaterialTheme.typography.titleLarge)
                Text("下载", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

fun formatUptime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return "${hours}小时 ${minutes}分"
}

fun getModuleEmoji(moduleId: String): String = when (moduleId) {
    "p2p" -> "📡"
    "tor" -> "🔒"
    "i2p" -> "🌐"
    "crdt" -> "🔄"
    "storage" -> "💾"
    else -> "📦"
}
