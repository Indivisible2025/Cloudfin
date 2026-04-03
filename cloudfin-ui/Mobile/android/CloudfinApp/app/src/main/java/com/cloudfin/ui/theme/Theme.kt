package com.cloudfin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    DARK, LIGHT, SYSTEM, WALLPAPER
}

data class WallpaperConfig(
    val path: String? = null,
    val overlayOpacity: Float = 0.55f, // 0.45 ~ 0.75
    val blurEnabled: Boolean = false
)

@Composable
fun CloudfinTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    wallpaperConfig: WallpaperConfig? = null,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.WALLPAPER -> true // 壁纸模式强制暗色文字
    }

    val colors = if (isDark) DarkColorPalette else LightColorPalette

    // 壁纸模式特殊处理
    val backgroundColor = if (themeMode == ThemeMode.WALLPAPER) {
        Color.Transparent // 露出壁纸
    } else {
        if (isDark) Color(0xFF121212) else Color(0xFFFAFAFA)
    }

    CompositionLocalProvider(
        LocalThemeMode provides themeMode,
        LocalWallpaperConfig provides wallpaperConfig
    ) {
        MaterialTheme(
            colorScheme = colors,
            content = content
        )
    }
}

val LocalThemeMode = compositionLocalOf { ThemeMode.DARK }
val LocalWallpaperConfig = compositionLocalOf<WallpaperConfig?> { null }
