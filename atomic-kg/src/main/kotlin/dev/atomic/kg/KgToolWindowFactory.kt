package dev.atomic.kg

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import dev.atomic.core.cli.AtomicCliImpl
import dev.atomic.core.model.KgNode
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSplitPane

class KgToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = KgToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

internal class KgToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val cli = AtomicCliImpl()
    private val viewModel = KgViewModel(cli)
    private val searchPanel = KgSearchPanel(::search, ::navigate, ::focus)
    private val graphCanvas = KgCanvas(::expandNode, ::inspectNode)
    private val detailsPanel = KgDetailsPanel(::navigate, ::focusRelated)
    private var currentGraph = KgGraphSnapshot()
    private val refreshButton = JButton("Refresh")
    private val enrichButton = JButton("Enrich")
    private val fitGraphButton = JButton("Fit Graph")
    private var repositoryAvailable = false
    private var isBusy = false

    init {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT))
        toolbar.add(refreshButton)
        toolbar.add(enrichButton)
        toolbar.add(fitGraphButton)
        add(toolbar, BorderLayout.NORTH)
        val leftPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, searchPanel, detailsPanel)
        leftPane.resizeWeight = 0.55
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPane, graphCanvas)
        splitPane.resizeWeight = 0.32
        add(splitPane, BorderLayout.CENTER)

        refreshButton.addActionListener { checkRepository() }
        enrichButton.addActionListener { enrich() }
        fitGraphButton.addActionListener { graphCanvas.fitGraph() }
        updateButtons()
        checkRepository()
    }

    private fun checkRepository() {
        val root = project.basePath?.let(Path::of)
        if (root == null) {
            repositoryAvailable = false
            searchPanel.showMessage("Open a project directory to use the Atomic knowledge graph.")
            updateButtons()
            return
        }

        startTask("Checking Atomic repository", "Checking repository…", { cli.isRepository(root) }) { isRepository ->
            repositoryAvailable = isRepository
            searchPanel.showMessage(
                if (isRepository) {
                    "Atomic repository detected. Loading top-level knowledge graph nodes…"
                } else {
                    NOT_AN_ATOMIC_REPOSITORY_MESSAGE
                },
            )
            updateButtons()
            if (isRepository) {
                loadRoots(root)
            } else {
                showGraph(viewModel.clearGraph())
            }
        }
    }

    private fun enrich() {
        val root = project.basePath?.let(Path::of) ?: return
        if (!repositoryAvailable || isBusy) return

        startTask("Enriching Atomic knowledge graph", "Enriching knowledge graph…", { cli.vaultQueryEnrich(root) }) {
            loadRoots(root)
        }
    }

    private fun loadRoots(root: Path) {
        startTask("Loading Atomic knowledge graph roots", "Loading top-level nodes…", { viewModel.loadRoots(root) }) { graph ->
            showGraph(graph)
            searchPanel.showMessage(
                if (graph.nodes.isEmpty()) {
                    "No top-level KG roots found. Run Enrich to populate the graph."
                } else {
                    "${graph.rootIds.size} top-level node(s) loaded. Select a node to expand its neighbors."
                },
            )
        }
    }

    private fun search(mode: KgSearchMode, query: String, type: String?) {
        val root = project.basePath?.let(Path::of)
        if (root == null) {
            searchPanel.showMessage("Open a project directory to search the Atomic vault.")
            return
        }
        if (!repositoryAvailable) {
            searchPanel.showMessage(NOT_AN_ATOMIC_REPOSITORY_MESSAGE)
            return
        }
        startTask("Searching Atomic knowledge graph", "Searching…", { viewModel.search(root, mode, query, type) }) { results ->
            searchPanel.showResults(results)
        }
    }

    private fun focus(node: KgNode) {
        val root = project.basePath?.let(Path::of) ?: return
        if (!repositoryAvailable) return
        startTask("Expanding Atomic node", "Loading neighbors for ${node.name}…", { viewModel.focusNode(root, node) }) { graph ->
            showGraph(graph, node.id)
            searchPanel.showMessage("Expanded neighbors for ${node.name}. Inspect related nodes in the details pane.")
        }
    }

    fun focusFromEditor(request: KgEditorFocusRequest) {
        val root = project.basePath?.let(Path::of)
        if (root == null) {
            searchPanel.showMessage("Open a project directory to search the Atomic vault.")
            return
        }
        if (!repositoryAvailable) {
            searchPanel.showMessage(NOT_AN_ATOMIC_REPOSITORY_MESSAGE)
            return
        }
        startTask("Searching Atomic vault from editor", "Searching from editor…", { viewModel.focusFromEditor(root, request) }) { result ->
            val mode = when (result.choice?.kind) {
                KgEditorQueryKind.CODE_IN_FILE -> KgSearchMode.CODE
                else -> KgSearchMode.SEARCH
            }
            searchPanel.setQuery(result.query, mode)
            searchPanel.showResults(result.items)
            showGraph(result.graph)
        }
    }

    private fun expandNode(nodeId: String) {
        val root = project.basePath?.let(Path::of) ?: return
        if (!repositoryAvailable || isBusy) return
        startTask("Expanding Atomic node", "Loading node neighbors…", { viewModel.focusNode(root, nodeId) }) { graph ->
            showGraph(graph, nodeId)
            searchPanel.showMessage("Node neighbors loaded. Related nodes are listed in the details pane.")
        }
    }

    private fun focusRelated(related: KgRelatedNode) {
        focus(related.node)
    }

    private fun inspectNode(nodeId: String) {
        detailsPanel.showDetails(KgNodeDetails.from(currentGraph, nodeId))
    }

    private fun showGraph(graph: KgGraphSnapshot, selectedId: String? = graph.focusId) {
        currentGraph = graph
        graphCanvas.updateGraph(graph)
        detailsPanel.showDetails(KgNodeDetails.from(graph, selectedId))
    }

    private fun navigate(item: KgSearchItem) {
        val path = item.path ?: return
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toAbsolutePath().toString()) ?: return
        val line = item.line?.let { (it - 1).coerceAtLeast(0) } ?: -1
        OpenFileDescriptor(project, virtualFile, line, 0).navigate(true)
    }

    private fun <T> startTask(title: String, busyMessage: String, work: suspend () -> T, onSuccess: (T) -> Unit) {
        isBusy = true
        searchPanel.setBusy(true, busyMessage)
        updateButtons()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) {
            private var result: Result<T>? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.text = busyMessage
                result = runCatching { runBlocking { work() } }
            }

            override fun onSuccess() {
                isBusy = false
                searchPanel.setBusy(false)
                updateButtons()
                val outcome = result
                if (outcome == null) {
                    searchPanel.showMessage("$title did not return a result.")
                } else {
                    outcome.fold(
                        onSuccess = onSuccess,
                        onFailure = { error -> searchPanel.showMessage("$title failed: ${error.message ?: "Unknown error"}") },
                    )
                }
            }

            override fun onCancel() {
                isBusy = false
                searchPanel.setBusy(false)
                searchPanel.showMessage("$title canceled.")
                updateButtons()
            }
        })
    }

    private fun updateButtons() {
        refreshButton.isEnabled = !isBusy
        enrichButton.isEnabled = repositoryAvailable && !isBusy
    }

    private companion object {
        const val NOT_AN_ATOMIC_REPOSITORY_MESSAGE = "This project is not an Atomic repository."
    }
}
