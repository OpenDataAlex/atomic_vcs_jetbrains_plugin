package dev.atomic.kg

import dev.atomic.core.cli.AtomicCli
import dev.atomic.core.cli.AtomicCommandException
import dev.atomic.core.model.KgCodeHit
import dev.atomic.core.model.KgEdge
import dev.atomic.core.model.KgNeighborsResult
import dev.atomic.core.model.KgNode
import java.nio.file.Path

class KgViewModel(private val cli: AtomicCli) {
    private val graph = KgGraphState()

    suspend fun search(repoPath: Path, mode: KgSearchMode, query: String, type: String?): List<KgSearchItem> =
        when (mode) {
            KgSearchMode.SEARCH -> cli.vaultQuerySearch(repoPath, query, type).nodes.map(KgSearchItem::Node)
            KgSearchMode.CODE -> cli.vaultQueryCode(repoPath, query).map(KgSearchItem::CodeHit)
        }

    suspend fun loadRoots(repoPath: Path): KgGraphSnapshot {
        val roots = mutableListOf<KgNode>()
        for (type in ROOT_TYPES) {
            try {
                roots += cli.vaultQuerySearch(repoPath, type, type, ROOT_QUERY_LIMIT).nodes
            } catch (error: AtomicCommandException) {
                if (!isUnsupportedType(error)) throw error
            }
        }
        return graph.setRoots(roots)
    }

    suspend fun focusNode(repoPath: Path, node: KgNode): KgGraphSnapshot {
        graph.focus(node)
        return expandNode(repoPath, node.id)
    }

    suspend fun focusNode(repoPath: Path, nodeId: String): KgGraphSnapshot {
        val node = graph.node(nodeId) ?: return graph.snapshot()
        graph.focus(node)
        return expandNode(repoPath, nodeId)
    }

    suspend fun expandNode(repoPath: Path, nodeId: String): KgGraphSnapshot {
        if (!graph.canExpand(nodeId)) return graph.snapshot()
        val neighbors = cli.vaultQueryNeighbors(repoPath, nodeId)
        return graph.mergeNeighbors(neighbors)
    }

    fun clearGraph(): KgGraphSnapshot = graph.setRoots(emptyList())

    suspend fun focusFromEditor(repoPath: Path, request: KgEditorFocusRequest): KgEditorFocusResult {
        val choice = KgEditorFocus.choose(request)
            ?: return KgEditorFocusResult(query = "", items = emptyList(), graph = graph.snapshot())
        val items = mutableListOf<KgSearchItem>()
        when (choice.kind) {
            KgEditorQueryKind.SEARCH_AND_CODE -> {
                val nodes = cli.vaultQuerySearch(repoPath, choice.query).nodes
                nodes.forEach { graph.focus(it) }
                items += nodes.map(KgSearchItem::Node)
                items += cli.vaultQueryCode(repoPath, choice.query).map(KgSearchItem::CodeHit)
                nodes.firstOrNull()?.let { expandNode(repoPath, it.id) }
            }
            KgEditorQueryKind.CODE_IN_FILE -> {
                items += cli.vaultQueryCode(repoPath, choice.query, choice.pathGlob).map(KgSearchItem::CodeHit)
            }
            KgEditorQueryKind.FILE_ENTITIES -> {
                val file = request.filePath ?: return KgEditorFocusResult(choice.query, emptyList(), graph.snapshot())
                val nodes = cli.vaultQueryEntities(repoPath, file).nodes
                nodes.forEach { graph.focus(it) }
                items += nodes.map(KgSearchItem::Node)
                nodes.take(MAX_EDITOR_EXPAND).forEach { expandNode(repoPath, it.id) }
            }
        }
        return KgEditorFocusResult(choice.query, items, graph.snapshot(), choice)
    }

    private fun isUnsupportedType(error: AtomicCommandException): Boolean {
        val output = "${error.stderr} ${error.message}".lowercase()
        val invalid = listOf("unknown", "unsupported", "invalid", "unexpected argument", "possible values")
            .any(output::contains)
        val mentionsFilter = "kind" in output || "type" in output || "-t" in output || "--kind" in output
        return invalid && mentionsFilter
    }

    private companion object {
        const val ROOT_QUERY_LIMIT = 15
        const val MAX_EDITOR_EXPAND = 5
        val ROOT_TYPES = listOf("view", "goal", "intent", "change")
    }
}

data class KgGraphSnapshot(
    val nodes: List<KgNode> = emptyList(),
    val edges: List<KgEdge> = emptyList(),
    val rootIds: Set<String> = emptySet(),
    val focusId: String? = null,
)

class KgGraphState(
    private val maxRoots: Int = MAX_ROOTS,
    private val maxVisibleNodes: Int = MAX_VISIBLE_NODES,
) {
    private val nodes = linkedMapOf<String, KgNode>()
    private val edges = linkedSetOf<KgEdge>()
    private val rootIds = linkedSetOf<String>()
    private var focusId: String? = null

    fun setRoots(roots: List<KgNode>): KgGraphSnapshot {
        nodes.clear()
        edges.clear()
        rootIds.clear()
        focusId = null
        roots.distinctBy(KgNode::id).take(minOf(maxRoots, maxVisibleNodes)).forEach { root ->
            nodes[root.id] = root
            rootIds.add(root.id)
        }
        return snapshot()
    }

    fun focus(node: KgNode): KgGraphSnapshot {
        addNode(node)
        if (nodes.containsKey(node.id)) focusId = node.id
        return snapshot()
    }

    fun canExpand(nodeId: String): Boolean = nodeId in nodes

    fun node(nodeId: String): KgNode? = nodes[nodeId]

    fun mergeNeighbors(neighbors: KgNeighborsResult): KgGraphSnapshot {
        neighbors.nodes.forEach(::addNode)
        neighbors.edges
            .filter { it.fromId in nodes && it.toId in nodes }
            .forEach(edges::add)
        return snapshot()
    }

    fun snapshot(): KgGraphSnapshot = KgGraphSnapshot(
        nodes = nodes.values.toList(),
        edges = edges.toList(),
        rootIds = rootIds.toSet(),
        focusId = focusId,
    )

    private fun addNode(node: KgNode) {
        val existing = nodes[node.id]
        if (existing != null) {
            nodes[node.id] = existing.copy(
                type = existing.type.ifBlank { node.type },
                name = existing.name.ifBlank { node.name },
                path = existing.path ?: node.path,
                line = existing.line ?: node.line,
            )
        } else if (nodes.size < maxVisibleNodes) {
            nodes[node.id] = node
        }
    }

    private companion object {
        const val MAX_ROOTS = 50
        const val MAX_VISIBLE_NODES = 100
    }
}

sealed interface KgSearchItem {
    val path: Path?
    val line: Int?

    data class Node(val value: KgNode) : KgSearchItem {
        override val path: Path? get() = value.path
        override val line: Int? get() = value.line

        override fun toString(): String = "[${value.type}] ${value.name} (${value.id})"
    }

    data class CodeHit(val value: KgCodeHit) : KgSearchItem {
        override val path: Path get() = value.path
        override val line: Int? get() = value.line

        override fun toString(): String = "${value.path}${value.line?.let { ":$it" }.orEmpty()}  ${value.snippet}"
    }
}

data class KgEditorFocusResult(
    val query: String,
    val items: List<KgSearchItem>,
    val graph: KgGraphSnapshot,
    val choice: KgEditorQueryChoice? = null,
)

enum class KgSearchMode {
    SEARCH,
    CODE,
    ;

    override fun toString(): String = when (this) {
        SEARCH -> "Search"
        CODE -> "Code"
    }
}
