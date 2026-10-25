package dev.atomic.core.process

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DefaultProcessRunnerTest {
    private val runner = DefaultProcessRunner()

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun onlyOnPosix() {
        assumeFalse(System.getProperty("os.name", "").contains("win", ignoreCase = true))
    }

    @Test
    fun capturesStdoutAndStderrSeparately() = runTest {
        val result = runner.runProcess(listOf("sh", "-c", "echo out; echo err 1>&2"))

        assertEquals(0, result.exitCode)
        assertEquals("out", result.stdout.trim())
        assertEquals("err", result.stderr.trim())
        assertFalse(result.isTimeout)
    }

    @Test
    fun reportsNonZeroExitCode() = runTest {
        val result = runner.runProcess(listOf("sh", "-c", "exit 7"))

        assertEquals(7, result.exitCode)
    }

    @Test
    fun runsInTheGivenWorkingDirectory() = runTest {
        val result = runner.runProcess(listOf("pwd"), workingDir = tempDir)

        assertEquals(tempDir.toRealPath().toString(), result.stdout.trim())
    }

    @Test
    fun passesEnvironmentVariables() = runTest {
        val result = runner.runProcess(listOf("sh", "-c", "echo \$ATOMIC_TEST_VAR"), env = mapOf("ATOMIC_TEST_VAR" to "hello"))

        assertEquals("hello", result.stdout.trim())
    }

    @Test
    fun stdinIsClosedSoProgramsWaitingForInputDoNotHang() = runTest {
        val result = runner.runProcess(listOf("sh", "-c", "cat; echo done"), timeoutMillis = 10_000)

        assertFalse(result.isTimeout)
        assertEquals("done", result.stdout.trim())
    }

    @Test
    fun timedOutProcessIsKilledAndFlagged() = runTest {
        val result = runner.runProcess(listOf("sleep", "30"), timeoutMillis = 300)

        assertTrue(result.isTimeout)
        assertEquals(-1, result.exitCode)
        assertTrue(result.stderr.contains("timed out"), result.stderr)
    }
}
