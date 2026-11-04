package dev.atomic.kg

import dev.atomic.core.model.KgNode
import java.awt.geom.Point2D
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

object KgNodeLayout {
    const val MIN_SEPARATION = 180.0

    fun assignPositions(
        nodes: List<KgNode>,
        existing: Map<String, Point2D.Double>,
        focusId: String?,
        centerX: Double,
        centerY: Double,
        radius: Double,
    ): Map<String, Point2D.Double> {
        val next = existing.filterKeys { id -> nodes.any { it.id == id } }.toMutableMap()
        val missing = nodes.filter { it.id !in next }
        if (missing.isEmpty()) {
            return next
        }

        if (next.isEmpty()) {
            placeInCircle(missing, next, centerX, centerY, layoutRadius(missing.size, radius))
            resolveOverlaps(next, next.keys)
            return next
        }

        val hub = next[focusId] ?: centroid(next.values)
        placeWithoutOverlap(missing, next, hub, layoutRadius(missing.size, radius * 0.55))
        resolveOverlaps(next, missing.map { it.id }.toSet())
        return next
    }

    fun inverseTransform(
        screenX: Double,
        screenY: Double,
        centerX: Double,
        centerY: Double,
        zoom: Double,
        panX: Double,
        panY: Double,
    ): Point2D.Double =
        Point2D.Double(
            centerX + (screenX - centerX - panX) / zoom,
            centerY + (screenY - centerY - panY) / zoom,
        )

    fun nodesOverlap(a: Point2D.Double, b: Point2D.Double, minSeparation: Double = MIN_SEPARATION): Boolean =
        hypot(a.x - b.x, a.y - b.y) < minSeparation - 0.5

    fun separateAroundPinned(positions: MutableMap<String, Point2D.Double>, pinnedId: String) {
        resolveOverlaps(positions, positions.keys - pinnedId)
    }

    fun separateAll(positions: MutableMap<String, Point2D.Double>) {
        resolveOverlaps(positions, positions.keys.toSet())
    }

    private fun layoutRadius(count: Int, requested: Double): Double {
        val circumference = count * MIN_SEPARATION
        val fitted = circumference / (2.0 * PI)
        return max(requested, max(fitted, MIN_SEPARATION))
    }

    private fun placeInCircle(
        nodes: List<KgNode>,
        target: MutableMap<String, Point2D.Double>,
        centerX: Double,
        centerY: Double,
        radius: Double,
    ) {
        val count = nodes.size.coerceAtLeast(1)
        nodes.forEachIndexed { index, node ->
            val angle = 2.0 * PI * index / count - PI / 2.0
            target[node.id] = Point2D.Double(centerX + cos(angle) * radius, centerY + sin(angle) * radius)
        }
    }

    private fun placeWithoutOverlap(
        nodes: List<KgNode>,
        target: MutableMap<String, Point2D.Double>,
        hub: Point2D.Double,
        startRadius: Double,
    ) {
        val occupiedAngles = target.values.map { atan2(it.y - hub.y, it.x - hub.x) }
        nodes.forEachIndexed { index, node ->
            val preferred = preferredAngle(index, nodes.size, occupiedAngles)
            target[node.id] = firstFreePoint(hub, preferred, startRadius, target.values)
        }
    }

    private fun preferredAngle(index: Int, count: Int, occupied: List<Double>): Double {
        val even = 2.0 * PI * index / count.coerceAtLeast(1) - PI / 2.0
        if (occupied.isEmpty()) return even
        val golden = even + 0.5
        return if (occupied.minOf { angleDelta(it, even) } < occupied.minOf { angleDelta(it, golden) }) {
            golden
        } else {
            even
        }
    }

    private fun angleDelta(a: Double, b: Double): Double {
        var delta = (a - b) % (2.0 * PI)
        if (delta > PI) delta -= 2.0 * PI
        if (delta < -PI) delta += 2.0 * PI
        return kotlin.math.abs(delta)
    }

    private fun firstFreePoint(
        hub: Point2D.Double,
        startAngle: Double,
        startRadius: Double,
        occupied: Collection<Point2D.Double>,
    ): Point2D.Double {
        var radius = max(startRadius, MIN_SEPARATION)
        repeat(12) { ring ->
            val slots = 8 + ring * 4
            repeat(slots) { slot ->
                val angle = startAngle + 2.0 * PI * slot / slots
                val candidate = Point2D.Double(hub.x + cos(angle) * radius, hub.y + sin(angle) * radius)
                if (occupied.none { nodesOverlap(it, candidate) }) {
                    return candidate
                }
            }
            radius += MIN_SEPARATION
        }
        return Point2D.Double(hub.x + cos(startAngle) * radius, hub.y + sin(startAngle) * radius)
    }

    private fun resolveOverlaps(positions: MutableMap<String, Point2D.Double>, movableIds: Set<String>) {
        repeat(80) {
            var moved = false
            val ids = positions.keys.toList()
            for (i in ids.indices) {
                for (j in i + 1 until ids.size) {
                    val leftId = ids[i]
                    val rightId = ids[j]
                    val left = positions.getValue(leftId)
                    val right = positions.getValue(rightId)
                    var dx = right.x - left.x
                    var dy = right.y - left.y
                    if (kotlin.math.abs(dy) < 0.5 && kotlin.math.abs(dx) >= 0.0) {
                        dy = if (j % 2 == 0) 1.0 else -1.0
                    }
                    val distance = hypot(dx, dy).coerceAtLeast(0.001)
                    if (distance >= MIN_SEPARATION) continue
                    val push = (MIN_SEPARATION - distance) / 2.0
                    val nx = dx / distance
                    val ny = dy / distance
                    val leftMovable = leftId in movableIds
                    val rightMovable = rightId in movableIds
                    when {
                        leftMovable && rightMovable -> {
                            positions[leftId] = Point2D.Double(left.x - nx * push, left.y - ny * push)
                            positions[rightId] = Point2D.Double(right.x + nx * push, right.y + ny * push)
                            moved = true
                        }
                        leftMovable -> {
                            positions[leftId] = Point2D.Double(left.x - nx * push * 2, left.y - ny * push * 2)
                            moved = true
                        }
                        rightMovable -> {
                            positions[rightId] = Point2D.Double(right.x + nx * push * 2, right.y + ny * push * 2)
                            moved = true
                        }
                    }
                }
            }
            if (!moved) return
        }
    }

    private fun centroid(points: Collection<Point2D.Double>): Point2D.Double {
        val x = points.sumOf { it.x } / points.size
        val y = points.sumOf { it.y } / points.size
        return Point2D.Double(x, y)
    }
}
