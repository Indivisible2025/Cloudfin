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
 * 独立小卡片：用于内容区域包装，透明背景。
 */
@Composable
fun SolidCard(
    modifier: Modifier = Modifier,
    contentColor: ColorProducer? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
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
