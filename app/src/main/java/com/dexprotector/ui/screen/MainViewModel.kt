package com.dexprotector.ui.screen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dexprotector.engine.DexProtectorEngine
import com.dexprotector.model.ProtectConfig
import com.dexprotector.model.ProtectProgress
import com.dexprotector.model.ProtectState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = DexProtectorEngine(application)

    private val _state = MutableStateFlow<ProtectState>(ProtectState.Idle)
    val state: StateFlow<ProtectState> = _state

    private val _config = MutableStateFlow(ProtectConfig())
    val config: StateFlow<ProtectConfig> = _config

    private val _selectedApkPath = MutableStateFlow<String?>(null)
    val selectedApkPath: StateFlow<String?> = _selectedApkPath

    fun selectApk(path: String) {
        _selectedApkPath.value = path
    }

    fun updateConfig(newConfig: ProtectConfig) {
        _config.value = newConfig
    }

    fun startProtect() {
        val apkPath = _selectedApkPath.value ?: return
        val currentConfig = _config.value

        viewModelScope.launch {
            _state.value = ProtectState.Processing(ProtectProgress("准备中...", 0f))

            try {
                val outputPath = engine.protect(apkPath, currentConfig) { progress ->
                    _state.value = ProtectState.Processing(progress)
                }
                _state.value = ProtectState.Success(outputPath)
            } catch (e: Exception) {
                _state.value = ProtectState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun reset() {
        _state.value = ProtectState.Idle
        _selectedApkPath.value = null
    }
}
