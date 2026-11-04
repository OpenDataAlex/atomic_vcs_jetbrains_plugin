package dev.atomic.ai.agent

import dev.atomic.core.cli.AtomicCliImpl
import dev.atomic.core.process.ProcessResult
import dev.atomic.core.process.ProcessRunner
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.nio.file.Paths

class AtomicAgentIntegrationServiceTest {
    private val repoPath = Paths.get("/project/repo")
    private val executablePath = Paths.get(System.getProperty("java.home"), "bin", "java")

    @Test
    fun enableUsesConfiguredExecutableSelectedAgentAndRepository() = runTest {
        val runner = RecordingProcessRunner()
        val service = service(runner)

        val result = service.enable(repoPath, "claude-code")

        assertEquals(0, result.exitCode)
        assertEquals(
            listOf(executablePath.toString(), "agent", "enable", "--agent", "claude-code"),
            runner.command,
        )
        assertEquals(repoPath, runner.workingDirectory)
    }

    @Test
    fun disableUsesSelectedCustomAgentAndStatusUsesVerboseCommand() = runTest {
        val runner = RecordingProcessRunner()
        val service = service(runner)

        service.disable(repoPath, "custom-agent.v2")
        assertEquals(
            listOf(executablePath.toString(), "agent", "disable", "--agent", "custom-agent.v2"),
            runner.command,
        )
        assertEquals(repoPath, runner.workingDirectory)

        service.status(repoPath)
        assertEquals(listOf(executablePath.toString(), "agent", "status", "--verbose"), runner.command)
        assertEquals(repoPath, runner.workingDirectory)
    }

    @Test
    fun preservesNonzeroExitAndOutput() = runTest {
        val runner = RecordingProcessRunner(ProcessResult(3, "partial output", "unsupported agent"))

        val result = service(runner).enable(repoPath, "unknown-agent")

        assertEquals(3, result.exitCode)
        assertEquals("partial output", result.stdout)
        assertEquals("unsupported agent", result.stderr)
    }

    @Test
    fun reportsTimeout() = runTest {
        val runner = RecordingProcessRunner(ProcessResult(1, "", "", isTimeout = true))

        val result = service(runner).status(repoPath)

        assertTrue(result.timedOut)
        assertEquals(null, result.exitCode)
        assertTrue(result.stderr.contains("timed out"))
    }

    @Test
    fun rejectsMalformedAgentIdBeforeRunningCommand() = runTest {
        val runner = RecordingProcessRunner()

        val error = runCatching { service(runner).enable(repoPath, "../claude") }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertFalse(runner.wasCalled)
    }

    private fun service(runner: RecordingProcessRunner) = AtomicAgentIntegrationService(
        AtomicCliImpl(
            processRunner = runner,
            executablePathProvider = { executablePath.toString() },
        ),
    )

    private class RecordingProcessRunner(
        private val response: ProcessResult = ProcessResult(0, "agent integration ready", ""),
    ) : ProcessRunner {
        var command: List<String> = emptyList()
        var workingDirectory: Path? = null
        var wasCalled: Boolean = false
            private set

        override suspend fun runProcess(
            command: List<String>,
            workingDir: Path?,
            timeoutMillis: Long,
            env: Map<String, String>,
        ): ProcessResult {
            this.command = command
            workingDirectory = workingDir
            wasCalled = true
            return response
        }
    }
}
