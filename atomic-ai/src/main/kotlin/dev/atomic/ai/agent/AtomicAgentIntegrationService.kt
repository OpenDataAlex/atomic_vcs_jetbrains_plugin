package dev.atomic.ai.agent

import dev.atomic.core.cli.AtomicCliImpl
import dev.atomic.core.cli.AtomicTimeoutException
import java.nio.file.Path

data class AgentIntegrationCommandResult(
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
)

class AtomicAgentIntegrationService(
    private val cli: AtomicCliImpl = AtomicCliImpl(),
) {
    suspend fun enable(repoPath: Path, agentId: String): AgentIntegrationCommandResult {
        validateAgentId(agentId)
        return run(repoPath, listOf("agent", "enable", "--agent", agentId))
    }

    suspend fun disable(repoPath: Path, agentId: String): AgentIntegrationCommandResult {
        validateAgentId(agentId)
        return run(repoPath, listOf("agent", "disable", "--agent", agentId))
    }

    suspend fun status(repoPath: Path): AgentIntegrationCommandResult =
        run(repoPath, listOf("agent", "status", "--verbose"))

    private suspend fun run(
        repoPath: Path,
        args: List<String>,
    ): AgentIntegrationCommandResult =
        try {
            val result = cli.runCommand(
                repoPath = repoPath,
                args = args,
                timeoutMillis = COMMAND_TIMEOUT_MILLIS,
                allowFailure = true,
            )
            AgentIntegrationCommandResult(result.exitCode, result.stdout, result.stderr)
        } catch (e: AtomicTimeoutException) {
            AgentIntegrationCommandResult(null, "", e.message.orEmpty(), timedOut = true)
        }

    companion object {
        const val COMMAND_TIMEOUT_MILLIS = 120_000L

        private val AGENT_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")

        fun validateAgentId(agentId: String) {
            require(AGENT_ID_PATTERN.matches(agentId)) {
                "Enter an agent ID containing only letters, numbers, dots, underscores, or hyphens."
            }
        }
    }
}
