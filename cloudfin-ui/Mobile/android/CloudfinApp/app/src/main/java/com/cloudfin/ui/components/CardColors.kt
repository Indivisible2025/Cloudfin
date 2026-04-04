package com.cloudfin.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cloudfin.ui.theme.DarkSurface
import com.cloudfin.ui.theme.LightSurface
import com.cloudfin.ui.theme.WallpaperCardBg
import com.cloudfin.ui.theme.WallpaperCardBorder

private val CardShape = RoundedCornerShape(16.dp)
private val CardElevation = 2.dp

/**
 * 根据主题模式返回卡片背景色。
 *
 * @param isWallpaperMode true = 图片背景模式（70% 深灰半透明）
 * @param isDarkTheme     true = 暗色模式，false = 亮色模式
 */
@Composable
fun cardColors(
    isWallpaperMode: Boolean,
    isDarkTheme: Boolean = isSystemInDarkTheme()
): CardColors {
    val bg = when {
        isWallpaperMode -> WallpaperCardBg          // 70% 深灰
        isDarkTheme     -> DarkSurface               // 0xFF1E1E1E 不透明
        else            -> LightSurface              // 0xFFFFFFFF 白色
    }
    // contentColor 必须显式设置，否则子组件可能 fallback 到默认黑色
    val content = when {
        isWallpaperMode -> Color.White // 壁纸模式强制白色文字
        isDarkTheme     -> MaterialTheme.colorScheme.onSurface  // 暗色模式 = 白色
        else            -> MaterialTheme.colorScheme.onSurface  // 亮色模式 = 深色
    }
    return CardDefaults.cardColors(
        containerColor = bg,
        contentColor = content
    )
}

/**
 * 统一卡片边框（仅壁纸模式有浅色边框）
 */
@Composable
fun cardBorder(isWallpaperMode: Boolean): Color? {
    return if (isWallpaperMode) WallpaperCardBorder else null
}

/**
 * 标题文字颜色：壁纸模式下用 92% 白色（轻微降低纯度，
 * 在极亮壁纸上比纯白有稍好对比度；深色壁纸上依然接近白色）
 */
@Composable
fun titleTextColor(isWallpaperMode: Boolean): Color {
    return if (isWallpaperMode) Color.White.copy(alpha = 0.92f)
           else MaterialTheme.colorScheme.onSurface
}

val cardShape: RoundedCornerShape get() = CardShape
val cardElevation: androidx.compose.ui.unit.Dp get() = CardElevation
