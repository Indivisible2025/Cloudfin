package com.cloudfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cloudfin.model.ModuleAction
import com.cloudfin.ui.screens.moduleStatusText
import com.cloudfin.model.ModuleInfo
import com.cloudfin.model.ModuleStatus
import com.cloudfin.ui.screens.getModuleEmoji

@Composable
fun ModuleCard(
    module: ModuleInfo,
    onAction: (ModuleAction) -> Unit,
    onConfigure: () -> Unit,
    isDarkTheme: Boolean = true
) {
    val colors = cardColors(isDarkTheme)
    val borderColor = cardBorder()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        border = if (borderColor != null) BorderStroke(1.dp, borderColor) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(getModuleEmoji(module.id), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(module.name, style = MaterialTheme.typography.titleLarge)
                        Text(module.version, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                when (module.status) {
                                    ModuleStatus.RUNNING -> Color(0xFF4CAF50)
                                    ModuleStatus.STOPPED -> Color(0xFF9E9E9E)
                                    ModuleStatus.ERROR -> Color(0xFFF44336)
                                    else -> Color(0xFF9E9E9E)
                                },
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(moduleStatusText(module.status))
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onAction(if (module.status == ModuleStatus.RUNNING) ModuleAction.STOP else ModuleAction.START) }
                ) {
                    Text(if (module.status == ModuleStatus.RUNNING) "停止" else "启动")
                }
                OutlinedButton(onClick = onConfigure) {
                    Text("配置")
                }
            }
        }
    }
}
