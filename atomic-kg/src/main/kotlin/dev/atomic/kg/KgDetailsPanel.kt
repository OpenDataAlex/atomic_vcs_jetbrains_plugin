package dev.atomic.kg

import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.event.ListSelectionEvent

class KgDetailsPanel(
    private val onOpenNode: (KgSearchItem) -> Unit,
    private val onFocusRelated: (KgRelatedNode) -> Unit,
) : JPanel(BorderLayout()) {
    private val titleLabel = JLabel(EMPTY_TITLE, SwingConstants.LEFT)
    private val propertiesPanel = JPanel(GridLayout(0, 1))
    private val relatedModel = DefaultListModel<KgRelatedNode>()
    private val relatedList = JList(relatedModel)
    private val openButton = JButton("Open in editor")
    private var details: KgNodeDetails? = null

    init {
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        relatedList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        relatedList.cellRenderer = RelatedNodeRenderer()
        relatedList.addListSelectionListener { event: ListSelectionEvent ->
            if (!event.valueIsAdjusting) {
                relatedList.selectedValue?.let(onFocusRelated)
            }
        }

        openButton.isEnabled = false
        openButton.addActionListener {
            details?.node?.let { node ->
                onOpenNode(KgSearchItem.Node(node))
            }
        }

        val header = JPanel(BorderLayout())
        header.add(titleLabel, BorderLayout.CENTER)
        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        actions.add(openButton)
        header.add(actions, BorderLayout.EAST)

        val relatedScroll = JScrollPane(relatedList)
        relatedScroll.border = BorderFactory.createTitledBorder("Related nodes")
        val propertiesScroll = JScrollPane(propertiesPanel)
        propertiesScroll.border = BorderFactory.createTitledBorder("Properties")

        add(header, BorderLayout.NORTH)
        add(propertiesScroll, BorderLayout.CENTER)
        add(relatedScroll, BorderLayout.SOUTH)
        showEmpty()
    }

    fun showDetails(next: KgNodeDetails?) {
        details = next
        if (next == null) {
            showEmpty()
            return
        }
        titleLabel.text = "${next.node.name.ifBlank { next.node.id }}  [${next.node.type}]"
        openButton.isEnabled = next.node.path != null
        propertiesPanel.removeAll()
        next.properties.forEach { (label, value) ->
            propertiesPanel.add(JLabel("$label: $value"))
        }
        propertiesPanel.revalidate()
        propertiesPanel.repaint()
        relatedModel.clear()
        next.related.forEach(relatedModel::addElement)
        relatedList.clearSelection()
    }

    private fun showEmpty() {
        titleLabel.text = EMPTY_TITLE
        openButton.isEnabled = false
        propertiesPanel.removeAll()
        propertiesPanel.add(JLabel("Select a node on the canvas or in search results to inspect it."))
        propertiesPanel.revalidate()
        propertiesPanel.repaint()
        relatedModel.clear()
    }

    private class RelatedNodeRenderer : javax.swing.DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: javax.swing.JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): java.awt.Component {
            val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
            val related = value as? KgRelatedNode
            if (related != null) {
                val arrow = if (related.direction == KgRelationDirection.OUTGOING) "→" else "←"
                label.text = "$arrow ${related.relation}: ${related.node.name} (${related.node.type})"
            }
            return label
        }
    }

    private companion object {
        const val EMPTY_TITLE = "Node details"
    }
}
