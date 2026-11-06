package com.keyforge.chat.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keyforge.chat.ai.RoutingPreset
import com.keyforge.chat.data.datastore.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: String = "System",
    val dynamicColor: Boolean = true,
    val biometricLock: Boolean = false,
    val routingPreset: String = "Balanced",
    val reduceMotion: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        store.themeMode, store.dynamicColor, store.biometricLock, store.routingPreset, store.reduceMotion,
    ) { theme, dyn, bio, preset, motion ->
        SettingsUiState(theme, dyn, bio, preset, motion)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setThemeMode(mode: String) = viewModelScope.launch { store.setThemeMode(mode) }
    fun setDynamicColor(v: Boolean) = viewModelScope.launch { store.setDynamicColor(v) }
    fun setBiometricLock(v: Boolean) = viewModelScope.launch { store.setBiometricLock(v) }
    fun setRoutingPreset(p: String) = viewModelScope.launch { store.setRoutingPreset(p) }
    fun setReduceMotion(v: Boolean) = viewModelScope.launch { store.setReduceMotion(v) }
}
