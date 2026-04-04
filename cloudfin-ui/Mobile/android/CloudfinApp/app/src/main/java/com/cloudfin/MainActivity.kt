package com.cloudfin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cloudfin.data.repository.CloudfinRepository
import com.cloudfin.model.*
import com.cloudfin.ui.navigation.BottomNavBar
import com.cloudfin.ui.screens.*
import com.cloudfin.ui.theme.*
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent {
            CloudfinApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudfinApp(
    viewModel: MainViewModel = viewModel(
        factory = MainViewModelFactory(CloudfinRepository())
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 继承切换前的亮/暗模式（壁纸模式本身不影响颜色方案）
    val effectiveTheme = if (uiState.themeMode == ThemeMode.WALLPAPER) {
        uiState.previousThemeMode
    } else {
        uiState.themeMode
    }

    val isDarkTheme = when (effectiveTheme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.WALLPAPER -> true
    }

    val wallpaperPath = uiState.wallpaperConfig?.path
    // 壁纸模式：themeMode == WALLPAPER 且 path 指向有效文件（AsyncImage 加载失败则显示深色背景）
    val isWallpaperMode = uiState.themeMode == ThemeMode.WALLPAPER && wallpaperPath != null

    // Layer 0 背景色
    val rootBackgroundColor = when {
        isWallpaperMode -> DarkBackground            // 壁纸 AsyncImage 会盖住它
        isDarkTheme -> DarkBackground               // 0xFF121212
        else -> LightBackground                     // 0xFFFAFAFA
    }

    // 图片选择器
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val file = File(context.filesDir, "wallpaper.png")
                FileOutputStream(file).use { out ->
                    inputStream?.copyTo(out)
                }
                inputStream?.close()
                val current = uiState.wallpaperConfig ?: WallpaperConfig()
                viewModel.setWallpaperConfig(current.copy(path = file.absolutePath))
            } catch (_: Exception) {
                // 文件保存失败，忽略
            }
        }
    }

    CloudfinTheme(
        themeMode = effectiveTheme,
        wallpaperConfig = uiState.wallpaperConfig
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Layer 0：背景色（所有模式都有，壁纸模式下被 AsyncImage 盖住）
            Box(modifier = Modifier.fillMaxSize().background(rootBackgroundColor))

            // Layer 1：壁纸图片（仅壁纸模式，完全由 Compose 处理）
            if (isWallpaperMode) {
                wallpaperPath?.let { path ->
                    AsyncImage(
                        model = File(path),
                        contentDescription = "wallpaper",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Scaffold + 各页面
            Scaffold(
                bottomBar = {
                    BottomNavBar(
                        selectedTab = uiState.selectedTab,
                        onTabSelected = { viewModel.selectTab(it) },
                        themeMode = effectiveTheme,
                        isDarkTheme = isDarkTheme
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (uiState.selectedTab) {
                        0 -> StatusScreen(
                            coreStatus = uiState.coreStatus,
                            isLoading = uiState.isLoading,
                            onRefresh = { viewModel.refreshStatus() },
                            themeMode = effectiveTheme,
                            isWallpaperMode = isWallpaperMode,
                            wallpaperConfig = uiState.wallpaperConfig
                        )
                        1 -> ModulesScreen(
                            modules = uiState.modules,
                            onModuleAction = { id, action -> viewModel.controlModule(id, action) },
                            onConfigure = { id -> viewModel.configureModule(id) },
                            themeMode = effectiveTheme,
                            isWallpaperMode = isWallpaperMode,
                            wallpaperConfig = uiState.wallpaperConfig
                        )
                        2 -> NetworkScreen(
                            p2pState = null,
                            peers = uiState.peers.map { PeerInfo(id = it.id, name = it.id, addr = it.addr, latencyMs = null, connectedAt = 0) },
                            isLoading = uiState.isLoading,
                            onAddPeer = { viewModel.showAddPeerDialog() },
                            onRefresh = { viewModel.refreshPeers() },
                            themeMode = effectiveTheme,
                            isWallpaperMode = isWallpaperMode,
                            wallpaperConfig = uiState.wallpaperConfig
                        )
                        3 -> SyncScreen(
                            syncState = null,
                            documents = uiState.docs.map { DocInfo(id = it.id, name = it.name, sizeKb = 0, lastModified = "", syncStatus = if (it.synced) SyncStatus.SYNCED else SyncStatus.SYNCING) },
                            isLoading = uiState.isLoading,
                            onCreateDoc = { viewModel.createDoc() },
                            onImport = { viewModel.importDoc() },
                            onExport = { viewModel.exportDoc("") },
                            themeMode = effectiveTheme,
                            isWallpaperMode = isWallpaperMode,
                            wallpaperConfig = uiState.wallpaperConfig
                        )
                        4 -> SettingsScreen(
                            themeMode = effectiveTheme,
                            selectedThemeMode = uiState.themeMode,
                            wallpaperConfig = uiState.wallpaperConfig,
                            onThemeChange = { viewModel.setThemeMode(it) },
                            onWallpaperChange = { viewModel.setWallpaperConfig(it) },
                            onSelectWallpaper = {
                                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            isWallpaperMode = isWallpaperMode
                        )
                    }
                }
            }
        }
    }
}
