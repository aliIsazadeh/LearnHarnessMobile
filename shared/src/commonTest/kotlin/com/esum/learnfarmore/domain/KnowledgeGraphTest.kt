package com.esum.learnfarmore.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KnowledgeGraphTest {

    @Test
    fun assignsSequentialIdsAndLinksPremises() {
        val (first, a) = KnowledgeGraph().add("A", emptyList(), "s1", 1)
        val (second, b) = first.add("B", listOf(a.id), "s1", 2)

        assertEquals("p1", a.id)
        assertEquals("p2", b.id)
        assertEquals(listOf(b), second.conclusionsOf(a.id))
    }

    @Test
    fun rejectsUnknownPremises() {
        assertFailsWith<IllegalArgumentException> {
            KnowledgeGraph().add("B", listOf("p1"), "s1", 1)
        }
    }
}
