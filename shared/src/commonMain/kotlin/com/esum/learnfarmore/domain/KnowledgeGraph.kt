package com.esum.learnfarmore.domain

import kotlinx.serialization.Serializable

@Serializable
data class Proposition(
    val id: String,
    val statement: String,
    val premiseIds: List<String> = emptyList(),
    val sessionId: String,
    val createdAt: Long,
)

@Serializable
data class KnowledgeGraph(val propositions: List<Proposition> = emptyList()) {

    fun byId(id: String): Proposition? = propositions.firstOrNull { it.id == id }

    fun conclusionsOf(id: String): List<Proposition> = propositions.filter { id in it.premiseIds }

    fun missingPremises(premiseIds: List<String>): List<String> = premiseIds.filter { byId(it) == null }

    fun add(statement: String, premiseIds: List<String>, sessionId: String, createdAt: Long): Pair<KnowledgeGraph, Proposition> {
        require(statement.isNotBlank()) { "Statement must not be blank" }
        val missing = missingPremises(premiseIds)
        require(missing.isEmpty()) { "Unknown premise ids: ${missing.joinToString()}" }
        val proposition = Proposition(
            id = nextId(),
            statement = statement.trim(),
            premiseIds = premiseIds.distinct(),
            sessionId = sessionId,
            createdAt = createdAt,
        )
        return copy(propositions = propositions + proposition) to proposition
    }

    private fun nextId(): String {
        val max = propositions.maxOfOrNull { it.id.removePrefix(ID_PREFIX).toIntOrNull() ?: 0 } ?: 0
        return "$ID_PREFIX${max + 1}"
    }
}

private const val ID_PREFIX = "p"
