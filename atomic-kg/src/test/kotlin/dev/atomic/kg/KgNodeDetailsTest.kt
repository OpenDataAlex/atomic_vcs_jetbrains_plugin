package dev.atomic.kg

import dev.atomic.core.model.KgEdge
import dev.atomic.core.model.KgNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class KgNodeDetailsTest {
    @Test
    fun `details include properties and both directions of related nodes`() {
        val file = KgNode("file:Main.kt", "file", "Main.kt", Paths.get("/repo/Main.kt"), 12)
        val entity = KgNode("entity:Main.kt:Foo:1", "entity", "Foo")
        val view = KgNode("view:main", "view", "main")
        val snapshot = KgGraphSnapshot(
            nodes = listOf(file, entity, view),
            edges = listOf(
                KgEdge("entity:Main.kt:Foo:1", "file:Main.kt", "DEFINED_IN"),
                KgEdge("view:main", "file:Main.kt", "CONTAINS"),
            ),
            rootIds = setOf("view:main"),
            focusId = "file:Main.kt",
        )

        val details = KgNodeDetails.from(snapshot, "file:Main.kt")!!

        assertEquals("file:Main.kt", details.node.id)
        assertEquals(false, details.isRoot)
        assertTrue(details.properties.any { it.first == "Path" && it.second.contains("Main.kt") })
        assertTrue(details.properties.any { it.first == "Line" && it.second == "12" })
        assertEquals(2, details.related.size)
        assertTrue(details.related.any { it.node.id == "entity:Main.kt:Foo:1" && it.direction == KgRelationDirection.INCOMING })
        assertTrue(details.related.any { it.node.id == "view:main" && it.direction == KgRelationDirection.INCOMING })
    }

    @Test
    fun `unknown node yields no details`() {
        assertNull(KgNodeDetails.from(KgGraphSnapshot(), "missing"))
    }
}
