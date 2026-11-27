package com.esum.learnfarmore.ui.session

import com.esum.learnfarmore.agent.Question
import com.esum.learnfarmore.agent.TeacherTools
import com.esum.learnfarmore.agent.string
import com.esum.learnfarmore.agent.toQuestion
import com.esum.learnfarmore.domain.LearnerAnswer
import com.esum.learnfarmore.domain.LhMessage
import com.esum.learnfarmore.domain.Session
import kotlinx.serialization.json.Json

sealed interface TranscriptItem {
    val key: String

    data class Learner(override val key: String, val text: String) : TranscriptItem
    data class Teacher(override val key: String, val text: String) : TranscriptItem

    /** [answer] is null while the question is still waiting for the learner. */
    data class Asked(override val key: String, val question: Question, val answer: LearnerAnswer?) : TranscriptItem
    data class Recorded(override val key: String, val statement: String) : TranscriptItem
}

/** What the learner sees; rejected tool calls and bookkeeping results are hidden. */
fun Session.transcript(json: Json): List<TranscriptItem> {
    val results = messages.filterIsInstance<LhMessage.ToolResult>().associateBy { it.callId }
    return buildList {
        messages.forEachIndexed { index, message ->
            when (message) {
                is LhMessage.User -> add(TranscriptItem.Learner("m$index", message.text))
                is LhMessage.Assistant -> {
                    if (message.text.isNotBlank()) add(TranscriptItem.Teacher("m$index", message.text))
                    message.toolCalls.forEach { call ->
                        val result = results[call.id]
                        if (result?.isError == true) return@forEach
                        val key = "m$index-${call.id}"
                        val question = call.toQuestion()
                        if (question != null) {
                            add(TranscriptItem.Asked(key, question, result?.let { decodeAnswer(json, it.content) }))
                        } else if (call.name == TeacherTools.RECORD_PROPOSITION && result != null) {
                            call.arguments.string("statement")?.let { add(TranscriptItem.Recorded(key, it)) }
                        }
                    }
                }
                is LhMessage.ToolResult -> Unit
            }
        }
    }
}

private fun decodeAnswer(json: Json, content: String): LearnerAnswer? =
    runCatching { json.decodeFromString(LearnerAnswer.serializer(), content) }.getOrNull()
