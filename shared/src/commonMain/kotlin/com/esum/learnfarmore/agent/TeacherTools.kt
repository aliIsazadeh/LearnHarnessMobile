package com.esum.learnfarmore.agent

import com.esum.learnfarmore.domain.ToolCall
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object TeacherTools {
    const val ASK_QUESTION = "ask_question"
    const val SET_PLAN = "set_plan"
    const val SET_STEP_STATUS = "set_step_status"
    const val RECORD_PROPOSITION = "record_proposition"

    val specs: List<ToolSpec> = listOf(
        ToolSpec(
            name = ASK_QUESTION,
            description = "Show the learner one question and pause until they answer. The answer comes back as the tool result.",
            parameters = objectSchema(
                required = listOf("prompt", "purpose"),
                "prompt" to stringSchema("The question, written for the learner."),
                "purpose" to stringSchema(
                    "probe: find where understanding ends. predict: ask before explaining. check: verify a step landed.",
                    enum = QuestionPurpose.entries.map { it.wireName },
                ),
                "options" to arraySchema(
                    stringSchema("One answer choice."),
                    "Optional multiple-choice options. Omit for a free-text answer.",
                ),
            ),
        ),
        ToolSpec(
            name = SET_PLAN,
            description = "Replace the lesson plan: the smallest set of steps from what the learner holds to the goal.",
            parameters = objectSchema(
                required = listOf("steps"),
                "steps" to arraySchema(
                    objectSchema(
                        required = listOf("id", "title"),
                        "id" to stringSchema("Short stable id, e.g. s1."),
                        "title" to stringSchema("What the learner will understand after this step."),
                        "depends_on" to arraySchema(stringSchema("Id of a prerequisite step."), "Prerequisite step ids."),
                    ),
                    "Ordered plan steps.",
                ),
            ),
        ),
        ToolSpec(
            name = SET_STEP_STATUS,
            description = "Mark a plan step active when you start it or done when its check succeeds.",
            parameters = objectSchema(
                required = listOf("step_id", "status"),
                "step_id" to stringSchema("Id of the plan step."),
                "status" to stringSchema("New status.", enum = listOf("pending", "active", "done")),
            ),
        ),
        ToolSpec(
            name = RECORD_PROPOSITION,
            description = "Add a proposition the learner now holds to their knowledge graph. Returns its id.",
            parameters = objectSchema(
                required = listOf("statement"),
                "statement" to stringSchema("One self-contained claim, in plain words."),
                "premise_ids" to arraySchema(
                    stringSchema("Id of an existing proposition, e.g. p3."),
                    "Existing propositions this one follows from.",
                ),
            ),
        ),
    )

    private fun stringSchema(description: String, enum: List<String>? = null): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
        if (enum != null) putJsonArray("enum") { enum.forEach { add(it) } }
    }

    private fun arraySchema(items: JsonObject, description: String): JsonObject = buildJsonObject {
        put("type", "array")
        put("description", description)
        put("items", items)
    }

    private fun objectSchema(required: List<String>, vararg properties: Pair<String, JsonObject>): JsonObject =
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") { properties.forEach { (name, schema) -> put(name, schema) } }
            putJsonArray("required") { required.forEach { add(it) } }
        }
}

enum class QuestionPurpose(val wireName: String) {
    PROBE("probe"),
    PREDICT("predict"),
    CHECK("check"),
}

data class Question(
    val callId: String,
    val prompt: String,
    val purpose: QuestionPurpose,
    val options: List<String>,
)

/** Null when the call isn't a well-formed question. */
fun ToolCall.toQuestion(): Question? {
    if (name != TeacherTools.ASK_QUESTION) return null
    val prompt = arguments.string("prompt")?.takeIf { it.isNotBlank() } ?: return null
    val purpose = QuestionPurpose.entries.firstOrNull { it.wireName == arguments.string("purpose") } ?: QuestionPurpose.CHECK
    return Question(id, prompt.trim(), purpose, arguments.strings("options").filter { it.isNotBlank() })
}

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

internal fun JsonObject.objects(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
