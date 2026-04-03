package com.cloudfin.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    val context = LocalContext.current
    val file = File(wallpaperConfig.path)

    // 同步加载 bitmap（文件小，加载很快）
    val bitmap = if (file.exists()) {
        try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
    } else {
        null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 图层1: 壁纸
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "wallpaper",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

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
