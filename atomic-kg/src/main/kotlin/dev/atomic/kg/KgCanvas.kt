package dev.atomic.kg

import dev.atomic.core.model.KgNode
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.geom.Point2D
import javax.swing.JPanel
import kotlin.math.min
import kotlin.math.sqrt

class KgCanvas(
    private val onNodeSelected: (String) -> Unit,
    private val onNodeInspected: (String) -> Unit = {},
) : JPanel() {
    private var graph = KgGraphSnapshot()
    private var zoom = 1.0
    private var panX = 0.0
    private var panY = 0.0
    private var pressedAt: Point? = null
    private var lastDragPoint: Point? = null
    private var dragged = false
    private var selectedId: String? = null
    private var draggingNodeId: String? = null
    private val positions = linkedMapOf<String, Point2D.Double>()

    init {
        preferredSize = Dimension(540, 420)
        background = Color(0xFA, 0xFA, 0xFA)
        val mouseHandler = object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                pressedAt = event.point
                lastDragPoint = event.point
                dragged = false
                val hitId = hitTest(event.point)?.id
                draggingNodeId = hitId
                if (hitId != null) {
                    selectedId = hitId
                    onNodeInspected(hitId)
                    cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                    repaint()
                }
            }

            override fun mouseDragged(event: MouseEvent) {
                val previous = lastDragPoint ?: return
                if (pressedAt?.distance(event.point)?.let { it > 3.0 } == true) dragged = true
                val nodeId = draggingNodeId
                if (nodeId != null) {
                    positions[nodeId] = KgNodeLayout.inverseTransform(
                        event.x.toDouble(),
                        event.y.toDouble(),
                        width / 2.0,
                        height / 2.0,
                        zoom,
                        panX,
                        panY,
                    )
                    KgNodeLayout.separateAroundPinned(positions, nodeId)
                } else {
                    panX += event.x - previous.x
                    panY += event.y - previous.y
                }
                lastDragPoint = event.point
                repaint()
            }

            override fun mouseReleased(event: MouseEvent) {
                cursor = Cursor.getDefaultCursor()
                if (!dragged && event.button == MouseEvent.BUTTON1) {
                    hitTest(event.point)?.let { node ->
                        selectedId = node.id
                        onNodeSelected(node.id)
                        repaint()
                    }
                }
                pressedAt = null
                lastDragPoint = null
                draggingNodeId = null
            }

            override fun mouseMoved(event: MouseEvent) {
                cursor = if (hitTest(event.point) != null) {
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                } else {
                    Cursor.getDefaultCursor()
                }
            }

            override fun mouseWheelMoved(event: MouseWheelEvent) {
                zoom = (zoom * (1.0 - event.preciseWheelRotation * 0.1)).coerceIn(MIN_ZOOM, MAX_ZOOM)
                repaint()
            }
        }
        addMouseListener(mouseHandler)
        addMouseMotionListener(mouseHandler)
        addMouseWheelListener(mouseHandler)
        addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(event: ComponentEvent) {
                    KgNodeLayout.separateAll(positions)
                    repaint()
                }
            },
        )
    }

    fun updateGraph(snapshot: KgGraphSnapshot) {
        graph = snapshot
        selectedId = snapshot.focusId
        relayout()
        repaint()
    }

    fun fitGraph() {
        zoom = 1.0
        panX = 0.0
        panY = 0.0
        positions.clear()
        relayout()
        repaint()
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics.create() as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        if (graph.nodes.isEmpty()) {
            g.color = Color.GRAY
            g.drawString("No graph roots loaded. Run Enrich or search the vault.", 24, 32)
            g.dispose()
            return
        }

        g.stroke = BasicStroke((1.4f * zoom).toFloat().coerceAtLeast(0.8f))
        g.color = Color(0xA0, 0xA0, 0xA0)
        graph.edges.forEach { edge ->
            val from = positions[edge.fromId]?.let(::transform) ?: return@forEach
            val to = positions[edge.toId]?.let(::transform) ?: return@forEach
            g.drawLine(from.x.toInt(), from.y.toInt(), to.x.toInt(), to.y.toInt())
            if (edge.relation.isNotBlank() && zoom >= 0.7) {
                g.color = Color(0x70, 0x70, 0x70)
                g.drawString(edge.relation, ((from.x + to.x) / 2).toInt(), ((from.y + to.y) / 2).toInt())
                g.color = Color(0xA0, 0xA0, 0xA0)
            }
        }

        val nodeWidth = scaledNodeWidth()
        val nodeHeight = scaledNodeHeight()
        val arc = (16 * zoom).toInt().coerceAtLeast(6)
        g.font = g.font.deriveFont((11f * zoom.toFloat()).coerceIn(8f, 16f))
        graph.nodes.forEach { node ->
            val center = positions[node.id]?.let(::transform) ?: return@forEach
            val x = center.x.toInt() - nodeWidth / 2
            val y = center.y.toInt() - nodeHeight / 2
            val selected = node.id == graph.focusId || node.id == selectedId
            g.color = KgNodeColors.fill(node.type)
            g.fillRoundRect(x, y, nodeWidth, nodeHeight, arc, arc)
            g.stroke = BasicStroke(if (selected) (2.6f * zoom).toFloat().coerceAtLeast(1.4f) else (1.4f * zoom).toFloat().coerceAtLeast(0.8f))
            g.color = KgNodeColors.border(selected)
            g.drawRoundRect(x, y, nodeWidth, nodeHeight, arc, arc)
            g.color = Color(0x22, 0x2B, 0x36)
            val metrics = g.fontMetrics
            val maxTextWidth = (nodeWidth - 12).coerceAtLeast(8)
            val label = fitLabel("${node.type}: ${node.name}", metrics, maxTextWidth)
            g.drawString(label, center.x.toInt() - metrics.stringWidth(label) / 2, center.y.toInt() + metrics.ascent / 2 - 2)
        }
        g.dispose()
    }

    private fun relayout() {
        val assigned = KgNodeLayout.assignPositions(
            nodes = graph.nodes,
            existing = positions,
            focusId = graph.focusId ?: selectedId,
            centerX = width.coerceAtLeast(1) / 2.0,
            centerY = height.coerceAtLeast(1) / 2.0,
            radius = min(width, height).coerceAtLeast(1) * 0.36,
        )
        positions.clear()
        positions.putAll(assigned)
    }

    private fun transform(point: Point2D.Double): Point2D.Double {
        val centerX = width / 2.0
        val centerY = height / 2.0
        return Point2D.Double(
            centerX + (point.x - centerX) * zoom + panX,
            centerY + (point.y - centerY) * zoom + panY,
        )
    }

    private fun hitTest(point: Point): KgNode? {
        return graph.nodes.asReversed().firstOrNull { node ->
            val center = positions[node.id]?.let(::transform) ?: return@firstOrNull false
            val dx = center.x - point.x
            val dy = center.y - point.y
            sqrt(dx * dx + dy * dy) <= scaledNodeWidth() / 2.0
        }
    }

    private fun scaledNodeWidth(): Int = (NODE_WIDTH * zoom).toInt().coerceAtLeast(36)

    private fun scaledNodeHeight(): Int = (NODE_HEIGHT * zoom).toInt().coerceAtLeast(18)

    private companion object {
        const val NODE_WIDTH = 150
        const val NODE_HEIGHT = 42
        const val MIN_ZOOM = 0.4
        const val MAX_ZOOM = 3.0

        fun fitLabel(text: String, metrics: java.awt.FontMetrics, maxWidth: Int): String {
            if (metrics.stringWidth(text) <= maxWidth) return text
            val ellipsis = "…"
            var end = text.length
            while (end > 0 && metrics.stringWidth(text.take(end) + ellipsis) > maxWidth) {
                end--
            }
            return if (end == 0) ellipsis else text.take(end) + ellipsis
        }
    }
}
