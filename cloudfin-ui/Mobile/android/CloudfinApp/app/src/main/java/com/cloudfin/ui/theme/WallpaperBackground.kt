package com.cloudfin.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory

/**
 * 壁纸背景组件
 *
 * Layer 栈（底→顶）：
 * 1. 壁纸图片（全屏 Crop）
 * 2. 遮罩层（半透明黑，alpha = overlayOpacity）
 * 3. 内容（通过 content() 传入）
 */
@Composable
fun WallpaperBackground(
    wallpaperConfig: WallpaperConfig?,
    content: @Composable () -> Unit
) {
    if (wallpaperConfig?.path == null) {
        content()
        return
    }

    val bitmap = try {
        BitmapFactory.decodeFile(wallpaperConfig.path)
    } catch (e: Exception) {
        null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: 壁纸图片
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "wallpaper",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Layer 2: 遮罩层（关键！提供壁纸与内容之间的对比度）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = wallpaperConfig.overlayOpacity))
        )

        // Layer 3: 内容
        content()
    }
}
