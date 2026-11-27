package com.esum.learnfarmore.ui

import com.esum.learnfarmore.agent.TeacherTools
import com.esum.learnfarmore.agent.newSession
import com.esum.learnfarmore.domain.KnowledgeGraph
import com.esum.learnfarmore.domain.LhMessage
import com.esum.learnfarmore.domain.ToolCall
import com.esum.learnfarmore.ui.graph.layoutGraph
import com.esum.learnfarmore.ui.session.TranscriptItem
import com.esum.learnfarmore.ui.session.transcript
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranscriptAndLayoutTest {

    private val json = Json

    @Test
    fun transcriptPairsAnswersAndHidesRejectedCalls() {
        val ask = ToolCall("q1", TeacherTools.ASK_QUESTION, buildJsonObject { put("prompt", "Why?"); put("purpose", "check") })
        val second = ToolCall("q2", TeacherTools.ASK_QUESTION, buildJsonObject { put("prompt", "Also?") })
        val record = ToolCall("r1", TeacherTools.RECORD_PROPOSITION, buildJsonObject { put("statement", "Ice is less dense.") })
        val session = newSession("s", "Why ice floats", 0).copy(
            messages = listOf(
                LhMessage.User("I want to understand: Why ice floats"),
                LhMessage.Assistant("Think about density.", listOf(record, ask, second)),
                LhMessage.ToolResult("r1", "Recorded as p1."),
                LhMessage.ToolResult("q2", "Not shown.", isError = true),
                LhMessage.ToolResult("q1", """{"answer":"Density","thinking":"","confidence":"CONFIDENT"}"""),
            ),
        )

        val items = session.transcript(json)

        assertEquals(4, items.size)
        assertIs<TranscriptItem.Learner>(items[0])
        assertIs<TranscriptItem.Teacher>(items[1])
        assertEquals("Ice is less dense.", assertIs<TranscriptItem.Recorded>(items[2]).statement)
        assertEquals("Density", assertIs<TranscriptItem.Asked>(items[3]).answer?.answer)
    }

    @Test
    fun pendingQuestionHasNoAnswer() {
        val ask = ToolCall("q1", TeacherTools.ASK_QUESTION, buildJsonObject { put("prompt", "Why?") })
        val session = newSession("s", "g", 0).copy(
            messages = listOf(LhMessage.User("hi"), LhMessage.Assistant("", listOf(ask))),
        )

        assertNull(assertIs<TranscriptItem.Asked>(session.transcript(json).last()).answer)
    }

    @Test
    fun layoutFitsAndSeparatesNodes() {
        var graph = KnowledgeGraph()
        repeat(12) { i ->
            graph = graph.add("Idea $i", if (i == 0) emptyList() else listOf("p$i"), "s", i.toLong()).first
        }

        val positions = layoutGraph(graph)

        assertEquals(12, positions.size)
        assertTrue(positions.values.all { abs(it.x) <= 1.0001f && abs(it.y) <= 1.0001f })
        val points = positions.values.toList()
        val minDistance = points.indices.minOf { i ->
            points.indices.filter { it != i }.minOf { j -> (points[i] - points[j]).getDistance() }
        }
        assertTrue(minDistance > 0.05f, "nodes overlap: $minDistance")
    }
}
