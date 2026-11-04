package dev.atomic.ai.agent

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import dev.atomic.core.cli.AtomicCliImpl
import dev.atomic.vcs.settings.AtomicSettings
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingConstants

class AtomicAgentIntegrationsConfigurable(private val project: Project) : SearchableConfigurable {
    private val settings = AtomicSettings.getInstance(project)
    private val repositoryField = TextFieldWithBrowseButton()
    private val agentIdField = JTextField("claude-code", 20)
    private val presets = documentedAgentPresets()
    private val presetSelector = JComboBox(presets.keys.toTypedArray()).apply {
        addActionListener {
            val selectedPreset = selectedItem as? String
            presets[selectedPreset]?.let { agentIdField.text = it }
        }
    }
    private val statusLabel = JLabel(" ")
    private val outputArea = JTextArea(12, 72).apply {
        isEditable = false
        lineWrap = false
    }
    private val enableButton = JButton("Enable")
    private val disableButton = JButton("Disable")
    private val statusButton = JButton("Refresh Status")
    private var busy = false
    private var repositoryBrowseListenerConfigured = false

    @Volatile private var uiActive = false

    init {
        enableButton.addActionListener { enableSelectedAgent() }
        disableButton.addActionListener {
            runSelectedAgentCommand("Disable") { path, agentId -> service().disable(path, requireNotNull(agentId)) }
        }
        statusButton.addActionListener {
            runSelectedAgentCommand("Status", requiresAgent = false) { path, _ -> service().status(path) }
        }
    }

    override fun getId(): String = "dev.atomic.ai.agent.AtomicAgentIntegrationsConfigurable"

    override fun getDisplayName(): String = "Atomic Agent Integrations"

    override fun createComponent(): JComponent {
        uiActive = true
        if (repositoryField.text.isBlank()) repositoryField.text = project.basePath.orEmpty()
        if (!repositoryBrowseListenerConfigured) {
            repositoryField.addBrowseFolderListener(
                project,
                FileChooserDescriptorFactory
                    .createSingleFolderDescriptor()
                    .withTitle("Select Atomic Repository Directory")
                    .withDescription("Choose the directory where the Atomic agent command will run"),
            )
            repositoryBrowseListenerConfigured = true
        }

        val panel = JPanel(GridBagLayout())
        val constraints = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(5, 8, 5, 8)
            anchor = GridBagConstraints.WEST
        }
        var row = 0

        constraints.gridx = 0
        constraints.gridy = row
        constraints.weightx = 0.0
        panel.add(JLabel("Repository directory:"), constraints)
        constraints.gridx = 1
        constraints.weightx = 1.0
        constraints.gridwidth = 2
        panel.add(repositoryField, constraints)
        constraints.gridwidth = 1

        row++
        constraints.gridx = 0
        constraints.gridy = row
        constraints.weightx = 0.0
        panel.add(JLabel("Agent integration:"), constraints)
        constraints.gridx = 1
        constraints.weightx = 0.0
        panel.add(presetSelector, constraints)
        constraints.gridx = 2
        constraints.weightx = 1.0
        panel.add(agentIdField, constraints)

        row++
        constraints.gridx = 0
        constraints.gridy = row
        constraints.gridwidth = 3
        constraints.weightx = 1.0
        panel.add(
            JLabel(
                "Choose a documented preset or enter another agent ID supported by your Atomic CLI. " +
                    "Claude Code hooks can be installed; Codex hooks are experimental. This does not establish " +
                    "compatibility with Claude Agent, Junie, or arbitrary ACP agents inside Air.",
                SwingConstants.LEFT,
            ),
            constraints,
        )

        row++
        constraints.gridy = row
        panel.add(
            JLabel("Enable installs hooks/skills and may modify project or shared files; status reports CLI output only."),
            constraints,
        )

