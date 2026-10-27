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

    @Test
    fun testVaultQueryCommands() = runTest {
        val mockRunner = MockProcessRunner()
        mockRunner.onCommand(
            "vault query search",
            ProcessResult(exitCode = 0, stdout = """{"nodes":[{"id":"view:main","type":"view","name":"main"}]}""", stderr = ""),
        )
        mockRunner.onCommand(
            "vault query code",
            ProcessResult(exitCode = 0, stdout = """[{"path":"src/Main.kt","line":8,"snippet":"fun main()"}]""", stderr = ""),
        )
        mockRunner.onCommand(
            "vault query neighbors",
            ProcessResult(
                exitCode = 0,
                stdout = """
                    {
                      "center_id":"view:main",
                      "nodes":[{"id":"file:README.md","type":"file","name":"README"}],
                      "edges":[{"from":"view:main","to":"file:README.md","kind":"CONTAINS"}]
                    }
                """.trimIndent(),
                stderr = "",
            ),
        )
        mockRunner.onCommand(
            "vault query entities",
            ProcessResult(exitCode = 0, stdout = """{"nodes":[{"id":"entity:src/Main.kt:main:1","kind":"function","name":"main"}]}""", stderr = ""),
        )

        val cli = AtomicCliImpl(processRunner = mockRunner)
        assertEquals(
            "view:main",
            cli
                .vaultQuerySearch(repoRoot, "main", "view", 12)
                .nodes
                .single()
                .id,
        )
        assertEquals(
            repoRoot.resolve("src/Main.kt"),
            cli.vaultQueryCode(repoRoot, "fun main", "src/*.kt", "kt").single().path,
        )
        assertEquals(
            "file:README.md",
            cli
                .vaultQueryNeighbors(repoRoot, "view:main")
                .nodes
                .single()
                .id,
        )
        assertEquals(
            "entity:src/Main.kt:main:1",
            cli
                .vaultQueryEntities(repoRoot, repoRoot.resolve("src/Main.kt"))
                .nodes
                .single()
                .id,
        )
        cli.vaultQueryEnrich(repoRoot)

        assertTrue(mockRunner.recordedCommands.any { it.containsAll(listOf("vault", "query", "search", "main", "-k", "12", "-t", "view", "--json")) })
        assertTrue(mockRunner.recordedCommands.any { it.containsAll(listOf("vault", "query", "code", "fun main", "-g", "src/*.kt", "-t", "kt", "--json")) })
        assertTrue(mockRunner.recordedCommands.any { it.containsAll(listOf("vault", "query", "entities", "src/Main.kt", "--json")) })
        assertTrue(mockRunner.recordedCommands.any { it.containsAll(listOf("vault", "query", "enrich")) })
    }

    @Test
    fun testVaultQuerySearchFallsBackWhenJsonIsUnsupported() = runTest {
        val mockRunner = MockProcessRunner()
        mockRunner.onCommand(
            "vault query search Main -k 5 --json",
            ProcessResult(exitCode = 2, stdout = "", stderr = "unexpected argument --json"),
        )
        mockRunner.onCommand(
            "vault query search",
            ProcessResult(exitCode = 0, stdout = "[view] view:main Main", stderr = ""),
        )

        val result = AtomicCliImpl(processRunner = mockRunner).vaultQuerySearch(repoRoot, "Main", limit = 5)

        assertEquals("view:main", result.nodes.single().id)
        assertEquals(2, mockRunner.recordedCommands.size)
        assertTrue(mockRunner.recordedCommands.last().none { it == "--json" })
    }
}
