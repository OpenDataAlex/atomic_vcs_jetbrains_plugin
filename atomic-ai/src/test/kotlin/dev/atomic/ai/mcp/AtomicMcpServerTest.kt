package dev.atomic.ai.mcp

import dev.atomic.core.cli.AtomicCliImpl
import dev.atomic.core.process.ProcessResult
import dev.atomic.core.test.MockProcessRunner
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class AtomicMcpServerTest {
    private val repoRoot = Paths.get("/repo")

    @Test
    fun testInitializeRequest() = runTest {
        val mockRunner = MockProcessRunner()
        val cli = AtomicCliImpl(processRunner = mockRunner)
        val server = AtomicMcpServer(cli = cli, defaultRepoPath = repoRoot)

        val request = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "initialize",
                "params": {}
            }
        """.trimIndent()

        val responseStr = server.handleMessage(request)
        assertNotNull(responseStr)

        val response = Json.parseToJsonElement(responseStr!!).jsonObject
        assertEquals("2.0", response["jsonrpc"]?.jsonPrimitive?.content)
        assertEquals(1, response["id"]?.jsonPrimitive?.int)

        val result = response["result"]?.jsonObject
        assertNotNull(result)
        val serverInfo = result?.get("serverInfo")?.jsonObject
        assertEquals("atomic-mcp-server", serverInfo?.get("name")?.jsonPrimitive?.content)
    }

    @Test
    fun testToolsList() = runTest {
        val mockRunner = MockProcessRunner()
        val cli = AtomicCliImpl(processRunner = mockRunner)
        val server = AtomicMcpServer(cli = cli, defaultRepoPath = repoRoot)

        val request = """
            {
                "jsonrpc": "2.0",
                "id": 2,
                "method": "tools/list",
                "params": {}
            }
        """.trimIndent()

        val responseStr = server.handleMessage(request)
        assertNotNull(responseStr)

        val response = Json.parseToJsonElement(responseStr!!).jsonObject
        val tools = response["result"]?.jsonObject?.get("tools")?.jsonArray
        assertNotNull(tools)
        assertTrue(tools!!.size >= 7)

        val toolNames = tools.map { it.jsonObject["name"]?.jsonPrimitive?.content }
        assertTrue(toolNames.contains("atomic_status"))
        assertTrue(toolNames.contains("atomic_diff"))
        assertTrue(toolNames.contains("atomic_log"))
        assertTrue(toolNames.contains("atomic_change"))
        assertTrue(toolNames.contains("atomic_query"))
        assertTrue(toolNames.contains("atomic_vault_context"))
        assertTrue(toolNames.contains("atomic_provenance"))
    }

    @Test
    fun testToolsCallStatus() = runTest {
        val mockRunner = MockProcessRunner()
        mockRunner.onCommand(
            "status",
            ProcessResult(
                exitCode = 0,
                stdout = """{"view":"feature-login","modified":["src/login.rs"],"added":[],"deleted":[],"untracked":[],"conflicted":[]}""",
                stderr = "",
            ),
        )

        val cli = AtomicCliImpl(processRunner = mockRunner)
        val server = AtomicMcpServer(cli = cli, defaultRepoPath = repoRoot)

        val request = """
            {
                "jsonrpc": "2.0",
                "id": 3,
                "method": "tools/call",
                "params": {
                    "name": "atomic_status",
                    "arguments": {}
                }
            }
        """.trimIndent()

        val responseStr = server.handleMessage(request)
        assertNotNull(responseStr)

        val response = Json.parseToJsonElement(responseStr!!).jsonObject
        val result = response["result"]?.jsonObject
        val content = result
            ?.get("content")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
        val text = content?.get("text")?.jsonPrimitive?.content
        assertNotNull(text)
        assertTrue(text!!.contains("Active View: feature-login"))
        assertTrue(text.contains("M /repo/src/login.rs") || text.contains("login.rs"))
    }

    @Test
    fun testTurnEndReportsScopeAgainstBaseView() = runTest {
        val mockRunner = MockProcessRunner()
        // Order matters: the mock matches by substring and "status" is contained in "--name-status".
        mockRunner.onCommand("--name-status", ProcessResult(0, "M  src/a.kt\nD  src/old.kt\n", ""))
        mockRunner.onCommand("--stat", ProcessResult(0, " 2 files changed, 5 insertions(+), 9 deletions(-)\n", ""))
        mockRunner.onCommand(
            "status",
            ProcessResult(0, """{"view":"dev","modified":["src/a.kt"],"added":[],"deleted":[],"untracked":[],"conflicted":[]}""", ""),
        )
        mockRunner.onCommand("record", ProcessResult(0, "[agent-s1 1/HASH1] [Turn 1] edit\n src/a.kt\n", ""))
        val server = AtomicMcpServer(cli = AtomicCliImpl(processRunner = mockRunner), defaultRepoPath = repoRoot)

        server.handleMessage(toolCall(1, "atomic_session_start", """{"sessionId":"s1"}"""))
        val response = server.handleMessage(toolCall(2, "atomic_session_turn_end", """{"sessionId":"s1","message":"edit"}"""))

        val text = Json
            .parseToJsonElement(response!!)
            .jsonObject["result"]!!
            .jsonObject["content"]!!
            .jsonArray[0]
            .jsonObject["text"]!!
            .jsonPrimitive.content
        assertTrue(text.contains("Scope vs 'dev': 2 files changed (+5 / -9)"), text)
        assertTrue(text.contains("D src/old.kt"), text)
        assertTrue(text.contains("WARNING: 1 deleted: src/old.kt"), text)
        assertTrue(
            mockRunner.recordedCommands.any { it.containsAll(listOf("diff", "--from", "dev", "--to", "agent-s1", "--name-status")) },
        )
    }

    private fun toolCall(id: Int, name: String, arguments: String) =
        """{"jsonrpc":"2.0","id":$id,"method":"tools/call","params":{"name":"$name","arguments":$arguments}}"""
}
