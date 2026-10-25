package dev.atomic.ai.agent

import dev.atomic.ai.mcp.AtomicMcpServer
import dev.atomic.core.cli.AtomicCliImpl
import dev.atomic.core.process.ProcessResult
import dev.atomic.core.test.MockProcessRunner
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class AtomicAgentSessionTrackerTest {
    private val repoRoot = Paths.get("/repo")

    @Test
    fun testSessionStartTurnAndEnd() = runTest {
        val mockRunner = MockProcessRunner()
        mockRunner.onCommand("status", ProcessResult(0, """{"view":"main","modified":[]}""", ""))
        mockRunner.onCommand(
            "record",
            ProcessResult(0, """{"hash":"bafkr4ihturn1","message":"[Turn 1] Implemented auth helper","files":["src/app.rs"]}""", ""),
        )
        mockRunner.onCommand("attest", ProcessResult(0, """{"hash":"attest-session-123","status":"verified"}""", ""))

        val cli = AtomicCliImpl(processRunner = mockRunner)
        val tracker = AtomicAgentSessionTracker(cli = cli)

        // 1. Start Session
        val startResult = tracker.startSession("session-123", repoRoot)
        assertEquals("agent-session-123", startResult.draftView)
        assertEquals("main", startResult.parentView)

        val active = tracker.getSession("session-123")
        assertNotNull(active)
        assertEquals("agent-session-123", active?.draftView)

        // 2. Record Turn
        tracker.recordTouchedPath(repoRoot.resolve("src/app.rs"))
        val turnResult = tracker.recordTurn("session-123", "Implemented auth helper", repoRoot)
        assertEquals("[Turn 1] Implemented auth helper", turnResult.message)
        assertEquals(1, active?.turnIndex)

        // 3. End Session
        val endResult = tracker.endSession("session-123", repoRoot)
        assertEquals("main", endResult.restoredView)
        assertEquals("agent-session-123", endResult.draftView)
        assertNull(tracker.getSession("session-123"))
    }

    @Test
    fun testFallbackRecordingWhenTurnSkipped() = runTest {
        val mockRunner = MockProcessRunner()
        mockRunner.onCommand("status", ProcessResult(0, """{"view":"main","modified":[]}""", ""))
        mockRunner.onCommand("attest", ProcessResult(0, """{"hash":"attest-fallback","status":"verified"}""", ""))

        val cli = AtomicCliImpl(processRunner = mockRunner)
        val tracker = AtomicAgentSessionTracker(cli = cli)

        tracker.startSession("session-fallback", repoRoot)

        // Simulate VFS touching files without explicit turn recording
        tracker.recordTouchedPath(repoRoot.resolve("src/untouched.rs"))

        // End session directly
        val endResult = tracker.endSession("session-fallback", repoRoot)
        assertTrue(endResult.fallbackRecorded)
        assertTrue(mockRunner.recordedCommands.any { it.contains("record") })
    }

    @Test
    fun testMcpServerSessionVerbs() = runTest {
        val mockRunner = MockProcessRunner()
        mockRunner.onCommand("status", ProcessResult(0, """{"view":"main","modified":[]}""", ""))
        mockRunner.onCommand("record", ProcessResult(0, """{"hash":"bafkr4ihmcp","message":"Turn","files":[]}""", ""))
        mockRunner.onCommand("attest", ProcessResult(0, """{"hash":"attest-mcp","status":"verified"}""", ""))

        val cli = AtomicCliImpl(processRunner = mockRunner)
        val tracker = AtomicAgentSessionTracker(cli = cli)
        val server = AtomicMcpServer(cli = cli, sessionTracker = tracker, defaultRepoPath = repoRoot)

        // 1. Call atomic_session_start
        val startReq = """
            {
                "jsonrpc": "2.0",
                "id": 10,
                "method": "tools/call",
                "params": {
                    "name": "atomic_session_start",
                    "arguments": {
                        "sessionId": "agent-test-mcp"
                    }
                }
            }
        """.trimIndent()
        val startRespStr = server.handleMessage(startReq)
        assertNotNull(startRespStr)
        assertTrue(startRespStr!!.contains("agent-agent-test-mcp") || startRespStr.contains("agent-test-mcp"))

        // 2. Call atomic_session_turn_end
        val turnReq = """
            {
                "jsonrpc": "2.0",
                "id": 11,
                "method": "tools/call",
                "params": {
                    "name": "atomic_session_turn_end",
                    "arguments": {
                        "sessionId": "agent-test-mcp",
                        "message": "Completed step"
                    }
                }
            }
        """.trimIndent()
        val turnRespStr = server.handleMessage(turnReq)
        assertNotNull(turnRespStr)
        assertTrue(turnRespStr!!.contains("Turn recorded"))

        // 3. Call atomic_session_end
        val endReq = """
            {
                "jsonrpc": "2.0",
                "id": 12,
                "method": "tools/call",
                "params": {
                    "name": "atomic_session_end",
                    "arguments": {
                        "sessionId": "agent-test-mcp"
                    }
                }
            }
        """.trimIndent()
        val endRespStr = server.handleMessage(endReq)
        assertNotNull(endRespStr)
        assertTrue(endRespStr!!.contains("concluded"))
    }
}
