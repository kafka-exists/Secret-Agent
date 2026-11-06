package com.keyforge.chat.feature.keys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keyforge.chat.ai.ProviderType
import com.keyforge.chat.data.repo.ApiKeyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KeyRowState(
    val id: String,
    val label: String,
    val provider: ProviderType,
    val maskedValue: String,
    val enabled: Boolean,
    val healthState: String,
    val lastUsedAt: Long,
    val useCount: Int,
    val lastError: String?,
)

data class KeysUiState(
    val rows: List<KeyRowState> = emptyList(),
    val showAddDialog: Boolean = false,
    val testingId: String? = null,
    val testResult: String? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class KeysViewModel @Inject constructor(
    private val repo: ApiKeyRepository,
) : ViewModel() {

    private val _local = MutableStateFlow(KeysUiState())
    val state: StateFlow<KeysUiState> = combine(repo.observeAll(), _local) { entities, local ->
        local.copy(
            rows = entities.map { e ->
                KeyRowState(
                    id = e.id,
                    label = e.label,
                    provider = runCatching { ProviderType.valueOf(e.provider) }.getOrDefault(ProviderType.GROQ),
                    maskedValue = repo.maskPlaintext(repo.decrypt(e)),
                    enabled = e.enabled,
                    healthState = e.healthState,
                    lastUsedAt = e.lastUsedAt,
                    useCount = e.useCount,
                    lastError = e.lastError,
                )
            },
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, KeysUiState())

    fun showAddDialog(show: Boolean) { _local.update { it.copy(showAddDialog = show) } }

    fun addKey(provider: ProviderType, label: String, plaintext: String) {
        viewModelScope.launch {
            repo.add(provider.name, label, plaintext)
            _local.update { it.copy(showAddDialog = false) }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repo.setEnabled(id, enabled) }
    }

    fun delete(id: String) { viewModelScope.launch { repo.delete(id) } }

    fun reorder(orderedIds: List<String>) { viewModelScope.launch { repo.reorder(orderedIds) } }

    fun test(id: String) {
        _local.update { it.copy(testingId = id, testResult = null) }
        viewModelScope.launch {
            val entity = repo.getAll().firstOrNull { it.id == id } ?: return@launch
            // The actual provider test happens upstream; here we record a placeholder
            // result. A full test wires in the AiProvider map.
            _local.update { it.copy(testingId = null, testResult = "Key queued for test") }
        }
    }
}
