package com.esum.learnfarmore.agent

import com.esum.learnfarmore.domain.LhMessage
import com.esum.learnfarmore.domain.ToolCall
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

data class ToolSpec(val name: String, val description: String, val parameters: JsonObject)

data class ModelReply(val text: String, val toolCalls: List<ToolCall>)

class ModelException(message: String) : Exception(message)

interface ModelClient {
    suspend fun complete(system: String, messages: List<LhMessage>, tools: List<ToolSpec>): ModelReply
}

/** Providers reject assistant turns with no content; older sessions may still contain them. */
internal fun LhMessage.Assistant.isEmpty(): Boolean = text.isBlank() && toolCalls.isEmpty()

@Serializable
private data class ErrorEnvelope(val error: ErrorBody? = null)

@Serializable
private data class ErrorBody(val message: String? = null)

/** Both supported APIs report failures as `{"error": {"message": ...}}`. */
internal fun providerErrorMessage(json: Json, status: HttpStatusCode, body: String): String {
    val message = runCatching { json.decodeFromString(ErrorEnvelope.serializer(), body).error?.message }.getOrNull()
    return "${status.value} ${status.description}: ${message ?: body.take(300)}"
}
