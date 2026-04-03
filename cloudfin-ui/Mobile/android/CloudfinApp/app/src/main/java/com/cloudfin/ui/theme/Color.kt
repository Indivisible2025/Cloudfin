package com.cloudfin.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

// 主色
val Primary = Color(0xFF6C63FF)

// 暗色模式
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkOnSurface = Color(0xFFE0E0E0)
val DarkOnSurfaceVariant = Color(0xFFB0B0B0)

// 亮色模式
val LightBackground = Color(0xFFFAFAFA)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF212121)
val LightOnSurfaceVariant = Color(0xFF757575)

// 状态色
val Success = Color(0xFF4CAF50)
val Warning = Color(0xFFFFC107)
val Error = Color(0xFFF44336)

// 卡片边框
val DarkBorder = Color(0x33FFFFFF) // 20% white
val LightBorder = Color(0x1A000000) // 10% black

// 暗色主题调色板
val DarkColorPalette = darkColorScheme(
    primary = Primary,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.White,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = Error
)

// 亮色主题调色板
val LightColorPalette = lightColorScheme(
    primary = Primary,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = Error
)
