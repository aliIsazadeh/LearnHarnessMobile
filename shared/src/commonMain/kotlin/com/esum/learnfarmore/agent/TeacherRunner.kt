package com.esum.learnfarmore.agent

import com.esum.learnfarmore.data.GraphRepository
import com.esum.learnfarmore.data.ProviderSettings
import com.esum.learnfarmore.data.SessionRepository
import com.esum.learnfarmore.data.SettingsRepository
import com.esum.learnfarmore.domain.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Runs teacher turns outside any screen, so leaving a session does not cancel a turn in flight. */
class TeacherRunner(
    private val sessions: SessionRepository,
    private val graph: GraphRepository,
    private val settings: SettingsRepository,
    private val clientFor: (ProviderSettings, String) -> ModelClient,
    private val clock: () -> Long,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    data class RunState(val running: Boolean = false, val error: String? = null, val missingKey: Boolean = false)

    private val _states = MutableStateFlow<Map<String, RunState>>(emptyMap())
    val states: StateFlow<Map<String, RunState>> = _states.asStateFlow()

    /** Each turn rewrites the whole graph, so turns from different sessions must not overlap. */
    private val turnLock = Mutex()

    /** Applies [input] to the stored session, then lets the teacher respond. Ignored while a turn is running. */
    fun start(sessionId: String, input: ((Session) -> Session)? = null) {
        var started = false
        _states.update { current ->
            started = current[sessionId]?.running != true
            if (started) current + (sessionId to RunState(running = true)) else current
        }
        if (!started) return
        scope.launch {
            var result = RunState()
            try {
                if (input != null) sessions.get(sessionId)?.let { sessions.save(input(it)) }
                result = turnLock.withLock { teach(sessionId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result = RunState(error = e.message ?: "Something went wrong (${e::class.simpleName}).")
            } finally {
                _states.update { it + (sessionId to result) }
            }
        }
    }

    private suspend fun teach(sessionId: String): RunState {
        val session = sessions.get(sessionId) ?: return RunState()
        val provider = settings.settings.value
        val apiKey = settings.apiKey(provider.provider).orEmpty()
        if (apiKey.isBlank() && provider.requiresApiKey()) {
            return RunState(error = "Add your API key in Settings to start teaching.", missingKey = true)
        }
        TeachingAgent(clientFor(provider, apiKey), clock).run(TeachingState(session, graph.graph.value)) { update ->
            sessions.save(update.session)
            if (update.graph != graph.graph.value) graph.save(update.graph)
        }
        return RunState()
    }
}
