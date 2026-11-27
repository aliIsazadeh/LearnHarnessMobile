package com.esum.learnfarmore.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esum.learnfarmore.AppContainer
import com.esum.learnfarmore.agent.Question
import com.esum.learnfarmore.agent.SessionStatus
import com.esum.learnfarmore.agent.TeachingAgent
import com.esum.learnfarmore.agent.TeachingState
import com.esum.learnfarmore.agent.status
import com.esum.learnfarmore.agent.withAnswer
import com.esum.learnfarmore.agent.withLearnerMessage
import com.esum.learnfarmore.data.ProviderKind
import com.esum.learnfarmore.domain.LearnerAnswer
import com.esum.learnfarmore.domain.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionViewModel(
    private val sessionId: String,
    private val container: AppContainer,
) : ViewModel() {

    data class UiState(
        val session: Session? = null,
        val running: Boolean = false,
        val error: String? = null,
        val missingKey: Boolean = false,
    )

    private data class Failure(val message: String, val missingKey: Boolean = false)

    private val running = MutableStateFlow(false)
    private val failure = MutableStateFlow<Failure?>(null)

    val state: StateFlow<UiState> = combine(
        container.sessions.sessions.map { all -> all.firstOrNull { it.id == sessionId } },
        running,
        failure,
    ) { session, running, failure ->
        UiState(session, running, failure?.message, failure?.missingKey == true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState(session = container.sessions.get(sessionId)))

    init {
        val session = container.sessions.get(sessionId)
        if (session != null && session.messages.size == 1 && session.status() == SessionStatus.NeedsTeacher) {
            launchTeacher()
        }
    }

    fun answer(question: Question, answer: LearnerAnswer) = launchTeacher { session ->
        session.withAnswer(question, answer, container.json, container.clock())
    }

    fun send(text: String) = launchTeacher { session -> session.withLearnerMessage(text, container.clock()) }

    fun retry() = launchTeacher()

    private fun launchTeacher(input: ((Session) -> Session)? = null) {
        if (running.value) return
        running.value = true
        failure.value = null
        viewModelScope.launch {
            try {
                if (input != null) {
                    val session = container.sessions.get(sessionId) ?: return@launch
                    container.sessions.save(input(session))
                }
                teach()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure.value = Failure(e.message ?: "Something went wrong (${e::class.simpleName}).")
            } finally {
                running.value = false
            }
        }
    }

    private suspend fun teach() {
        val session = container.sessions.get(sessionId) ?: return
        val settings = container.settings.settings.value
        val apiKey = container.settings.apiKey(settings.provider).orEmpty()
        if (apiKey.isBlank() && (settings.provider == ProviderKind.ANTHROPIC || settings.baseUrl.isBlank())) {
            failure.value = Failure("Add your API key in Settings to start teaching.", missingKey = true)
            return
        }
        val agent = TeachingAgent(container.modelClient(settings, apiKey), container.clock)
        agent.run(TeachingState(session, container.graph.graph.value)) { update ->
            container.sessions.save(update.session)
            if (update.graph != container.graph.graph.value) container.graph.save(update.graph)
        }
    }
}
