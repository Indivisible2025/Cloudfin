package com.cloudfin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*

enum class ThemeMode {
    DARK, LIGHT, SYSTEM
}

@Composable
fun CloudfinTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()

    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    val colors = if (isDark) DarkColorPalette else LightColorPalette

    CompositionLocalProvider(
        LocalThemeMode provides themeMode
    ) {
        MaterialTheme(
            colorScheme = colors,
            content = content
        )
    }
}

val LocalThemeMode = compositionLocalOf { ThemeMode.DARK }
