package com.esum.learnfarmore.agent

import com.esum.learnfarmore.domain.LhMessage
import com.esum.learnfarmore.domain.ToolCall
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Chat Completions API: OpenAI, OpenRouter, local servers, and other compatible endpoints. */
class OpenAiCompatibleClient(
    private val http: HttpClient,
    private val json: Json,
    private val apiKey: String,
    private val model: String,
    baseUrl: String,
) : ModelClient {

    private val endpoint = baseUrl.ifBlank { DEFAULT_BASE_URL }.trimEnd('/') + "/chat/completions"

    override suspend fun complete(system: String, messages: List<LhMessage>, tools: List<ToolSpec>): ModelReply {
        val response = http.post(endpoint) {
            if (apiKey.isNotBlank()) bearerAuth(apiKey)
            setBody(TextContent(buildRequest(model, system, messages, tools).toString(), ContentType.Application.Json))
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) throw ModelException(providerErrorMessage(json, response.status, body))
        return parseResponse(json, body)
    }

    internal companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"

        fun buildRequest(model: String, system: String, messages: List<LhMessage>, tools: List<ToolSpec>): JsonObject =
            buildJsonObject {
                put("model", model)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", system)
                    }
                    messages.forEach { message ->
                        if (message is LhMessage.Assistant && message.isEmpty()) return@forEach
                        addJsonObject {
                            when (message) {
                                is LhMessage.User -> {
                                    put("role", "user")
                                    put("content", message.text)
                                }
                                is LhMessage.ToolResult -> {
                                    put("role", "tool")
                                    put("tool_call_id", message.callId)
                                    put("content", message.content)
                                }
                                is LhMessage.Assistant -> {
                                    put("role", "assistant")
                                    if (message.text.isBlank()) put("content", JsonNull) else put("content", message.text)
                                    if (message.toolCalls.isNotEmpty()) putJsonArray("tool_calls") {
                                        message.toolCalls.forEach { call ->
                                            addJsonObject {
                                                put("id", call.id)
                                                put("type", "function")
                                                putJsonObject("function") {
                                                    put("name", call.name)
                                                    put("arguments", call.arguments.toString())
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        addJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.parameters)
                            }
                        }
                    }
                }
            }

        fun parseResponse(json: Json, body: String): ModelReply {
            val message = json.decodeFromString(ChatResponse.serializer(), body).choices.firstOrNull()?.message
                ?: throw ModelException("The model returned no choices.")
            val calls = message.toolCalls.orEmpty().map { call ->
                val arguments = runCatching { json.parseToJsonElement(call.function.arguments).jsonObject }
                    .getOrElse { JsonObject(emptyMap()) }
                ToolCall(id = call.id, name = call.function.name, arguments = arguments)
            }
            return ModelReply(message.content.orEmpty().trim(), calls)
        }
    }
}

@Serializable
private data class ChatResponse(val choices: List<Choice> = emptyList())

@Serializable
private data class Choice(val message: ChatMessage)

@Serializable
private data class ChatMessage(
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ChatToolCall>? = null,
)

@Serializable
private data class ChatToolCall(val id: String, val function: ChatFunction)

@Serializable
private data class ChatFunction(val name: String, val arguments: String = "{}")
