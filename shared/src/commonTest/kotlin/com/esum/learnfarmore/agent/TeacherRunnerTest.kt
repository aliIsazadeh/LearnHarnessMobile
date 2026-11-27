package com.esum.learnfarmore.agent

import com.esum.learnfarmore.data.GraphRepository
import com.esum.learnfarmore.data.JsonFileStore
import com.esum.learnfarmore.data.SecretStore
import com.esum.learnfarmore.data.SessionRepository
import com.esum.learnfarmore.data.SettingsRepository
import com.esum.learnfarmore.domain.LhMessage
import com.esum.learnfarmore.domain.ToolCall
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class TeacherRunnerTest {

    private class MemorySecrets(vararg entries: Pair<String, String>) : SecretStore {
        private val values = mutableMapOf(*entries)
        override fun get(name: String) = values[name]
        override fun put(name: String, value: String) { values[name] = value }
        override fun remove(name: String) { values.remove(name) }
    }

    private class RecordingClient : ModelClient {
        var calls = 0
        override suspend fun complete(system: String, messages: List<LhMessage>, tools: List<ToolSpec>): ModelReply {
            calls++
            if (messages.last() is LhMessage.ToolResult) return ModelReply("Noted.", emptyList())
            val record = ToolCall("r1", TeacherTools.RECORD_PROPOSITION, buildJsonObject { put("statement", "Fact $calls") })
            return ModelReply("", listOf(record))
        }
    }

    private val store = JsonFileStore(Path(SystemTemporaryDirectory, "lh-runner-${Uuid.random()}"), Json)
    private val sessions = SessionRepository(store)
    private val graph = GraphRepository(store)
    private val client = RecordingClient()

    private fun runner(secrets: SecretStore = MemorySecrets("api_key_anthropic" to "key")) =
        TeacherRunner(sessions, graph, SettingsRepository(store, secrets), { _, _ -> client }, { 1L })

    private suspend fun TeacherRunner.awaitIdle(vararg ids: String) =
        states.first { current -> ids.all { current[it]?.running == false } }

    @Test
    fun concurrentSessionsKeepEveryProposition() = runTest {
        sessions.save(newSession("a", "Why is the sky blue?", 0))
        sessions.save(newSession("b", "Why do magnets attract?", 0))
        val runner = runner()

        runner.start("a")
        runner.start("b")
        val states = runner.awaitIdle("a", "b")

        assertTrue(states.values.none { it.error != null })
        assertEquals(listOf("p1", "p2"), graph.graph.value.propositions.map { it.id })
        assertEquals(setOf("a", "b"), graph.graph.value.propositions.map { it.sessionId }.toSet())
    }

    @Test
    fun savesLearnerInputBeforeTeaching() = runTest {
        sessions.save(newSession("a", "Why is the sky blue?", 0))
        val runner = runner()

        runner.start("a") { it.withLearnerMessage("Is it the ocean?", 2L) }
        runner.awaitIdle("a")

        val messages = sessions.get("a")!!.messages
        assertEquals("Is it the ocean?", (messages[1] as LhMessage.User).text)
        assertEquals(SessionStatus.AwaitingLearner, sessions.get("a")!!.status())
    }

    @Test
    fun missingKeyIsReportedWithoutCallingTheModel() = runTest {
        sessions.save(newSession("a", "Why is the sky blue?", 0))
        val runner = runner(MemorySecrets())

        runner.start("a")
        val state = runner.awaitIdle("a").getValue("a")

        assertTrue(state.missingKey)
        assertEquals(0, client.calls)
    }
}
