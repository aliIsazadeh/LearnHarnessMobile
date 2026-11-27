package com.esum.learnfarmore.agent

import com.esum.learnfarmore.domain.Confidence
import com.esum.learnfarmore.domain.KnowledgeGraph
import com.esum.learnfarmore.domain.LearnerAnswer
import com.esum.learnfarmore.domain.LhMessage
import com.esum.learnfarmore.domain.StepStatus
import com.esum.learnfarmore.domain.ToolCall
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TeachingAgentTest {

    private val json = Json
    private var now = 1_000L
    private val start = TeachingState(newSession("s1", "Why is the sky blue?", 0), KnowledgeGraph())

    private class ScriptedClient(vararg replies: ModelReply) : ModelClient {
        private val queue = ArrayDeque(replies.toList())
        val requests = mutableListOf<List<LhMessage>>()

        override suspend fun complete(system: String, messages: List<LhMessage>, tools: List<ToolSpec>): ModelReply {
            requests += messages
            return queue.removeFirst()
        }
    }

    private fun agent(client: ModelClient, maxModelCalls: Int = 8) = TeachingAgent(client, { now++ }, maxModelCalls)

    private fun call(id: String, name: String, arguments: JsonObjectBuilder.() -> Unit) =
        ToolCall(id, name, buildJsonObject(arguments))

    private fun question(id: String, prompt: String = "What is light?") = call(id, TeacherTools.ASK_QUESTION) {
        put("prompt", prompt)
        put("purpose", "probe")
    }

    @Test
    fun pausesOnQuestion() = runTest {
        val client = ScriptedClient(ModelReply("Let's see where you are.", listOf(question("q1"))))

        val result = agent(client).run(start) {}

        assertEquals(1, client.requests.size)
        val status = assertIs<SessionStatus.AwaitingAnswer>(result.session.status())
        assertEquals("What is light?", status.question.prompt)
        assertEquals(QuestionPurpose.PROBE, status.question.purpose)
    }

    @Test
    fun executesToolsUntilTeacherYields() = runTest {
        val plan = call("c1", TeacherTools.SET_PLAN) {
            putJsonArray("steps") {
                addJsonObject { put("id", "s1"); put("title", "Light is a mix of colors") }
                addJsonObject {
                    put("id", "s2"); put("title", "Air scatters blue light more")
                    putJsonArray("depends_on") { add("s1") }
                }
            }
        }
        val proposition = call("c2", TeacherTools.RECORD_PROPOSITION) { put("statement", "White light contains all colors.") }
        val client = ScriptedClient(
            ModelReply("", listOf(plan, proposition)),
            ModelReply("Nice work today.", emptyList()),
        )
        val updates = mutableListOf<TeachingState>()

        val result = agent(client).run(start) { updates += it }

        assertEquals(2, client.requests.size)
        assertEquals(2, updates.size)
        assertEquals(listOf("s1", "s2"), result.session.plan.map { it.id })
        assertEquals("p1", result.graph.propositions.single().id)
        assertEquals(SessionStatus.AwaitingLearner, result.session.status())
        val resultsSent = client.requests[1].filterIsInstance<LhMessage.ToolResult>()
        assertEquals(listOf("c1", "c2"), resultsSent.map { it.callId })
        assertTrue(resultsSent.none { it.isError })
    }

    @Test
    fun rejectsUnknownPremises() = runTest {
        val proposition = call("c1", TeacherTools.RECORD_PROPOSITION) {
            put("statement", "Blue scatters most.")
            putJsonArray("premise_ids") { add("p9") }
        }
        val client = ScriptedClient(ModelReply("", listOf(proposition)), ModelReply("Okay.", emptyList()))

        val result = agent(client).run(start) {}

        assertTrue(result.graph.propositions.isEmpty())
        val error = result.session.messages.filterIsInstance<LhMessage.ToolResult>().single()
        assertTrue(error.isError)
        assertTrue("p9" in error.content)
    }

    @Test
    fun showsOnlyTheFirstQuestion() = runTest {
        val client = ScriptedClient(ModelReply("", listOf(question("q1", "First?"), question("q2", "Second?"))))

        val result = agent(client).run(start) {}

        val status = assertIs<SessionStatus.AwaitingAnswer>(result.session.status())
        assertEquals("q1", status.question.callId)
        val rejected = result.session.messages.filterIsInstance<LhMessage.ToolResult>().single()
        assertEquals("q2", rejected.callId)
        assertTrue(rejected.isError)
    }

    @Test
    fun answerResumesTeacher() = runTest {
        val client = ScriptedClient(
            ModelReply("", listOf(question("q1"))),
            ModelReply("Good, let's build on that.", emptyList()),
        )
        val paused = agent(client).run(start) {}
        val pending = assertIs<SessionStatus.AwaitingAnswer>(paused.session.status()).question

        val answered = paused.session.withAnswer(pending, LearnerAnswer("Waves", "Not sure", Confidence.UNSURE), json, now)
        assertEquals(SessionStatus.NeedsTeacher, answered.status())

        val result = agent(client).run(paused.copy(session = answered)) {}

        val sentAnswer = client.requests[1].last()
        assertIs<LhMessage.ToolResult>(sentAnswer)
        assertTrue("UNSURE" in sentAnswer.content)
        assertEquals(SessionStatus.AwaitingLearner, result.session.status())
    }

    @Test
    fun renamesReusedCallIds() = runTest {
        val client = ScriptedClient(
            ModelReply("", listOf(question("call_0", "First?"))),
            ModelReply("", listOf(question("call_0", "Second?"))),
        )
        val first = agent(client).run(start) {}
        val pending = assertIs<SessionStatus.AwaitingAnswer>(first.session.status()).question
        val answered = first.session.withAnswer(pending, LearnerAnswer("A", confidence = Confidence.CONFIDENT), json, now)

        val second = agent(client).run(first.copy(session = answered)) {}

        val next = assertIs<SessionStatus.AwaitingAnswer>(second.session.status()).question
        assertEquals("call_0_2", next.callId)
        assertEquals("Second?", next.prompt)
    }

    @Test
    fun marksStepStatus() = runTest {
        val plan = call("c1", TeacherTools.SET_PLAN) {
            putJsonArray("steps") { addJsonObject { put("id", "s1"); put("title", "Light is a mix of colors") } }
        }
        val active = call("c2", TeacherTools.SET_STEP_STATUS) { put("step_id", "s1"); put("status", "active") }
        val client = ScriptedClient(ModelReply("", listOf(plan, active)), ModelReply("Go on.", emptyList()))

        val result = agent(client).run(start) {}

        assertEquals(StepStatus.ACTIVE, result.session.plan.single().status)
    }

    @Test
    fun emptyReplyFailsWithoutSaving() = runTest {
        val client = ScriptedClient(ModelReply("  ", emptyList()))
        val updates = mutableListOf<TeachingState>()

        assertFailsWith<ModelException> { agent(client).run(start) { updates += it } }

        assertTrue(updates.isEmpty())
    }

    @Test
    fun stopsAfterMaxModelCalls() = runTest {
        val looping = { id: String -> ModelReply("", listOf(call(id, TeacherTools.SET_STEP_STATUS) { put("step_id", "x"); put("status", "done") })) }
        val client = ScriptedClient(looping("c1"), looping("c2"), looping("c3"))

        val result = agent(client, maxModelCalls = 2).run(start) {}

        assertEquals(2, client.requests.size)
        assertEquals(SessionStatus.NeedsTeacher, result.session.status())
    }
}
