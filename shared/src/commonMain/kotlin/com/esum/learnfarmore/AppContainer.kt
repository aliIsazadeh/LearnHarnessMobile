package com.esum.learnfarmore

import com.esum.learnfarmore.agent.AnthropicClient
import com.esum.learnfarmore.agent.ModelClient
import com.esum.learnfarmore.agent.OpenAiCompatibleClient
import com.esum.learnfarmore.agent.TeacherRunner
import com.esum.learnfarmore.data.GraphRepository
import com.esum.learnfarmore.data.JsonFileStore
import com.esum.learnfarmore.data.ProviderKind
import com.esum.learnfarmore.data.ProviderSettings
import com.esum.learnfarmore.data.SecretStore
import com.esum.learnfarmore.data.SessionRepository
import com.esum.learnfarmore.data.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/** App-wide dependencies; create once per process. */
class AppContainer(dataDirectory: String, secretStore: SecretStore) {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() }

    private val store = JsonFileStore(Path(dataDirectory, "learnharness"), json)
    val sessions = SessionRepository(store)
    val graph = GraphRepository(store)
    val settings = SettingsRepository(store, secretStore)

    private val http = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 180_000
            socketTimeoutMillis = 180_000
        }
    }

    private val loadMutex = Mutex()
    private var loaded = false

    suspend fun load() = loadMutex.withLock {
        if (loaded) return@withLock
        settings.load()
        sessions.load()
        graph.load()
        loaded = true
    }

    fun modelClient(settings: ProviderSettings, apiKey: String): ModelClient = when (settings.provider) {
        ProviderKind.ANTHROPIC -> AnthropicClient(http, json, apiKey, settings.model, settings.baseUrl)
        ProviderKind.OPENAI_COMPATIBLE -> OpenAiCompatibleClient(http, json, apiKey, settings.model, settings.baseUrl)
    }

    val teacher = TeacherRunner(sessions, graph, settings, ::modelClient, clock)
}
