package com.cloudfin.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.cloudfin.ui.theme.DarkSurface
import com.cloudfin.ui.theme.LightSurface

private val CardShape = RoundedCornerShape(16.dp)
private val CardElevation = 2.dp

@Composable
fun cardColors(isDarkTheme: Boolean): CardColors {
    val bg = if (isDarkTheme) DarkSurface else LightSurface
    return CardDefaults.cardColors(
        containerColor = bg,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
}

private val LightBorder = androidx.compose.ui.graphics.Color(0x1A000000)
private val DarkBorder = androidx.compose.ui.graphics.Color(0x33FFFFFF)

fun cardBorder(isDark: Boolean): androidx.compose.ui.graphics.Color? = if (isDark) DarkBorder else LightBorder

@Composable
fun titleTextColor(): androidx.compose.ui.graphics.Color {
    return MaterialTheme.colorScheme.onSurface
}

val cardShape: RoundedCornerShape get() = CardShape
val cardElevation: androidx.compose.ui.unit.Dp get() = CardElevation
