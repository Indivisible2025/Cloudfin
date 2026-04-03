package com.cloudfin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudfin.data.repository.CloudfinRepository
import com.cloudfin.model.*
import com.cloudfin.ui.navigation.BottomNavBar
import com.cloudfin.ui.screens.*
import com.cloudfin.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    CloudfinTheme(
        themeMode = uiState.themeMode,
        wallpaperConfig = uiState.wallpaperConfig
    ) {
        Scaffold(
            bottomBar = {
                BottomNavBar(
                    selectedTab = uiState.selectedTab,
                    onTabSelected = { viewModel.selectTab(it) },
                    themeMode = uiState.themeMode,
                    wallpaperConfig = uiState.wallpaperConfig
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
                        themeMode = uiState.themeMode,
                        wallpaperConfig = uiState.wallpaperConfig
                    )
                    1 -> ModulesScreen(
                        modules = uiState.modules,
                        onModuleAction = { id, action -> viewModel.controlModule(id, action) },
                        onConfigure = { id -> viewModel.configureModule(id) },
                        themeMode = uiState.themeMode,
                        wallpaperConfig = uiState.wallpaperConfig
                    )
                    2 -> NetworkScreen(
                        p2pState = null,
                        peers = uiState.peers.map { PeerInfo(id = it.id, name = it.id, addr = it.addr, latencyMs = null, connectedAt = 0) },
                        isLoading = uiState.isLoading,
                        onAddPeer = { viewModel.showAddPeerDialog() },
                        onRefresh = { viewModel.refreshPeers() },
                        themeMode = uiState.themeMode,
                        wallpaperConfig = uiState.wallpaperConfig
                    )
                    3 -> SyncScreen(
                        syncState = null,
                        documents = uiState.docs.map { DocInfo(id = it.id, name = it.name, sizeKb = 0, lastModified = "", syncStatus = if (it.synced) com.cloudfin.model.SyncStatus.SYNCED else com.cloudfin.model.SyncStatus.SYNCING) },
                        isLoading = uiState.isLoading,
                        onCreateDoc = { viewModel.createDoc() },
                        onImport = { viewModel.importDoc() },
                        onExport = { viewModel.exportDoc("") },
                        themeMode = uiState.themeMode,
                        wallpaperConfig = uiState.wallpaperConfig
                    )
                    4 -> SettingsScreen(
                        themeMode = uiState.themeMode,
                        wallpaperConfig = uiState.wallpaperConfig,
                        onThemeChange = { viewModel.setThemeMode(it) },
                        onWallpaperChange = { viewModel.setWallpaperConfig(it) }
                    )
                }
            }
        }
    }
}
