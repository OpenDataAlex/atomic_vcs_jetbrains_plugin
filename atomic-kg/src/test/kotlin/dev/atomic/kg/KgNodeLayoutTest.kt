package dev.atomic.kg

import dev.atomic.core.model.KgNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.geom.Point2D

class KgNodeLayoutTest {
    @Test
    fun `existing node positions are preserved when new neighbors are placed`() {
        val root = KgNode("view:main", "view", "main")
        val existing = mapOf("view:main" to Point2D.Double(40.0, 80.0))
        val neighbor = KgNode("file:Main.kt", "file", "Main.kt")

        val assigned = KgNodeLayout.assignPositions(
            nodes = listOf(root, neighbor),
            existing = existing,
            focusId = "view:main",
            centerX = 200.0,
            centerY = 200.0,
            radius = 100.0,
        )

        assertEquals(40.0, assigned.getValue("view:main").x)
        assertEquals(80.0, assigned.getValue("view:main").y)
        assertTrue(assigned.containsKey("file:Main.kt"))
        assertNotEquals(assigned.getValue("view:main"), assigned.getValue("file:Main.kt"))
        assertTrue(!KgNodeLayout.nodesOverlap(assigned.getValue("view:main"), assigned.getValue("file:Main.kt")))
    }

    @Test
    fun `new neighbors are placed without overlapping each other or the hub`() {
        val hub = KgNode("view:main", "view", "main")
        val neighbors = (1..8).map { KgNode("file:$it.kt", "file", "$it.kt") }
        val assigned = KgNodeLayout.assignPositions(
            nodes = listOf(hub) + neighbors,
            existing = mapOf("view:main" to Point2D.Double(200.0, 200.0)),
            focusId = "view:main",
            centerX = 200.0,
            centerY = 200.0,
            radius = 80.0,
        )

        val points = assigned.values.toList()
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                assertTrue(!KgNodeLayout.nodesOverlap(points[i], points[j]), "nodes $i and $j overlap")
            }
        }
        assertEquals(200.0, assigned.getValue("view:main").x)
        assertEquals(200.0, assigned.getValue("view:main").y)
    }

    @Test
    fun `initial roots are spaced far enough apart to avoid overlap`() {
        val roots = (1..6).map { KgNode("view:$it", "view", "v$it") }
        val assigned = KgNodeLayout.assignPositions(
            nodes = roots,
            existing = emptyMap(),
            focusId = null,
            centerX = 300.0,
            centerY = 300.0,
            radius = 40.0,
        )
        val points = assigned.values.toList()
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                assertTrue(!KgNodeLayout.nodesOverlap(points[i], points[j]))
            }
        }
    }

    @Test
    fun `dragging a node pushes overlapping neighbors aside without moving the pinned node`() {
        val positions = mutableMapOf(
            "a" to Point2D.Double(0.0, 0.0),
            "b" to Point2D.Double(10.0, 0.0),
            "c" to Point2D.Double(20.0, 0.0),
        )

        KgNodeLayout.separateAroundPinned(positions, "a")

        assertEquals(0.0, positions.getValue("a").x)
        assertEquals(0.0, positions.getValue("a").y)
        assertTrue(!KgNodeLayout.nodesOverlap(positions.getValue("a"), positions.getValue("b")))
        assertTrue(!KgNodeLayout.nodesOverlap(positions.getValue("a"), positions.getValue("c")))
        assertTrue(!KgNodeLayout.nodesOverlap(positions.getValue("b"), positions.getValue("c")))
    }

    @Test
    fun `separate all resolves a piled cluster after a canvas resize`() {
        val positions = mutableMapOf(
            "a" to Point2D.Double(0.0, 0.0),
            "b" to Point2D.Double(5.0, 0.0),
            "c" to Point2D.Double(0.0, 5.0),
        )

        KgNodeLayout.separateAll(positions)

        assertTrue(!KgNodeLayout.nodesOverlap(positions.getValue("a"), positions.getValue("b")))
        assertTrue(!KgNodeLayout.nodesOverlap(positions.getValue("a"), positions.getValue("c")))
        assertTrue(!KgNodeLayout.nodesOverlap(positions.getValue("b"), positions.getValue("c")))
    }

    @Test
    fun `inverse transform undoes pan and zoom around the canvas center`() {
        val layout = KgNodeLayout.inverseTransform(
            screenX = 130.0,
            screenY = 90.0,
            centerX = 100.0,
            centerY = 100.0,
            zoom = 2.0,
            panX = 10.0,
            panY = -20.0,
        )
        assertEquals(110.0, layout.x, 0.0001)
        assertEquals(105.0, layout.y, 0.0001)
    }
}
