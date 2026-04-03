package com.cloudfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cloudfin.model.ModuleAction
import com.cloudfin.model.ModuleInfo
import com.cloudfin.model.ModuleStatus
import com.cloudfin.ui.screens.getModuleEmoji

@Composable
fun ModuleCard(
    module: ModuleInfo,
    onAction: (ModuleAction) -> Unit,
    onConfigure: () -> Unit,
    cardAlpha: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.surface.copy(alpha = cardAlpha)
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
                        Text(module.name, style = MaterialTheme.typography.h6)
                        Text(module.version, style = MaterialTheme.typography.caption)
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
                    Text(module.status.name)
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
