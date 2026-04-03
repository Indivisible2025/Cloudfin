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
import com.cloudfin.model.ModuleAction
import com.cloudfin.model.ModuleInfo
import com.cloudfin.model.ModuleStatus
import com.cloudfin.ui.components.ModuleCard
import com.cloudfin.ui.theme.ThemeMode
import com.cloudfin.ui.theme.WallpaperConfig

@Composable
fun ModulesScreen(
    modules: List<ModuleInfo>,
    onModuleAction: (String, ModuleAction) -> Unit,
    onConfigure: (String) -> Unit,
    themeMode: ThemeMode,
    wallpaperConfig: WallpaperConfig?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(modules) { module ->
            ModuleCard(
                module = module,
                onAction = { action -> onModuleAction(module.id, action) },
                onConfigure = { onConfigure(module.id) },
                cardAlpha = if (themeMode == ThemeMode.WALLPAPER) 0.95f else 1f
            )
        }
    }
}