        row++
        constraints.gridy = row
        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            add(enableButton)
            add(disableButton)
            add(statusButton)
        }
        panel.add(buttons, constraints)

        row++
        constraints.gridy = row
        panel.add(statusLabel, constraints)

        row++
        constraints.gridy = row
        constraints.fill = GridBagConstraints.BOTH
        constraints.weighty = 1.0
        panel.add(JScrollPane(outputArea), constraints)

        val wrapper = JPanel(BorderLayout())
        wrapper.add(panel, BorderLayout.CENTER)
        return wrapper
    }

    override fun isModified(): Boolean = false

    override fun apply() = Unit

    override fun reset() {
        repositoryField.text = project.basePath.orEmpty()
        presetSelector.selectedIndex = 0
        statusLabel.text = " "
        outputArea.text = ""
    }

    override fun disposeUIResources() {
        uiActive = false
    }

    private fun enableSelectedAgent() {
        val agentId = selectedAgentIdOrShowError() ?: return
        val confirmed = Messages.showOkCancelDialog(
            project,
            "Atomic will install the '$agentId' integration in the selected directory. This can fetch integration " +
                "files and modify project or shared agent configuration. It does not guarantee that the hooks " +
                "are used by an agent running inside Air. Continue?",
            "Enable Atomic Agent Integration",
            "Enable",
            "Cancel",
            Messages.getWarningIcon(),
        ) == Messages.OK
        if (!confirmed) return
        runSelectedAgentCommand("Enable") { path, _ -> service().enable(path, agentId) }
    }

    private fun runSelectedAgentCommand(
        operation: String,
        requiresAgent: Boolean = true,
        command: suspend (Path, String?) -> AgentIntegrationCommandResult,
    ) {
        if (busy) return
        val agentId = if (requiresAgent) selectedAgentIdOrShowError() ?: return else null
        val path = runCatching {
            require(repositoryField.text.isNotBlank()) { "Select a repository directory before running the Atomic agent command." }
            repositoryPath()
        }.getOrElse {
            showError("Invalid repository directory: ${it.message.orEmpty()}")
            return
        }
        busy = true
        updateButtonState()
        statusLabel.text = "$operation running…"

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runCatching { runBlocking { command(path, agentId) } }
            ApplicationManager.getApplication().invokeLater(
                {
                    if (project.isDisposed || !uiActive) return@invokeLater
                    busy = false
                    updateButtonState()
                    result.fold(
                        onSuccess = { displayResult(operation, it) },
                        onFailure = { showError("$operation failed: ${it.message.orEmpty()}") },
                    )
                },
                ModalityState.any(),
            )
        }
    }

    private fun displayResult(operation: String, result: AgentIntegrationCommandResult) {
        statusLabel.text = when {
            result.timedOut -> "$operation timed out"
            result.exitCode == 0 -> "$operation completed"
            else -> "$operation failed (exit code ${result.exitCode})"
        }
        outputArea.text = buildString {
            if (result.stdout.isNotBlank()) {
                appendLine("stdout:")
                appendLine(result.stdout.trimEnd())
            }
            if (result.stderr.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                appendLine("stderr:")
                appendLine(result.stderr.trimEnd())
            }
            if (isEmpty()) append("No output.")
        }
        outputArea.caretPosition = 0
    }

    private fun showError(message: String) {
        statusLabel.text = message
        outputArea.text = message
        outputArea.caretPosition = 0
    }

    private fun repositoryPath(): Path = Path.of(repositoryField.text.trim())

    private fun selectedAgentId(): String = agentIdField.text.trim()

    private fun selectedAgentIdOrShowError(): String? = try {
        selectedAgentId().also(AtomicAgentIntegrationService::validateAgentId)
    } catch (e: IllegalArgumentException) {
        showError(e.message.orEmpty())
        null
    }

    private fun service(): AtomicAgentIntegrationService = AtomicAgentIntegrationService(
        AtomicCliImpl(executablePathProvider = { settings.executablePath }),
    )

    private fun updateButtonState() {
        enableButton.isEnabled = !busy
        disableButton.isEnabled = !busy
        statusButton.isEnabled = !busy
    }

    private fun documentedAgentPresets(): LinkedHashMap<String, String> = linkedMapOf(
        "Claude Code (claude-code)" to "claude-code",
        "Codex (codex; experimental hooks)" to "codex",
        "Antigravity CLI (agy)" to "agy",
        "Cline (cline)" to "cline",
        "GitHub Copilot (copilot)" to "copilot",
        "Cursor (cursor)" to "cursor",
        "Devin (devin)" to "devin",
        "Grok Build (grok)" to "grok",
        "Kilo Code (kilo)" to "kilo",
        "Kiro (kiro)" to "kiro",
        "OpenCode (opencode)" to "opencode",
        "Pi (pi)" to "pi",
    )
}
