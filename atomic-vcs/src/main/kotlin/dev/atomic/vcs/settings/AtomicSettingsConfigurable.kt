package dev.atomic.vcs.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import dev.atomic.core.cli.AtomicCliImpl
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

class AtomicSettingsConfigurable(private val project: Project) : SearchableConfigurable {
    private val settings = AtomicSettings.getInstance(project)

    private val executablePathField = TextFieldWithBrowseButton()
    private val testButton = JButton("Test / Auto-detect")
    private val statusLabel = JLabel(" ")
    private val autoRegisterMcpCheckBox = JCheckBox("Automatically register Atomic STDIO MCP server for AI Assistant and Junie")
    private val enableAgentTrackingCheckBox = JCheckBox("Enable AI Agent session isolation (split draft views on turn start)")
    private val defaultParentViewField = JTextField(15)

    override fun getId(): String = "dev.atomic.vcs.settings.AtomicSettingsConfigurable"

    override fun getDisplayName(): String = "Atomic VCS"

    override fun createComponent(): JComponent {
        executablePathField.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory
                .createSingleFileNoJarsDescriptor()
                .withTitle("Select Atomic Executable")
                .withDescription("Choose the path to the atomic binary"),
        )

        testButton.addActionListener {
            val pathInput = executablePathField.text.trim()
            val cli = AtomicCliImpl(executablePathProvider = { pathInput })
            val detected = runBlocking { cli.findExecutable(pathInput.ifEmpty { null }) }
            if (detected != null) {
                executablePathField.text = detected.toAbsolutePath().toString()
                statusLabel.text = "✓ Found executable: ${detected.toAbsolutePath()}"
                statusLabel.foreground = java.awt.Color(0, 150, 0)
            } else {
                statusLabel.text = "✗ Executable not found at specified path or on PATH"
                statusLabel.foreground = java.awt.Color(200, 0, 0)
            }
        }

        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(6, 8, 6, 8)
            anchor = GridBagConstraints.WEST
        }

        var row = 0

        // Executable Path
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel.add(JLabel("Atomic Executable Path:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(executablePathField, gbc)

        gbc.gridx = 2
        gbc.weightx = 0.0
        panel.add(testButton, gbc)

        // Status line
        row++
        gbc.gridx = 1
        gbc.gridy = row
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        panel.add(statusLabel, gbc)
        gbc.gridwidth = 1

        // Default Parent View
        row++
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        panel.add(JLabel("Default Parent View:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(defaultParentViewField, gbc)

        // MCP Checkbox
        row++
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 3
        gbc.weightx = 1.0
        panel.add(autoRegisterMcpCheckBox, gbc)

        // Agent Tracking Checkbox
        row++
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 3
        gbc.weightx = 1.0
        panel.add(enableAgentTrackingCheckBox, gbc)

        // Fill remaining vertical space
        row++
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weighty = 1.0
        panel.add(Box.createVerticalGlue(), gbc)

        val wrapper = JPanel(BorderLayout())
        wrapper.add(panel, BorderLayout.NORTH)
        return wrapper
    }

    override fun isModified(): Boolean = executablePathField.text != settings.executablePath ||
        autoRegisterMcpCheckBox.isSelected != settings.autoRegisterMcp ||
        enableAgentTrackingCheckBox.isSelected != settings.enableAgentTracking ||
        defaultParentViewField.text != settings.defaultParentView

    override fun apply() {
        settings.executablePath = executablePathField.text.trim()
        settings.autoRegisterMcp = autoRegisterMcpCheckBox.isSelected
        settings.enableAgentTracking = enableAgentTrackingCheckBox.isSelected
        settings.defaultParentView = defaultParentViewField.text.trim().ifEmpty { "main" }
    }

    override fun reset() {
        executablePathField.text = settings.executablePath
        autoRegisterMcpCheckBox.isSelected = settings.autoRegisterMcp
        enableAgentTrackingCheckBox.isSelected = settings.enableAgentTracking
        defaultParentViewField.text = settings.defaultParentView
        statusLabel.text = " "
    }
}
