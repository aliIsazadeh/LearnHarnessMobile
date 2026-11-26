package com.esum.learnfarmore.agent

import com.esum.learnfarmore.domain.LearnerAnswer
import com.esum.learnfarmore.domain.LhMessage
import com.esum.learnfarmore.domain.Session
import kotlinx.serialization.json.Json

sealed interface SessionStatus {
    /** The teacher asked a question and is waiting for the answer. */
    data class AwaitingAnswer(val question: Question) : SessionStatus

    /** The teacher finished its turn without a question; the learner may write freely. */
    data object AwaitingLearner : SessionStatus

    /** The last message is the learner's (or a tool result), so the teacher should run. */
    data object NeedsTeacher : SessionStatus
}

fun Session.status(): SessionStatus {
    pendingQuestion()?.let { return SessionStatus.AwaitingAnswer(it) }
    val last = messages.lastOrNull()
    return if (last is LhMessage.Assistant && last.toolCalls.isEmpty()) {
        SessionStatus.AwaitingLearner
    } else {
        SessionStatus.NeedsTeacher
    }
}

fun Session.pendingQuestion(): Question? {
    val index = messages.indexOfLast { it is LhMessage.Assistant }
    if (index < 0) return null
    val assistant = messages[index] as LhMessage.Assistant
    val resolved = messages.subList(index + 1, messages.size)
        .filterIsInstance<LhMessage.ToolResult>()
        .mapTo(HashSet()) { it.callId }
    return assistant.toolCalls.firstOrNull { it.id !in resolved }?.toQuestion()
}

fun newSession(id: String, goal: String, now: Long): Session = Session(
    id = id,
    goal = goal.trim(),
    createdAt = now,
    updatedAt = now,
    messages = listOf(LhMessage.User("I want to understand: ${goal.trim()}")),
)

fun Session.withAnswer(question: Question, answer: LearnerAnswer, json: Json, now: Long): Session = copy(
    messages = messages + LhMessage.ToolResult(question.callId, json.encodeToString(LearnerAnswer.serializer(), answer)),
    updatedAt = now,
)

fun Session.withLearnerMessage(text: String, now: Long): Session = copy(
    messages = messages + LhMessage.User(text.trim()),
    updatedAt = now,
)
