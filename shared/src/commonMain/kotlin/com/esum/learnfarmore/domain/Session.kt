package com.esum.learnfarmore.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Session(
    val id: String,
    val goal: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<LhMessage> = emptyList(),
    val plan: List<PlanStep> = emptyList(),
)

@Serializable
sealed interface LhMessage {
    @Serializable
    @SerialName("user")
    data class User(val text: String) : LhMessage

    @Serializable
    @SerialName("assistant")
    data class Assistant(val text: String, val toolCalls: List<ToolCall> = emptyList()) : LhMessage

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(val callId: String, val content: String, val isError: Boolean = false) : LhMessage
}

@Serializable
data class ToolCall(val id: String, val name: String, val arguments: JsonObject)

@Serializable
data class PlanStep(
    val id: String,
    val title: String,
    val dependsOn: List<String> = emptyList(),
    val status: StepStatus = StepStatus.PENDING,
)

@Serializable
enum class StepStatus { PENDING, ACTIVE, DONE }

@Serializable
enum class Confidence { GUESS, UNSURE, CONFIDENT }

@Serializable
data class LearnerAnswer(
    val answer: String,
    val thinking: String = "",
    val confidence: Confidence,
)
