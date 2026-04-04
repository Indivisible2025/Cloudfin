package com.cloudfin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cloudfin.model.DocInfo
import com.cloudfin.model.SyncState
import com.cloudfin.model.SyncStatus
import com.cloudfin.ui.components.cardBorder
import com.cloudfin.ui.components.cardColors
import com.cloudfin.ui.components.cardElevation
import com.cloudfin.ui.components.cardShape
import com.cloudfin.ui.components.titleTextColor
import com.cloudfin.ui.theme.ThemeMode

@Composable
fun SyncScreen(
    syncState: SyncState?,
    documents: List<DocInfo>,
    isLoading: Boolean,
    onCreateDoc: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    themeMode: ThemeMode) {
    val isDarkTheme = themeMode != ThemeMode.LIGHT

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 同步状态卡片
        item {
            SyncStatusCard(syncState, isDarkTheme)
        }

        // 文档列表标题（普通文字，无卡片）
        item {
            Text(
                "文档列表",
                style = MaterialTheme.typography.titleMedium,
                color = titleTextColor(),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // 空状态
        if (documents.isEmpty() && !isLoading) {
            item {
                SyncEmptyCard(isDarkTheme)
            }
        }

        // 文档卡片
        items(documents, key = { it.id }) { doc ->
            DocCard(doc = doc, isDarkTheme = isDarkTheme, onClick = { /* 打开文档 */ })
        }

        // 操作按钮（直接放，不单独成卡）
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = onCreateDoc, modifier = Modifier.weight(1f)) {
                    Text("+ 新建文档")
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Text("导入")
                }
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Text("导出")
                }
            }
        }
    }
}

@Composable
private fun SyncEmptyCard(isDarkTheme: Boolean) {
    val colors = cardColors(isDarkTheme)
    val borderColor = cardBorder(isDarkTheme)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        border = if (borderColor != null) BorderStroke(1.dp, borderColor) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Info,
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

@Composable
fun SyncStatusCard(state: SyncState?, isDarkTheme: Boolean) {
    val colors = cardColors(isDarkTheme)
    val borderColor = cardBorder(isDarkTheme)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        border = if (borderColor != null) BorderStroke(1.dp, borderColor) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text("同步中心", style = MaterialTheme.typography.titleLarge)
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
                        color = if (state != null) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
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
fun DocCard(doc: DocInfo, isDarkTheme: Boolean, onClick: () -> Unit) {
    val colors = cardColors(isDarkTheme)
    val borderColor = cardBorder(isDarkTheme)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Info,
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
                    imageVector = if (doc.syncStatus == SyncStatus.SYNCED) Icons.Filled.CheckCircle else Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = if (doc.syncStatus == SyncStatus.SYNCED) Color(0xFF4CAF50) else Color(0xFFFFC107)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (doc.syncStatus == SyncStatus.SYNCED) "已同步" else "同步中",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (doc.syncStatus == SyncStatus.SYNCED) Color(0xFF4CAF50) else Color(0xFFFFC107)
                )
            }
        }
    }
}
