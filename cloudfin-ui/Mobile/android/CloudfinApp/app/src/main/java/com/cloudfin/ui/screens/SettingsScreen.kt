package com.cloudfin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudfin.ui.theme.ThemeMode
import com.cloudfin.ui.theme.WallpaperConfig
import java.io.File

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    wallpaperConfig: WallpaperConfig?,
    onThemeChange: (ThemeMode) -> Unit,
    onWallpaperChange: (WallpaperConfig?) -> Unit,
    onSelectWallpaper: () -> Unit = {},
    themeModes: List<ThemeMode> = ThemeMode.entries
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("外观", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        // 主题模式选择器
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                themeModes.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeChange(mode) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = themeMode == mode,
                            onClick = { onThemeChange(mode) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(getThemeModeLabel(mode))
                        if (mode == ThemeMode.WALLPAPER) {
                            Spacer(Modifier.width(4.dp))
                            Text("🖼️", style = TextStyle(fontSize = 16.sp))
                        }
                    }
                }
            }
        }

        // 壁纸选项（仅壁纸模式显示）
        if (themeMode == ThemeMode.WALLPAPER) {
            Spacer(Modifier.height(16.dp))
            WallpaperOptions(
                wallpaperConfig = wallpaperConfig,
                onChange = { onWallpaperChange(it) },
                onSelectImage = onSelectWallpaper
            )
        }

        Spacer(Modifier.height(24.dp))
        Text("网络", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsRow("监听地址", "0.0.0.0:19001")
                SettingsRow("模块目录", "./modules")
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("关于", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsRow("Core 版本", "v0.1.0")
                SettingsRow("UI 版本", "v0.1.0")
            }
        }
    }
}

@Composable
fun SettingsRow(label: String, value: String) {
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

@Composable
fun WallpaperOptions(
    wallpaperConfig: WallpaperConfig?,
    onChange: (WallpaperConfig) -> Unit,
    onSelectImage: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("背景图片", style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(8.dp))

            // 图片预览
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Color.Gray)
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

            Button(onClick = onSelectImage) {
                Text("选择图片")
            }

            if (wallpaperConfig?.path != null) {
                Spacer(Modifier.height(16.dp))

                // 遮罩强度
                Text("遮罩强度: ${((wallpaperConfig.overlayOpacity) * 100).toInt()}%")
                Slider(
                    value = wallpaperConfig.overlayOpacity,
                    onValueChange = { onChange(wallpaperConfig.copy(overlayOpacity = it)) },
                    valueRange = 0.45f..0.75f
                )

                // 模糊效果
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
            }
        }
    }
}

fun getThemeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.DARK -> "暗色模式 🌙"
    ThemeMode.LIGHT -> "亮色模式 ☀️"
    ThemeMode.SYSTEM -> "系统默认 📱"
    ThemeMode.WALLPAPER -> "图片背景"
}
