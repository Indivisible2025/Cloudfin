package com.cloudfin.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
    isDarkTheme: Boolean
) {
    // 壁纸模式：跟随暗色（完全不透明）
    // 暗色模式：0xFF1E1E1E
    // 亮色模式：0xFFF5F5F5
    val navBackground = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(navBackground)
    ) {
        NavigationBar(
            containerColor = Color.Transparent
        ) {
            NavigationBarItem(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                icon = { Text("☁️") },
                label = { Text("状态") }
            )
            NavigationBarItem(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                icon = { Text("📦") },
                label = { Text("模块") }
            )
            NavigationBarItem(
                selected = selectedTab == 2,
                onClick = { onTabSelected(2) },
                icon = { Text("📡") },
                label = { Text("网络") }
            )
            NavigationBarItem(
                selected = selectedTab == 3,
                onClick = { onTabSelected(3) },
                icon = { Text("🔄") },
                label = { Text("同步") }
            )
            NavigationBarItem(
                selected = selectedTab == 4,
                onClick = { onTabSelected(4) },
                icon = { Icon(Icons.Filled.Settings, contentDescription = "设置") },
                label = { Text("设置") }
            )
        }
    }
}
