package dev.atomic.core.cli

import dev.atomic.core.process.ProcessResult
import dev.atomic.core.test.MockProcessRunner
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class AtomicCliImplTest {
    private val repoRoot = Paths.get("/repo")

    @Test
    fun testStatusExecutionAndParsing() = runTest {
        val mockRunner = MockProcessRunner()
        mockRunner.onCommand(
            "status",
            ProcessResult(
                exitCode = 0,
                stdout = """{"view":"main","modified":["src/lib.rs"],"added":[],"deleted":[],"untracked":[],"conflicted":[]}""",
                stderr = "",
            ),
        )

        val cli = AtomicCliImpl(processRunner = mockRunner)
        val status = cli.status(repoRoot)

        assertEquals("main", status.currentView)
        assertEquals(listOf(repoRoot.resolve("src/lib.rs")), status.modified)
        assertTrue(mockRunner.recordedCommands.any { it.contains("status") })
    }

    @Test
    fun testRecordExecution() = runTest {
        val mockRunner = MockProcessRunner()
        mockRunner.onCommand(
            "record",
            ProcessResult(
                exitCode = 0,
                stdout = "[dev 1/BAFKR4IHREC999] Initial record\n src/lib.rs\n",
                stderr = "",
            ),
        )

        val cli = AtomicCliImpl(processRunner = mockRunner)
        val recordResult = cli.record(repoRoot, "Initial record", listOf(repoRoot.resolve("src/lib.rs")))

        assertEquals("BAFKR4IHREC999", recordResult.changeHash)
        assertTrue(mockRunner.recordedCommands.none { it.contains("--json") })
        assertTrue(mockRunner.recordedCommands.any { it.containsAll(listOf("record", "-m", "Initial record", "--", "src/lib.rs")) })
        assertEquals("Initial record", recordResult.message)
        assertEquals(listOf(repoRoot.resolve("src/lib.rs")), recordResult.filesRecorded)
    }

    @Test
    fun testCommandTimeoutThrowsException() = runTest {
        val mockRunner = MockProcessRunner()
        mockRunner.defaultResponse = ProcessResult(
            exitCode = -1,
            stdout = "",
            stderr = "Timed out",
            isTimeout = true,
        )

        val cli = AtomicCliImpl(processRunner = mockRunner)
        var thrown = false
        try {
            cli.status(repoRoot)
        } catch (e: AtomicTimeoutException) {
            thrown = true
        }
        assertTrue(thrown, "Expected AtomicTimeoutException")
    }

    @Test
    fun testCommandErrorThrowsAtomicCommandException() = runTest {
        val mockRunner = MockProcessRunner()
        mockRunner.onCommand(
            "diff",
            ProcessResult(
                exitCode = 1,
                stdout = "",
                stderr = "fatal: not an atomic repo",
            ),
        )

        val cli = AtomicCliImpl(processRunner = mockRunner)
        var thrown = false
        try {
            cli.diff(repoRoot)
        } catch (e: AtomicCommandException) {
            thrown = true
        }
        assertTrue(thrown, "Expected AtomicCommandException")
    }

    @Test
    fun testListViews() = runTest {
        val mockRunner = MockProcessRunner()
        mockRunner.onCommand(
            "view list",
            ProcessResult(
                exitCode = 0,
                stdout = """[{"name":"main","current":true,"draft":false},{"name":"draft-v1","current":false,"draft":true,"parent":"main"}]""",
                stderr = "",
            ),
        )

        val cli = AtomicCliImpl(processRunner = mockRunner)
        val views = cli.listViews(repoRoot)

        assertEquals(2, views.size)
        assertEquals("main", views[0].name)
        assertTrue(views[0].isCurrent)
        assertEquals("draft-v1", views[1].name)
        assertTrue(views[1].isDraft)
    }
}
