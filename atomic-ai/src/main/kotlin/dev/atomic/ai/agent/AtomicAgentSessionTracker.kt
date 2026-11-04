package dev.atomic.ai.agent

import dev.atomic.core.cli.AtomicCli
import dev.atomic.core.cli.AtomicCliImpl
import dev.atomic.core.model.AtomicAttestation
import dev.atomic.core.model.AtomicRecordResult
import dev.atomic.core.model.AtomicScopeSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

data class AgentSession(
    val sessionId: String,
    val repoPath: Path,
    val originalView: String,
    val draftView: String,
    /** The view the draft was branched from; scope checks compare the draft against it. */
    val baseView: String = originalView,
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
            baseView = baseView,
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

    /**
     * Summarizes what the session's draft view changed relative to the view it was branched from.
     * Best effort: returns `null` if the session is unknown or the CLI cannot produce the comparison.
     */
    suspend fun scopeSummary(sessionId: String, repoPath: Path? = null): AtomicScopeSummary? {
        val session = activeSessions[sessionId] ?: return null
        return attempt { cli.scopeSummary(repoPath ?: session.repoPath, session.baseView, session.draftView) }.getOrNull()
    }

    suspend fun endSession(
        sessionId: String,
        repoPath: Path? = null,
    ): AgentSessionEndResult {
        val session = activeSessions.remove(sessionId)
            ?: throw IllegalArgumentException("No active session found for ID: $sessionId")
        val root = repoPath ?: session.repoPath

        val fallbackRecorded: Boolean
        val attestation: AtomicAttestation
        try {
            fallbackRecorded = attempt {
                val status = cli.status(root)
                val unrecorded = (status.allDirtyFiles + session.touchedPaths).distinct()
                if (unrecorded.isEmpty()) {
                    false
                } else {
                    cli.add(root, unrecorded)
                    cli.record(root, "[Agent Fallback Turn] Auto-recorded untracked AI edits", unrecorded)
                    true
                }
            }.getOrDefault(false)

            attestation = attempt { cli.attest(root, session.draftView) }.getOrElse {
                AtomicAttestation("attest-${session.draftView}-${System.currentTimeMillis()}", "")
            }
        } finally {
            withContext(NonCancellable) {
                attempt { cli.switchView(root, session.originalView) }
            }
        }

        return AgentSessionEndResult(
            sessionId = sessionId,
            draftView = session.draftView,
            restoredView = session.originalView,
            attestation = attestation,
            fallbackRecorded = fallbackRecorded,
        )
    }

    suspend fun endAllSessions(): List<AgentSessionEndResult> {
        val results = mutableListOf<AgentSessionEndResult>()
        for (sessionId in activeSessions.keys.toList()) {
            val result = attempt { endSession(sessionId) }.getOrNull()
            if (result != null) results.add(result)
        }
        return results
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }

    companion object {
        val instance = AtomicAgentSessionTracker()
    }
}
