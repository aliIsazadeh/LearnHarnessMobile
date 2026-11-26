package com.esum.learnfarmore.agent

import com.esum.learnfarmore.domain.LhMessage
import com.esum.learnfarmore.domain.ToolCall
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class AnthropicClient(
    private val http: HttpClient,
    private val json: Json,
    private val apiKey: String,
    private val model: String,
    baseUrl: String,
) : ModelClient {

    private val endpoint = baseUrl.ifBlank { DEFAULT_BASE_URL }.trimEnd('/') + "/v1/messages"

    override suspend fun complete(system: String, messages: List<LhMessage>, tools: List<ToolSpec>): ModelReply {
        val response = http.post(endpoint) {
            header("x-api-key", apiKey)
            header("anthropic-version", API_VERSION)
            setBody(TextContent(buildRequest(model, system, messages, tools).toString(), ContentType.Application.Json))
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) throw ModelException(providerErrorMessage(json, response.status, body))
        return parseResponse(json, body)
    }

    internal companion object {
        const val DEFAULT_BASE_URL = "https://api.anthropic.com"
        const val API_VERSION = "2023-06-01"
        const val MAX_TOKENS = 4096

        fun buildRequest(model: String, system: String, messages: List<LhMessage>, tools: List<ToolSpec>): JsonObject =
            buildJsonObject {
                put("model", model)
                put("max_tokens", MAX_TOKENS)
                put("system", system)
                put("messages", messagesJson(messages))
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        addJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", tool.parameters)
                        }
                    }
                }
            }

        /** Tool results and learner text travel in the same user turn, as the API requires. */
        fun messagesJson(messages: List<LhMessage>): JsonArray {
            val turns = mutableListOf<Pair<String, MutableList<JsonObject>>>()
            fun blocks(role: String): MutableList<JsonObject> {
                val last = turns.lastOrNull()
                if (last != null && last.first == role) return last.second
                return mutableListOf<JsonObject>().also { turns += role to it }
            }
            messages.forEach { message ->
                when (message) {
                    is LhMessage.User -> blocks("user") += buildJsonObject {
                        put("type", "text")
                        put("text", message.text)
                    }
                    is LhMessage.ToolResult -> blocks("user") += buildJsonObject {
                        put("type", "tool_result")
                        put("tool_use_id", message.callId)
                        put("content", message.content)
                        if (message.isError) put("is_error", true)
                    }
                    is LhMessage.Assistant -> {
                        val target = blocks("assistant")
                        if (message.text.isNotBlank()) target += buildJsonObject {
                            put("type", "text")
                            put("text", message.text)
                        }
                        message.toolCalls.forEach { call ->
                            target += buildJsonObject {
                                put("type", "tool_use")
                                put("id", call.id)
                                put("name", call.name)
                                put("input", call.arguments)
                            }
                        }
                    }
                }
            }
            return buildJsonArray {
                turns.filter { it.second.isNotEmpty() }.forEach { (role, content) ->
                    addJsonObject {
                        put("role", role)
                        put("content", JsonArray(content))
                    }
                }
            }
        }

        fun parseResponse(json: Json, body: String): ModelReply {
            val response = json.decodeFromString(MessagesResponse.serializer(), body)
            val text = response.content.filter { it.type == "text" }.joinToString("\n\n") { it.text.orEmpty() }.trim()
            val calls = response.content.filter { it.type == "tool_use" && it.id != null && it.name != null }.map {
                ToolCall(id = it.id!!, name = it.name!!, arguments = it.input ?: JsonObject(emptyMap()))
            }
            return ModelReply(text, calls)
        }
    }
}

@Serializable
private data class MessagesResponse(
    val content: List<ContentBlock> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
)

@Serializable
private data class ContentBlock(
    val type: String,
    val text: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: JsonObject? = null,
)
