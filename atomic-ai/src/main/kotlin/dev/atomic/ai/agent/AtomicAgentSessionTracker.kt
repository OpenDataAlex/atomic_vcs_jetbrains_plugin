package dev.atomic.ai.agent

import dev.atomic.core.cli.AtomicCli
import dev.atomic.core.cli.AtomicCliImpl
import dev.atomic.core.model.AtomicAttestation
import dev.atomic.core.model.AtomicRecordResult
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

data class AgentSession(
    val sessionId: String,
    val repoPath: Path,
    val originalView: String,
    val draftView: String,
    var turnIndex: Int = 0,
    val touchedPaths: MutableSet<Path> = ConcurrentHashMap.newKeySet(),
)

data class AgentSessionStartResult(
    val sessionId: String,
    val draftView: String,
    val parentView: String,
)

data class AgentSessionEndResult(
    val sessionId: String,
    val draftView: String,
    val restoredView: String,
    val attestation: AtomicAttestation,
    val fallbackRecorded: Boolean = false,
)

class AtomicAgentSessionTracker(
    val cli: AtomicCli = AtomicCliImpl(),
) {
    private val activeSessions = ConcurrentHashMap<String, AgentSession>()

    fun getActiveSessions(): List<AgentSession> = activeSessions.values.toList()

    fun getSession(sessionId: String): AgentSession? = activeSessions[sessionId]

    fun recordTouchedPath(filePath: Path) {
        for (session in activeSessions.values) {
            if (filePath.startsWith(session.repoPath)) {
                session.touchedPaths.add(filePath.normalize())
            }
        }
    }

    suspend fun startSession(
        sessionId: String,
        repoPath: Path,
        parentView: String? = null,
    ): AgentSessionStartResult {
        val status = cli.status(repoPath)
        val originalView = status.currentView
        val baseView = parentView ?: originalView
        val draftView = "agent-$sessionId"

        // Create and switch to isolated draft view
        cli.createDraftView(repoPath, draftView, baseView)
        cli.switchView(repoPath, draftView)

        val session = AgentSession(
            sessionId = sessionId,
            repoPath = repoPath,
            originalView = originalView,
            draftView = draftView,
        )
        activeSessions[sessionId] = session

        return AgentSessionStartResult(
            sessionId = sessionId,
            draftView = draftView,
            parentView = baseView,
        )
    }

    suspend fun recordTurn(
        sessionId: String,
        message: String,
        repoPath: Path? = null,
    ): AtomicRecordResult {
        val session = activeSessions[sessionId]
            ?: throw IllegalArgumentException("No active session found for ID: $sessionId")
        val root = repoPath ?: session.repoPath

        val status = cli.status(root)
        val dirtyPaths = (status.allDirtyFiles + session.touchedPaths).distinct()

        val recordResult = if (dirtyPaths.isNotEmpty()) {
            cli.add(root, dirtyPaths)
            val msg = "[Turn ${session.turnIndex + 1}] $message"
            cli.record(root, msg, dirtyPaths)
        } else {
            AtomicRecordResult(
                changeHash = "no-changes",
                message = message,
                filesRecorded = emptyList(),
            )
        }

        session.turnIndex++
        session.touchedPaths.clear()
        return recordResult
    }

    suspend fun endSession(
        sessionId: String,
        repoPath: Path? = null,
    ): AgentSessionEndResult {
        val session = activeSessions.remove(sessionId)
            ?: throw IllegalArgumentException("No active session found for ID: $sessionId")
        val root = repoPath ?: session.repoPath

        // VFS Fallback check: If there are unrecorded modifications, record them now
        val status = cli.status(root)
        val unrecorded = (status.allDirtyFiles + session.touchedPaths).distinct()
        val fallbackRecorded = unrecorded.isNotEmpty() && runCatching {
            cli.add(root, unrecorded)
            cli.record(root, "[Agent Fallback Turn] Auto-recorded untracked AI edits", unrecorded)
        }.isSuccess

        // Attest draft view
        val attestation = runCatching { cli.attest(root, session.draftView) }.getOrElse {
            AtomicAttestation("attest-${session.draftView}-${System.currentTimeMillis()}", "")
        }

        // Switch back to original view; a failure here must not hide the session result
        runCatching { cli.switchView(root, session.originalView) }

        return AgentSessionEndResult(
            sessionId = sessionId,
            draftView = session.draftView,
            restoredView = session.originalView,
            attestation = attestation,
            fallbackRecorded = fallbackRecorded,
        )
    }

    companion object {
        val instance = AtomicAgentSessionTracker()
    }
}
