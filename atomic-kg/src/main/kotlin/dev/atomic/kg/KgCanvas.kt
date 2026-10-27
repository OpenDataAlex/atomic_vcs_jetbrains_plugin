package dev.atomic.kg

import dev.atomic.core.model.KgNode
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.geom.Point2D
import javax.swing.JPanel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class KgCanvas(private val onNodeSelected: (String) -> Unit) : JPanel() {
    private var graph = KgGraphSnapshot()
    private var zoom = 1.0
    private var panX = 0.0
    private var panY = 0.0
    private var pressedAt: Point? = null
    private var lastDragPoint: Point? = null
    private var dragged = false
    private var selectedId: String? = null

    init {
        preferredSize = Dimension(540, 420)
        background = Color(0xFA, 0xFA, 0xFA)
        val mouseHandler = object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                pressedAt = event.point
                lastDragPoint = event.point
                dragged = false
            }

            override fun mouseDragged(event: MouseEvent) {
                val previous = lastDragPoint ?: return
                if (pressedAt?.distance(event.point)?.let { it > 3.0 } == true) dragged = true
                panX += event.x - previous.x
                panY += event.y - previous.y
                lastDragPoint = event.point
                repaint()
            }

            override fun mouseReleased(event: MouseEvent) {
                if (!dragged && event.button == MouseEvent.BUTTON1) {
                    hitTest(event.point)?.let { node ->
                        selectedId = node.id
                        onNodeSelected(node.id)
                        repaint()
                    }
                }
                pressedAt = null
                lastDragPoint = null
            }

            override fun mouseWheelMoved(event: MouseWheelEvent) {
                zoom = (zoom * (1.0 - event.preciseWheelRotation * 0.1)).coerceIn(MIN_ZOOM, MAX_ZOOM)
                repaint()
            }
        }
        addMouseListener(mouseHandler)
        addMouseMotionListener(mouseHandler)
        addMouseWheelListener(mouseHandler)
    }

    fun updateGraph(snapshot: KgGraphSnapshot) {
        graph = snapshot
        selectedId = snapshot.focusId
        repaint()
    }

    fun fitGraph() {
        zoom = 1.0
        panX = 0.0
        panY = 0.0
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

        val positions = layoutPositions(graph.nodes)
        g.stroke = BasicStroke(1.4f)
        g.color = Color(0xA0, 0xA0, 0xA0)
        graph.edges.forEach { edge ->
            val from = positions[edge.fromId]?.let(::transform) ?: return@forEach
            val to = positions[edge.toId]?.let(::transform) ?: return@forEach
            g.drawLine(from.x.toInt(), from.y.toInt(), to.x.toInt(), to.y.toInt())
            if (edge.relation.isNotBlank()) {
                g.color = Color(0x70, 0x70, 0x70)
                g.drawString(edge.relation, ((from.x + to.x) / 2).toInt(), ((from.y + to.y) / 2).toInt())
                g.color = Color(0xA0, 0xA0, 0xA0)
            }
        }

        graph.nodes.forEach { node ->
            val center = positions[node.id]?.let(::transform) ?: return@forEach
            val width = NODE_WIDTH
            val height = NODE_HEIGHT
            val x = center.x.toInt() - width / 2
            val y = center.y.toInt() - height / 2
            g.color = when {
                node.id == graph.focusId || node.id == selectedId -> Color(0xB9, 0xD8, 0xFF)
                node.id in graph.rootIds -> Color(0xE2, 0xED, 0xFA)
                else -> Color.WHITE
            }
            g.fillRoundRect(x, y, width, height, 16, 16)
            g.color = Color(0x55, 0x6B, 0x82)
            g.drawRoundRect(x, y, width, height, 16, 16)
            g.color = Color(0x22, 0x2B, 0x36)
            val label = "${node.type}: ${node.name}".take(MAX_LABEL_LENGTH)
            val metrics = g.fontMetrics
            g.drawString(label, center.x.toInt() - metrics.stringWidth(label) / 2, center.y.toInt() + metrics.ascent / 2 - 2)
        }
        g.dispose()
    }

    private fun layoutPositions(nodes: List<KgNode>): Map<String, Point2D.Double> {
        val radius = min(width, height).coerceAtLeast(1) * 0.36
        val centerX = width / 2.0
        val centerY = height / 2.0
        return nodes
            .mapIndexed { index, node ->
                val angle = 2.0 * PI * index / nodes.size
                node.id to Point2D.Double(centerX + cos(angle) * radius, centerY + sin(angle) * radius)
            }.toMap()
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
        val positions = layoutPositions(graph.nodes)
        return graph.nodes.asReversed().firstOrNull { node ->
            val center = positions[node.id]?.let(::transform) ?: return@firstOrNull false
            val dx = center.x - point.x
            val dy = center.y - point.y
            sqrt(dx * dx + dy * dy) <= NODE_WIDTH / 2.0
        }
    }

    private companion object {
        const val NODE_WIDTH = 150
        const val NODE_HEIGHT = 42
        const val MAX_LABEL_LENGTH = 24
        const val MIN_ZOOM = 0.4
        const val MAX_ZOOM = 3.0
    }
}
