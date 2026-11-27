package com.esum.learnfarmore.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esum.learnfarmore.AppContainer
import com.esum.learnfarmore.data.ProviderKind
import com.esum.learnfarmore.data.ProviderSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val provider: ProviderKind,
        val model: String,
        val baseUrl: String,
        val apiKey: String = "",
        val hasStoredKey: Boolean = false,
        val savedCount: Int = 0,
    )

    private val _state = MutableStateFlow(
        container.settings.settings.value.let { UiState(it.provider, it.model, it.baseUrl) }
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refreshStoredKey()
    }

    fun setProvider(provider: ProviderKind) {
        _state.update { current ->
            val keepModel = current.model.isNotBlank() && current.model != current.provider.defaultModel
            current.copy(
                provider = provider,
                model = if (keepModel) current.model else provider.defaultModel,
                apiKey = "",
            )
        }
        refreshStoredKey()
    }

    fun setModel(value: String) = _state.update { it.copy(model = value) }

    fun setBaseUrl(value: String) = _state.update { it.copy(baseUrl = value) }

    fun setApiKey(value: String) = _state.update { it.copy(apiKey = value) }

    fun save() {
        val current = _state.value
        viewModelScope.launch {
            container.settings.save(
                ProviderSettings(
                    provider = current.provider,
                    model = current.model.trim().ifEmpty { current.provider.defaultModel },
                    baseUrl = current.baseUrl.trim(),
                ),
                apiKey = current.apiKey.ifBlank { null },
            )
            _state.update { it.copy(apiKey = "", savedCount = it.savedCount + 1) }
            refreshStoredKey()
        }
    }

    fun removeKey() {
        val provider = _state.value.provider
        viewModelScope.launch {
            container.settings.removeApiKey(provider)
            refreshStoredKey()
        }
    }

    private fun refreshStoredKey() {
        val provider = _state.value.provider
        viewModelScope.launch {
            val hasKey = !container.settings.apiKey(provider).isNullOrBlank()
            _state.update { if (it.provider == provider) it.copy(hasStoredKey = hasKey) else it }
        }
    }
}
