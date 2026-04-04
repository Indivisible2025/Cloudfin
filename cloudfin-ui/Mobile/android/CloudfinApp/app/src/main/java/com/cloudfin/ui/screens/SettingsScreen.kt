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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudfin.ui.components.cardBorder
import com.cloudfin.ui.components.cardColors
import com.cloudfin.ui.components.cardElevation
import com.cloudfin.ui.components.cardShape
import com.cloudfin.ui.components.titleTextColor
import com.cloudfin.ui.theme.ThemeMode
import com.cloudfin.ui.theme.WallpaperConfig
import java.io.File

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    selectedThemeMode: ThemeMode,
    wallpaperConfig: WallpaperConfig?,
    onThemeChange: (ThemeMode) -> Unit,
    onWallpaperChange: (WallpaperConfig?) -> Unit,
    onSelectWallpaper: () -> Unit = {},
    isWallpaperMode: Boolean = false
) {
    val isDarkTheme = selectedThemeMode != ThemeMode.LIGHT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 外观 ──────────────────────────────
        Text(
            "外观",
            style = MaterialTheme.typography.titleMedium,
            color = titleTextColor(isWallpaperMode),
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // 主题选择卡片
        ThemeModeCard(selectedThemeMode, onThemeChange, isWallpaperMode, isDarkTheme)

        // 壁纸选项（仅 WALLPAPER 模式显示）
        if (selectedThemeMode == ThemeMode.WALLPAPER) {
            WallpaperOptionsCard(
                wallpaperConfig,
                onChange = onWallpaperChange,
                onSelectImage = onSelectWallpaper,
                isWallpaperMode = isWallpaperMode,
                isDarkTheme = isDarkTheme
            )
        }

        // ── 网络 ──────────────────────────────
        Text(
            "网络",
            style = MaterialTheme.typography.titleMedium,
            color = titleTextColor(isWallpaperMode),
            modifier = Modifier.padding(vertical = 4.dp)
        )
        SettingsCard(isWallpaperMode, isDarkTheme) {
            SettingsRow("监听地址", "0.0.0.0:19001")
            SettingsRow("模块目录", "./modules")
        }

        // ── 关于 ──────────────────────────────
        Text(
            "关于",
            style = MaterialTheme.typography.titleMedium,
            color = titleTextColor(isWallpaperMode),
            modifier = Modifier.padding(vertical = 4.dp)
        )
        SettingsCard(isWallpaperMode, isDarkTheme) {
            SettingsRow("Core 版本", "v0.1.0")
            SettingsRow("UI 版本", "v0.1.0")
        }
    }
}

@Composable
private fun ThemeModeCard(
    selectedThemeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    isWallpaperMode: Boolean,
    isDarkTheme: Boolean
) {
    val colors = cardColors(isWallpaperMode, isDarkTheme)
    val borderColor = cardBorder(isWallpaperMode)

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
                    if (mode == ThemeMode.WALLPAPER) {
                        Spacer(Modifier.width(4.dp))
                        Text("🖼️", style = TextStyle(fontSize = 16.sp), modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperOptionsCard(
    wallpaperConfig: WallpaperConfig?,
    onChange: (WallpaperConfig?) -> Unit,
    onSelectImage: () -> Unit,
    isWallpaperMode: Boolean,
    isDarkTheme: Boolean
) {
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
            Text("背景图片", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))

            // 图片预览
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Color.Gray, RoundedCornerShape(16.dp))
            ) {
                wallpaperConfig?.path?.let {
                    AsyncImage(
                        model = File(it),
                        contentDescription = "wallpaper preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } ?: run {
                    Text(
                        "未选择图片",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (wallpaperConfig?.path != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSelectImage) { Text("更换图片") }
                    OutlinedButton(onClick = { onChange(null) }) { Text("清除") }
                }
                Spacer(Modifier.height(16.dp))
                Text("遮罩强度: ${((wallpaperConfig.overlayOpacity) * 100).toInt()}%")
                Slider(
                    value = wallpaperConfig.overlayOpacity,
                    onValueChange = { onChange(wallpaperConfig.copy(overlayOpacity = it)) },
                    valueRange = 0.45f..0.75f
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("模糊效果")
                    Switch(
                        checked = wallpaperConfig.blurEnabled,
                        onCheckedChange = { onChange(wallpaperConfig.copy(blurEnabled = it)) }
                    )
                }
            } else {
                Button(onClick = onSelectImage) { Text("选择图片") }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    isWallpaperMode: Boolean,
    isDarkTheme: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = cardColors(isWallpaperMode, isDarkTheme)
    val borderColor = cardBorder(isWallpaperMode)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        border = if (borderColor != null) BorderStroke(1.dp, borderColor) else null
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
    ThemeMode.WALLPAPER -> "图片背景"
}
