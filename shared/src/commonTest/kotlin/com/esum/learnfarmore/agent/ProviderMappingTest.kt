package com.esum.learnfarmore.agent

import com.esum.learnfarmore.domain.LhMessage
import com.esum.learnfarmore.domain.ToolCall
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderMappingTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val call = ToolCall("call_1", "ask_question", buildJsonObject { put("prompt", "Why?") })
    private val transcript = listOf(
        LhMessage.User("I want to understand: tides"),
        LhMessage.Assistant("Let's start.", listOf(call)),
        LhMessage.ToolResult("call_1", """{"answer":"gravity"}"""),
    )

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    @Test
    fun anthropicPutsToolResultsInUserTurn() {
        val messages = AnthropicClient.messagesJson(transcript)

        assertEquals(listOf("user", "assistant", "user"), messages.map { it.jsonObject.str("role") })
        val assistant = messages[1].jsonObject.getValue("content").jsonArray.map { it.jsonObject }
        assertEquals(listOf("text", "tool_use"), assistant.map { it.str("type") })
        val result = messages[2].jsonObject.getValue("content").jsonArray.single().jsonObject
        assertEquals("tool_result", result.str("type"))
        assertEquals("call_1", result.str("tool_use_id"))
    }

    @Test
    fun anthropicRequestDeclaresTools() {
        val request = AnthropicClient.buildRequest("model-x", "system", transcript, TeacherTools.specs)

        val tools = request.getValue("tools").jsonArray.map { it.jsonObject }
        assertEquals(TeacherTools.specs.map { it.name }, tools.map { it.str("name") })
        assertTrue(tools.all { "input_schema" in it })
        assertEquals("system", request.str("system"))
    }

    @Test
    fun anthropicParsesTextAndToolUse() {
        val body = """
            {"content":[
              {"type":"text","text":"Here is the idea."},
              {"type":"tool_use","id":"toolu_1","name":"ask_question","input":{"prompt":"Predict?","purpose":"predict"}}
            ],"stop_reason":"tool_use"}
        """

        val reply = AnthropicClient.parseResponse(json, body)

        assertEquals("Here is the idea.", reply.text)
        val parsed = reply.toolCalls.single()
        assertEquals("toolu_1", parsed.id)
        assertEquals(QuestionPurpose.PREDICT, parsed.toQuestion()?.purpose)
    }

    @Test
    fun openAiSendsToolCallsAndToolMessages() {
        val request = OpenAiCompatibleClient.buildRequest("model-x", "system", transcript, TeacherTools.specs)

        val messages = request.getValue("messages").jsonArray.map { it.jsonObject }
        assertEquals(listOf("system", "user", "assistant", "tool"), messages.map { it.str("role") })
        val function = messages[2].getValue("tool_calls").jsonArray.single().jsonObject.getValue("function").jsonObject
        assertEquals("""{"prompt":"Why?"}""", function.str("arguments"))
        assertEquals("call_1", messages[3].str("tool_call_id"))
    }

    @Test
    fun openAiParsesStringArgumentsAndNullContent() {
        val body = """
            {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
              {"id":"call_9","type":"function","function":{"name":"record_proposition","arguments":"{\"statement\":\"Tides follow the moon.\"}"}}
            ]}}]}
        """

        val reply = OpenAiCompatibleClient.parseResponse(json, body)

        assertEquals("", reply.text)
        val arguments = reply.toolCalls.single().arguments
        assertEquals(JsonPrimitive("Tides follow the moon."), arguments["statement"])
    }

    @Test
    fun openAiToleratesMalformedArguments() {
        val body = """{"choices":[{"message":{"tool_calls":[{"id":"c","function":{"name":"set_plan","arguments":"{oops"}}]}}]}"""

        val reply = OpenAiCompatibleClient.parseResponse(json, body)

        assertEquals(JsonObject(emptyMap()), reply.toolCalls.single().arguments)
    }

    @Test
    fun errorMessageUsesProviderMessage() {
        val message = providerErrorMessage(json, HttpStatusCode.Unauthorized, """{"error":{"message":"invalid x-api-key"}}""")

        assertEquals("401 Unauthorized: invalid x-api-key", message)
    }

    @Test
    fun anthropicSkipsEmptyAssistantText() {
        val messages = AnthropicClient.messagesJson(
            listOf(LhMessage.User("hi"), LhMessage.Assistant("", listOf(call)), LhMessage.ToolResult("call_1", "ok")),
        )

        val assistant = messages[1].jsonObject.getValue("content") as JsonArray
        assertEquals(listOf("tool_use"), assistant.map { it.jsonObject.str("type") })
    }
}
