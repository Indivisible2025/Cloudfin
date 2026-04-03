package com.cloudfin.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File

@Composable
fun WallpaperBackground(
    wallpaperConfig: WallpaperConfig?,
    content: @Composable () -> Unit
) {
    if (wallpaperConfig?.path == null) {
        content()
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 图层1: 壁纸
        AsyncImage(
            model = File(wallpaperConfig.path),
            contentDescription = "wallpaper",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 图层2: 遮罩
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = wallpaperConfig.overlayOpacity))
        )

        // 图层3: 内容
        content()
    }
}
