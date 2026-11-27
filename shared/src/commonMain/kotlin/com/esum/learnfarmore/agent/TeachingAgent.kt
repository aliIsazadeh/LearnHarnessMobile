package com.esum.learnfarmore.agent

import com.esum.learnfarmore.domain.KnowledgeGraph
import com.esum.learnfarmore.domain.LhMessage
import com.esum.learnfarmore.domain.PlanStep
import com.esum.learnfarmore.domain.Session
import com.esum.learnfarmore.domain.StepStatus
import com.esum.learnfarmore.domain.ToolCall

data class TeachingState(val session: Session, val graph: KnowledgeGraph)

/**
 * Runs the teacher until it needs the learner: a pending question, a reply without tool calls,
 * or [maxModelCalls] model calls in one turn. [onUpdate] receives every intermediate state so
 * callers can persist progress before the next model call.
 */
class TeachingAgent(
    private val client: ModelClient,
    private val clock: () -> Long,
    private val maxModelCalls: Int = 8,
) {

    suspend fun run(initial: TeachingState, onUpdate: suspend (TeachingState) -> Unit): TeachingState {
        var state = initial
        repeat(maxModelCalls) {
            if (state.session.status() != SessionStatus.NeedsTeacher) return state
            val reply = client.complete(
                system = TeachingPrompt.system(state.session, state.graph),
                messages = state.session.messages,
                tools = TeacherTools.specs,
            )
            if (reply.text.isBlank() && reply.toolCalls.isEmpty()) {
                throw ModelException("The model returned an empty reply.")
            }
            state = apply(state, reply)
            onUpdate(state)
        }
        return state
    }

    internal fun apply(state: TeachingState, rawReply: ModelReply): TeachingState {
        val reply = withUniqueCallIds(rawReply, state.session.messages)
        var plan = state.session.plan
        var graph = state.graph
        var questionPending = false
        val results = mutableListOf<LhMessage.ToolResult>()

        reply.toolCalls.forEach { call ->
            if (call.toQuestion() != null) {
                if (questionPending) {
                    results += LhMessage.ToolResult(call.id, "Not shown. Ask one question at a time and wait for its answer.", isError = true)
                } else {
                    questionPending = true
                }
                return@forEach
            }
            val outcome = when (call.name) {
                TeacherTools.SET_PLAN -> setPlan(call, plan).also { plan = it.plan ?: plan }
                TeacherTools.SET_STEP_STATUS -> setStepStatus(call, plan).also { plan = it.plan ?: plan }
                TeacherTools.RECORD_PROPOSITION -> recordProposition(call, graph, state.session.id).also { graph = it.graph ?: graph }
                TeacherTools.ASK_QUESTION -> Outcome.error("A question needs a non-empty prompt.")
                else -> Outcome.error("Unknown tool: ${call.name}")
            }
            results += LhMessage.ToolResult(call.id, outcome.message, outcome.isError)
        }

        val session = state.session.copy(
            messages = state.session.messages + LhMessage.Assistant(reply.text, reply.toolCalls) + results,
            plan = plan,
            updatedAt = clock(),
        )
        return TeachingState(session, graph)
    }

    /** Answers are matched to questions by call id, so ids must be unique across the whole session. */
    private fun withUniqueCallIds(reply: ModelReply, history: List<LhMessage>): ModelReply {
        val used = history.filterIsInstance<LhMessage.Assistant>().flatMap { it.toolCalls }.mapTo(HashSet()) { it.id }
        val calls = reply.toolCalls.map { call ->
            val base = call.id.ifBlank { "call" }
            var id = base
            var suffix = 2
            while (id in used) id = "${base}_${suffix++}"
            used += id
            if (id == call.id) call else call.copy(id = id)
        }
        return reply.copy(toolCalls = calls)
    }

    private fun setPlan(call: ToolCall, current: List<PlanStep>): Outcome {
        val steps = call.arguments.objects("steps").map { step ->
            val id = step.string("id")?.trim().orEmpty()
            PlanStep(
                id = id,
                title = step.string("title")?.trim().orEmpty(),
                dependsOn = step.strings("depends_on"),
                status = current.firstOrNull { it.id == id }?.status ?: StepStatus.PENDING,
            )
        }
        val ids = steps.map { it.id }
        return when {
            steps.isEmpty() -> Outcome.error("The plan needs at least one step.")
            steps.any { it.id.isEmpty() || it.title.isEmpty() } -> Outcome.error("Every step needs an id and a title.")
            ids.toSet().size != ids.size -> Outcome.error("Step ids must be unique.")
            steps.any { step -> step.dependsOn.any { it !in ids } } -> Outcome.error("depends_on may only reference ids in this plan.")
            else -> Outcome("Plan saved with ${steps.size} steps.", plan = steps)
        }
    }

    private fun setStepStatus(call: ToolCall, current: List<PlanStep>): Outcome {
        val id = call.arguments.string("step_id")
        val status = StepStatus.entries.firstOrNull { it.name.equals(call.arguments.string("status"), ignoreCase = true) }
            ?: return Outcome.error("status must be pending, active, or done.")
        if (current.none { it.id == id }) return Outcome.error("No plan step with id $id.")
        return Outcome(
            "Step $id is ${status.name.lowercase()}.",
            plan = current.map { if (it.id == id) it.copy(status = status) else it },
        )
    }

    private fun recordProposition(call: ToolCall, graph: KnowledgeGraph, sessionId: String): Outcome {
        val statement = call.arguments.string("statement")?.trim()
        if (statement.isNullOrEmpty()) return Outcome.error("statement is required.")
        val premiseIds = call.arguments.strings("premise_ids")
        val missing = graph.missingPremises(premiseIds)
        if (missing.isNotEmpty()) return Outcome.error("Unknown premise ids: ${missing.joinToString()}. Use ids listed in the knowledge graph.")
        val (updated, proposition) = graph.add(statement, premiseIds, sessionId, clock())
        return Outcome("Recorded as ${proposition.id}.", graph = updated)
    }

    private data class Outcome(
        val message: String,
        val isError: Boolean = false,
        val plan: List<PlanStep>? = null,
        val graph: KnowledgeGraph? = null,
    ) {
        companion object {
            fun error(message: String) = Outcome(message, isError = true)
        }
    }
}
