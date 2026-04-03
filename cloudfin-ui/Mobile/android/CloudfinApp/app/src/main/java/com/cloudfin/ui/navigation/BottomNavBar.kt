package com.cloudfin.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.cloudfin.ui.theme.ThemeMode

@Composable
fun BottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    themeMode: ThemeMode,
    wallpaperConfig: com.cloudfin.ui.theme.WallpaperConfig?
) {
    val navBackground = when (themeMode) {
        ThemeMode.WALLPAPER -> Color.Black.copy(alpha = 0.75f)
        ThemeMode.DARK -> Color(0xFF1E1E1E)
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(navBackground)
    ) {
        NavigationBar(
            containerColor = Color.Transparent
        ) {
            val tabs = listOf("状态", "模块", "网络", "同步", "设置")
            tabs.forEachIndexed { index, title ->
                NavigationBarItem(
                    icon = { Icon(getTabIcon(index), contentDescription = title) },
                    label = { Text(title) },
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) }
                )
            }
        }
    }
}

private fun getTabIcon(index: Int) = when (index) {
    0 -> Icons.Default.Cloud      // 状态
    1 -> Icons.Default.Widgets    // 模块
    2 -> Icons.Default.Router     // 网络
    3 -> Icons.Default.Sync       // 同步
    4 -> Icons.Default.Settings   // 设置
    else -> Icons.Default.Cloud
}
