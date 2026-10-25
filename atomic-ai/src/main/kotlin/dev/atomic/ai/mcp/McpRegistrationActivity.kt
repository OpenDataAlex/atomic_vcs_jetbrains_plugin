package dev.atomic.ai.mcp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.atomic.vcs.AtomicVcs
import dev.atomic.vcs.settings.AtomicSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class McpRegistrationActivity(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ProjectActivity {
    override suspend fun execute(project: Project) = withContext(ioDispatcher) {
        val basePath = project.basePath ?: return@withContext
        val root = Path.of(basePath)

        val settings = AtomicSettings.getInstance(project)
        if (!settings.autoRegisterMcp) return@withContext

        // Check if project contains .atomic
        val dotAtomic = root.resolve(AtomicVcs.DOT_DIR)
        if (!Files.exists(dotAtomic)) return@withContext

        registerJetBrainsAiAssistant(root, settings.executablePath)
        registerJunieMcp(root, settings.executablePath)
    }

    private fun registerJetBrainsAiAssistant(root: Path, customExePath: String) {
        try {
            val ideaDir = root.resolve(".idea")
            if (!Files.isDirectory(ideaDir)) return

            val mcpServersFile = ideaDir.resolve("llm.mcpServers.xml")
            val exeCommand = customExePath.ifEmpty { "atomic" }

            val content = """
                <application>
                  <component name="LlmMcpServers">
                    <option name="servers">
                      <list>
                        <McpServerConfig>
                          <option name="name" value="Atomic" />
                          <option name="command" value="$exeCommand" />
                          <option name="args">
                            <list>
                              <option value="mcp" />
                            </list>
                          </option>
                        </McpServerConfig>
                      </list>
                    </option>
                  </component>
                </application>
            """.trimIndent()

            if (!Files.exists(mcpServersFile)) {
                Files.writeString(mcpServersFile, content)
            }
        } catch (e: IOException) {
            LOG.warn("Could not register the Atomic MCP server for AI Assistant", e)
        }
    }

    private fun registerJunieMcp(root: Path, customExePath: String) {
        try {
            val junieDir = root.resolve(".junie")
            if (!Files.isDirectory(junieDir)) {
                Files.createDirectories(junieDir)
            }

            val mcpJsonFile = junieDir.resolve("mcp.json")
            val exeCommand = customExePath.ifEmpty { "atomic" }

            val content = """
                {
                  "mcpServers": {
                    "atomic": {
                      "command": "$exeCommand",
                      "args": ["mcp"],
                      "env": {}
                    }
                  }
                }
            """.trimIndent()

            if (!Files.exists(mcpJsonFile)) {
                Files.writeString(mcpJsonFile, content)
            }
        } catch (e: IOException) {
            LOG.warn("Could not register the Atomic MCP server for Junie", e)
        }
    }

    private companion object {
        val LOG = Logger.getInstance(McpRegistrationActivity::class.java)
    }
}
