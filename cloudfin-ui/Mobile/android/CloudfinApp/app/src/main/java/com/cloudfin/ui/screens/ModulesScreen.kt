package com.cloudfin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cloudfin.model.ModuleAction
import com.cloudfin.model.ModuleInfo
import com.cloudfin.ui.components.ModuleCard
import com.cloudfin.ui.components.titleTextColor
import com.cloudfin.ui.theme.ThemeMode

@Composable
fun ModulesScreen(
    modules: List<ModuleInfo>,
    onModuleAction: (String, ModuleAction) -> Unit,
    onConfigure: (String) -> Unit,
    themeMode: ThemeMode) {
    val isDarkTheme = themeMode != ThemeMode.LIGHT

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 标题（普通文字，无独立卡片）
        item {
            Text(
                "已安装 ${modules.size} 个模块",
                style = MaterialTheme.typography.titleMedium,
                color = titleTextColor(),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        items(modules) { module ->
            ModuleCard(
                module = module,
                onAction = { action -> onModuleAction(module.id, action) },
                onConfigure = { onConfigure(module.id) },
                isDarkTheme = isDarkTheme
            )
        }
    }
}
