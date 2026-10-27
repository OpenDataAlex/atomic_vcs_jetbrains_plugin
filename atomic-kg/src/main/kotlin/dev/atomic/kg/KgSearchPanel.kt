package dev.atomic.kg

import dev.atomic.core.model.KgNode
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.event.ListSelectionEvent

class KgSearchPanel(
    private val onSearch: (KgSearchMode, String, String?) -> Unit,
    private val onNavigate: (KgSearchItem) -> Unit,
    private val onNodeFocus: (KgNode) -> Unit,
) : JPanel(BorderLayout()) {
    private val modeSelector = JComboBox(KgSearchMode.entries.toTypedArray())
    private val typeSelector = JComboBox(
        arrayOf("All types", "entity", "file", "change", "view", "intent", "goal", "memory"),
    )
    private val queryField = JTextField()
    private val searchButton = JButton("Search")
    private val resultsModel = DefaultListModel<KgSearchItem>()
    private val resultsList = JList(resultsModel)
    private val statusLabel = JLabel("Search the Atomic vault or indexed source code.", SwingConstants.LEFT)

    init {
        val controls = JPanel(FlowLayout(FlowLayout.LEFT))
        controls.add(modeSelector)
        controls.add(typeSelector)
        queryField.columns = 32
        controls.add(queryField)
        controls.add(searchButton)
        add(controls, BorderLayout.NORTH)
        add(JScrollPane(resultsList), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        resultsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        resultsList.addListSelectionListener { event: ListSelectionEvent ->
            if (!event.valueIsAdjusting) {
                (resultsList.selectedValue as? KgSearchItem.Node)?.let { onNodeFocus(it.value) }
            }
        }
        modeSelector.addActionListener { updateTypeFilterEnabled() }
        searchButton.addActionListener { submitSearch() }
        queryField.addActionListener { submitSearch() }
        resultsList.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    if (event.clickCount == 2) openSelectedResult()
                }
            },
        )
        resultsList.inputMap.put(KeyStroke.getKeyStroke("ENTER"), "open-result")
        resultsList.actionMap.put(
            "open-result",
            object : AbstractAction() {
                override fun actionPerformed(event: java.awt.event.ActionEvent?) = openSelectedResult()
            },
        )
        updateTypeFilterEnabled()
    }

    fun setBusy(isBusy: Boolean, message: String? = null) {
        searchButton.isEnabled = !isBusy
        modeSelector.isEnabled = !isBusy
        typeSelector.isEnabled = !isBusy && modeSelector.selectedItem == KgSearchMode.SEARCH
        queryField.isEnabled = !isBusy
        resultsList.isEnabled = !isBusy
        if (message != null) statusLabel.text = message
    }

    fun setQuery(query: String, mode: KgSearchMode) {
        queryField.text = query
        modeSelector.selectedItem = mode
        updateTypeFilterEnabled()
    }

    fun showMessage(message: String) {
        statusLabel.text = message
    }

    fun showResults(results: List<KgSearchItem>) {
        resultsModel.clear()
        results.forEach(resultsModel::addElement)
        statusLabel.text = if (results.isEmpty()) "No results found." else "${results.size} result(s). Double-click a file or code hit to open it."
    }

    private fun submitSearch() {
        val query = queryField.text.trim()
        if (query.isEmpty()) {
            statusLabel.text = "Enter a search term or code pattern."
            return
        }
        val mode = modeSelector.selectedItem as? KgSearchMode ?: KgSearchMode.SEARCH
        val type = if (mode == KgSearchMode.SEARCH) typeSelector.selectedItem?.toString()?.takeUnless { it == "All types" } else null
        onSearch(mode, query, type)
    }

    private fun openSelectedResult() {
        resultsList.selectedValue?.let(onNavigate)
    }

    private fun updateTypeFilterEnabled() {
        typeSelector.isEnabled = modeSelector.selectedItem == KgSearchMode.SEARCH
    }
}
