package dev.atomic.kg

import dev.atomic.core.model.KgNode
import java.nio.file.Path

data class KgRelatedNode(
    val node: KgNode,
    val relation: String,
    val direction: KgRelationDirection,
) {
    val label: String
        get() = when (direction) {
            KgRelationDirection.OUTGOING -> "$relation → ${node.name}"
            KgRelationDirection.INCOMING -> "${node.name} → $relation"
        }
}

enum class KgRelationDirection {
    OUTGOING,
    INCOMING,
}

data class KgNodeDetails(
    val node: KgNode,
    val isRoot: Boolean,
    val properties: List<Pair<String, String>>,
    val related: List<KgRelatedNode>,
) {
    companion object {
        fun from(snapshot: KgGraphSnapshot, nodeId: String?): KgNodeDetails? {
            val node = snapshot.nodes.firstOrNull { it.id == nodeId } ?: return null
            val related = snapshot.edges
                .mapNotNull { edge ->
                    when (node.id) {
                        edge.fromId -> snapshot.nodes.firstOrNull { it.id == edge.toId }?.let {
                            KgRelatedNode(it, edge.relation.ifBlank { "related" }, KgRelationDirection.OUTGOING)
                        }
                        edge.toId -> snapshot.nodes.firstOrNull { it.id == edge.fromId }?.let {
                            KgRelatedNode(it, edge.relation.ifBlank { "related" }, KgRelationDirection.INCOMING)
                        }
                        else -> null
                    }
                }.distinctBy { Triple(it.node.id, it.relation, it.direction) }
            return KgNodeDetails(
                node = node,
                isRoot = node.id in snapshot.rootIds,
                properties = propertiesOf(node, node.id in snapshot.rootIds, related.size),
                related = related,
            )
        }

        private fun propertiesOf(node: KgNode, isRoot: Boolean, relatedCount: Int): List<Pair<String, String>> =
            buildList {
                add("Name" to node.name.ifBlank { node.id })
                add("Type" to node.type.ifBlank { "unknown" })
                add("ID" to node.id)
                node.path?.let { add("Path" to formatPath(it)) }
                node.line?.let { add("Line" to it.toString()) }
                add("Role" to if (isRoot) "Top-level root" else "Expanded neighbor")
                add("Related nodes" to relatedCount.toString())
            }

        private fun formatPath(path: Path): String = path.toString()
    }
}
