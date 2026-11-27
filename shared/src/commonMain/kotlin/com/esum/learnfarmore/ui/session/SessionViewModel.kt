package com.esum.learnfarmore.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esum.learnfarmore.AppContainer
import com.esum.learnfarmore.agent.Question
import com.esum.learnfarmore.agent.SessionStatus
import com.esum.learnfarmore.agent.TeacherRunner.RunState
import com.esum.learnfarmore.agent.status
import com.esum.learnfarmore.agent.withAnswer
import com.esum.learnfarmore.agent.withLearnerMessage
import com.esum.learnfarmore.domain.LearnerAnswer
import com.esum.learnfarmore.domain.Session
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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

    private val teacher = container.teacher

    val state: StateFlow<UiState> = combine(
        container.sessions.sessions.map { all -> all.firstOrNull { it.id == sessionId } },
        teacher.states.map { it[sessionId] ?: RunState() },
    ) { session, run ->
        UiState(session, run.running, run.error, run.missingKey)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        UiState(session = container.sessions.get(sessionId), running = teacher.states.value[sessionId]?.running == true),
    )

    init {
        val session = container.sessions.get(sessionId)
        if (session != null && session.messages.size == 1 && session.status() == SessionStatus.NeedsTeacher) {
            teacher.start(sessionId)
        }
    }

    fun answer(question: Question, answer: LearnerAnswer) = teacher.start(sessionId) { session ->
        session.withAnswer(question, answer, container.json, container.clock())
    }

    fun send(text: String) = teacher.start(sessionId) { session -> session.withLearnerMessage(text, container.clock()) }

    fun retry() = teacher.start(sessionId)
}
