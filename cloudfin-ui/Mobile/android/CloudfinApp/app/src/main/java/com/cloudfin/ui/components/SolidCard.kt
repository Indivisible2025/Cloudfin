package com.cloudfin.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.unit.dp

/**
 * 独立小卡片：每个内容区域用它包起来。
 * 在图片背景模式下用暗色/亮色半透明背景，确保文字可读；
 * 在普通模式下用纯色背景。
 */
@Composable
fun SolidCard(
    modifier: Modifier = Modifier,
    isWallpaperMode: Boolean = false,
    contentColor: ColorProducer? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()

    // 暗色/亮色半透明背景，用于图片背景模式
    val wallpaperCardColor = if (isDark) {
        Color(0xDD1E1E1E) // 暗色：85%不透明
    } else {
        Color(0xF5FFFFFF) // 亮色：96%不透明
    }

    val cardColor = if (isWallpaperMode) wallpaperCardColor else Color.Transparent

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = cardColor,
        contentColor = if (contentColor != null) contentColor() else MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier,
            content = content
        )
    }
}
