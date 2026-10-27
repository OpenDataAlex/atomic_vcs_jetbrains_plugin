package dev.atomic.kg

import dev.atomic.core.model.KgEdge
import dev.atomic.core.model.KgNeighborsResult
import dev.atomic.core.model.KgNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class KgGraphStateTest {
    @Test
    fun `neighbor hops merge without duplicating roots or edges`() {
        val state = KgGraphState()
        val root = KgNode("view:main", "view", "main")
        state.setRoots(listOf(root))
        state.focus(KgNode("entity:src/Main.kt:main:1", "entity", "main"))

        val firstHop = KgNeighborsResult(
            centerId = "entity:src/Main.kt:main:1",
            nodes = listOf(root, KgNode("file:src/Main.kt", "file", "Main.kt", Paths.get("/repo/src/Main.kt"))),
            edges = listOf(KgEdge("entity:src/Main.kt:main:1", "file:src/Main.kt", "DEFINED_IN")),
        )
        state.mergeNeighbors(firstHop)
        val merged = state.mergeNeighbors(firstHop)

        assertEquals(3, merged.nodes.size)
        assertEquals(setOf("view:main"), merged.rootIds)
        assertEquals(1, merged.edges.size)
        assertEquals("file:src/Main.kt", merged.nodes.single { it.type == "file" }.id)
    }

    @Test
    fun `root and visible node limits are enforced`() {
        val state = KgGraphState(maxRoots = 2, maxVisibleNodes = 3)
        val roots = (1..4).map { KgNode("view:$it", "view", "View $it") }
        val snapshot = state.setRoots(roots)
        state.focus(KgNode("file:extra", "file", "extra"))
        val capped = state.focus(KgNode("file:overflow", "file", "overflow"))

        assertEquals(listOf("view:1", "view:2"), snapshot.nodes.map(KgNode::id))
        assertEquals(2, snapshot.rootIds.size)
        assertEquals(3, capped.nodes.size)
        assertTrue(capped.nodes.none { it.id == "file:overflow" })
    }
}
