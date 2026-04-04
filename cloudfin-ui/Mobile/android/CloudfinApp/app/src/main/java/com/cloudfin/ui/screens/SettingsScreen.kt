package com.cloudfin.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cloudfin.ui.components.cardBorder
import com.cloudfin.ui.components.cardColors
import com.cloudfin.ui.components.cardElevation
import com.cloudfin.ui.components.cardShape
import com.cloudfin.ui.components.titleTextColor
import com.cloudfin.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 外观 ──────────────────────────────
        Text(
            "外观",
            style = MaterialTheme.typography.titleMedium,
            color = titleTextColor(),
            modifier = Modifier.padding(vertical = 4.dp)
        )

        ThemeModeCard(
            selectedThemeMode = themeMode,
            onThemeChange = onThemeChange
        )

        // ── 网络 ──────────────────────────────
        Text(
            "网络",
            style = MaterialTheme.typography.titleMedium,
            color = titleTextColor(),
            modifier = Modifier.padding(vertical = 4.dp)
        )
        SettingsCard {
            SettingsRow("监听地址", "0.0.0.0:19001")
            SettingsRow("模块目录", "./modules")
        }

        // ── 关于 ──────────────────────────────
        Text(
            "关于",
            style = MaterialTheme.typography.titleMedium,
            color = titleTextColor(),
            modifier = Modifier.padding(vertical = 4.dp)
        )
        SettingsCard {
            SettingsRow("Core 版本", "v0.1.0")
            SettingsRow("UI 版本", "v0.1.0")
        }
    }
}

@Composable
private fun ThemeModeCard(
    selectedThemeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    val isDark = selectedThemeMode != ThemeMode.LIGHT
    val colors = cardColors(isDark)
    val borderColor = cardBorder()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        border = if (borderColor != null) BorderStroke(1.dp, borderColor) else null
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onThemeChange(mode) }
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedThemeMode == mode,
                        onClick = { onThemeChange(mode) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = getThemeModeLabel(mode),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = cardColors(true),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun getThemeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.DARK -> "暗色模式 🌙"
    ThemeMode.LIGHT -> "亮色模式 ☀️"
    ThemeMode.SYSTEM -> "系统默认 📱"
}
