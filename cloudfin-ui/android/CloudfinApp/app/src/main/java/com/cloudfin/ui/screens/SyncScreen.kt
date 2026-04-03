package com.cloudfin.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cloudfin.model.DocInfo
import com.cloudfin.model.SyncState
import com.cloudfin.model.SyncStatus
import com.cloudfin.ui.theme.ThemeMode
import com.cloudfin.ui.theme.WallpaperConfig

@Composable
fun SyncScreen(
    syncState: SyncState?,
    documents: List<DocInfo>,
    isLoading: Boolean,
    onCreateDoc: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 同步状态卡片
            item {
                SyncStatusCard(syncState)
            }

            // 文档列表标题
            item {
                Text(
                    "文档列表",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 空状态
            if (documents.isEmpty() && !isLoading) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(
                                alpha = if (themeMode == ThemeMode.WALLPAPER) 0.95f else 1f
                            )
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "暂无同步文档",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            // 文档卡片
            items(documents, key = { it.id }) { doc ->
                DocCard(
                    doc = doc,
                    cardAlpha = if (themeMode == ThemeMode.WALLPAPER) 0.95f else 1f,
                    onClick = { /* 打开文档 */ }
                )
            }

            // 操作按钮行
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onCreateDoc,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ 新建文档")
                    }
                    OutlinedButton(
                        onClick = onImport,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("导入")
                    }
                    OutlinedButton(
                        onClick = onExport,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("导出")
                    }
                }
            }
        }
    }
}

@Composable
fun SyncStatusCard(state: SyncState?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "同步中心",
                    style = MaterialTheme.typography.titleLarge
                )
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
                        state?.status ?: "未连接",
                        color = if (state != null) Color(0xFF4CAF50)
                                else MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "文档 · 设备",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        "${state?.docCount ?: 0} 个文档 · ${state?.deviceCount ?: 0} 个设备",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun DocCard(doc: DocInfo, cardAlpha: Float, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = cardAlpha)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(doc.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${doc.sizeKb} KB · ${doc.lastModified}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (doc.syncStatus == SyncStatus.SYNCED)
                        Icons.Default.CheckCircle else Icons.Default.Sync,
                    contentDescription = null,
                    tint = if (doc.syncStatus == SyncStatus.SYNCED)
                        Color(0xFF4CAF50) else Color(0xFFFFC107)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (doc.syncStatus == SyncStatus.SYNCED) "已同步" else "同步中",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (doc.syncStatus == SyncStatus.SYNCED)
                        Color(0xFF4CAF50) else Color(0xFFFFC107)
                )
            }
        }
    }
}
