package com.cloudfin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.cloudfin.ui.components.cardBorder
import com.cloudfin.ui.components.cardColors
import com.cloudfin.ui.components.cardElevation
import com.cloudfin.ui.components.cardShape
import com.cloudfin.ui.components.titleTextColor
import com.cloudfin.ui.theme.ThemeMode

// Semantic status colors
private val StatusRunning = Color(0xFF4CAF50)
private val StatusStopped = Color(0xFF9E9E9E)
private val StatusError = Color(0xFFF44336)

fun moduleStatusText(status: ModuleStatus): String = when(status) {
    ModuleStatus.RUNNING -> "已连接"
    ModuleStatus.STOPPED -> "已停止"
    ModuleStatus.ERROR -> "错误"
}

@Composable
fun StatusScreen(
    coreStatus: CoreStatus?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    themeMode: ThemeMode) {
    val isDarkTheme = themeMode != ThemeMode.LIGHT

    Box(modifier = Modifier.fillMaxSize()) {
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
                    StatusCard(coreStatus, isDarkTheme)
                }

                // 模块状态标题（普通文字，无卡片）
                item {
                    Text(
                        "模块状态",
                        style = MaterialTheme.typography.titleMedium,
                        color = titleTextColor(),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // 模块列表
                items(coreStatus?.modules?.toList() ?: emptyList()) { module ->
                    ModuleStatusCard(module, isDarkTheme)
                }

                // 流量卡片
                item {
                    TrafficCard(isDarkTheme)
                }
            }
        }
    }
}

@Composable
fun StatusCard(status: CoreStatus?, isDarkTheme: Boolean) {
    val colors = cardColors(isDarkTheme)
    val borderColor = cardBorder(isDarkTheme)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        border = if (borderColor != null) androidx.compose.foundation.BorderStroke(1.dp, borderColor) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            if (status != null) StatusRunning else StatusError,
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
fun ModuleStatusCard(module: ModuleInfo, isDarkTheme: Boolean) {
    val colors = cardColors(isDarkTheme)
    val borderColor = cardBorder(isDarkTheme)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        border = if (borderColor != null) androidx.compose.foundation.BorderStroke(1.dp, borderColor) else null
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
                            when (module.status) {
                                ModuleStatus.RUNNING -> StatusRunning
                                ModuleStatus.STOPPED -> StatusStopped
                                ModuleStatus.ERROR -> StatusError
                            },
                            CircleShape
                        )
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    moduleStatusText(module.status),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun TrafficCard(isDarkTheme: Boolean) {
    val colors = cardColors(isDarkTheme)
    val borderColor = cardBorder(isDarkTheme)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        border = if (borderColor != null) androidx.compose.foundation.BorderStroke(1.dp, borderColor) else null
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
