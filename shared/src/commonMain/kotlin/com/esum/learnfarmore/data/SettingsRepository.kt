package com.esum.learnfarmore.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
enum class ProviderKind(val defaultModel: String) {
    ANTHROPIC("claude-sonnet-4-5"),
    OPENAI_COMPATIBLE("gpt-5"),
    GROK("grok-4.7"),
}

@Serializable
data class ProviderSettings(
    val provider: ProviderKind = ProviderKind.ANTHROPIC,
    val model: String = ProviderKind.ANTHROPIC.defaultModel,
    /** Empty means the provider's public endpoint. */
    val baseUrl: String = "",
) {
    /** Only a custom OpenAI-compatible endpoint (for example a local server) may run without a key. */
    fun requiresApiKey(): Boolean = provider != ProviderKind.OPENAI_COMPATIBLE || baseUrl.isBlank()
}

class SettingsRepository(
    private val store: JsonFileStore,
    private val secrets: SecretStore,
) {
    private val _settings = MutableStateFlow(ProviderSettings())
    val settings: StateFlow<ProviderSettings> = _settings.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        _settings.value = runCatching { store.read(FILE, ProviderSettings.serializer()) }.getOrNull() ?: ProviderSettings()
    }

    /** A null [apiKey] keeps the stored key; a blank one removes it. */
    suspend fun save(settings: ProviderSettings, apiKey: String?) = withContext(Dispatchers.IO) {
        store.write(FILE, ProviderSettings.serializer(), settings)
        when {
            apiKey == null -> Unit
            apiKey.isBlank() -> secrets.remove(keyName(settings.provider))
            else -> secrets.put(keyName(settings.provider), apiKey.trim())
        }
        _settings.value = settings
    }

    suspend fun apiKey(provider: ProviderKind): String? = withContext(Dispatchers.IO) {
        secrets.get(keyName(provider))
    }

    suspend fun removeApiKey(provider: ProviderKind) = withContext(Dispatchers.IO) {
        secrets.remove(keyName(provider))
    }

    private fun keyName(provider: ProviderKind) = "api_key_${provider.name.lowercase()}"

    private companion object {
        const val FILE = "settings.json"
    }
}
