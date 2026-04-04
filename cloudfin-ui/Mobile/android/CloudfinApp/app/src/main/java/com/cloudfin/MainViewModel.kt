package com.cloudfin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cloudfin.data.repository.CloudfinRepository
import com.cloudfin.model.*
import com.cloudfin.ui.theme.ThemeMode
import com.cloudfin.ui.theme.WallpaperConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val selectedTab: Int = 0,
    val coreStatus: CoreStatus? = null,
    val modules: List<ModuleInfo> = emptyList(),
    val peers: List<NetworkPeer> = emptyList(),
    val docs: List<SyncDoc> = emptyList(),
    val isLoading: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val wallpaperConfig: WallpaperConfig? = null,
    // WALLPAPER 模式继承切换前的亮/暗模式
    val previousThemeMode: ThemeMode = ThemeMode.SYSTEM
)

class MainViewModel(
    private val repository: CloudfinRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        refreshStatus()
    }

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            repository.getCoreStatus().onSuccess { status ->
                _uiState.update { it.copy(coreStatus = status, isLoading = false) }
            }.onFailure {
                _uiState.update { it.copy(coreStatus = null, isLoading = false) }
            }

            repository.getModules().onSuccess { modules ->
                _uiState.update { it.copy(modules = modules) }
            }
        }
    }

    fun controlModule(moduleId: String, action: ModuleAction) {
        viewModelScope.launch {
            repository.controlModule(moduleId, action)
            refreshStatus()
        }
    }

    fun configureModule(moduleId: String) {
        // TODO: Navigate to module config screen
    }

    fun refreshPeers() {
        // TODO: Fetch peers from API
    }

    fun disconnectPeer(peerId: String) {
        // TODO: Call API to disconnect peer
    }

    fun showAddPeerDialog() {
        // TODO: Show bottom sheet for adding peer
    }

    fun createDoc() {
        // TODO: Create new document
    }

    fun importDoc() {
        // TODO: Import document
    }

    fun exportDoc(docId: String) {
        // TODO: Export document
    }

    fun setThemeMode(mode: ThemeMode) {
        _uiState.update { current ->
            when {
                // 切换到 WALLPAPER：保存当前模式
                mode == ThemeMode.WALLPAPER -> current.copy(
                    themeMode = mode,
                    previousThemeMode = current.themeMode
                )
                // 从 WALLPAPER 切走：恢复之前保存的模式
                current.themeMode == ThemeMode.WALLPAPER -> current.copy(
                    themeMode = mode,
                    previousThemeMode = current.previousThemeMode
                )
                // 普通模式切换
                else -> current.copy(themeMode = mode)
            }
        }
    }

    fun setWallpaperConfig(config: WallpaperConfig?) {
        _uiState.update { it.copy(wallpaperConfig = config) }
    }
}

class MainViewModelFactory(
    private val repository: CloudfinRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
