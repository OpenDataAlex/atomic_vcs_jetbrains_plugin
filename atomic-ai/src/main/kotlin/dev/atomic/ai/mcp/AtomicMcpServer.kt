package dev.atomic.ai.mcp

import dev.atomic.ai.agent.AtomicAgentSessionTracker
import dev.atomic.core.cli.AtomicCli
import dev.atomic.core.cli.AtomicCliImpl
import dev.atomic.core.model.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.nio.file.Path
import java.nio.file.Paths

private const val REPO_PATH = "repoPath"
private const val SESSION_ID = "sessionId"
private const val CHANGE_HASH = "changeHash"
private const val TYPE_STRING = "string"
private const val INVALID_PARAMS = -32602

/** One property of a tool's JSON input schema. */
private class ToolProperty(
    val name: String,
    val description: String,
    val type: String = TYPE_STRING,
    val arrayItemType: String? = null,
)

private val repoPathProperty = ToolProperty(REPO_PATH, "Path to repository root (optional)")

class AtomicMcpServer(
    val cli: AtomicCli = AtomicCliImpl(),
    val sessionTracker: AtomicAgentSessionTracker = AtomicAgentSessionTracker(cli),
    val defaultRepoPath: Path = Paths.get(".").toAbsolutePath().normalize(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    suspend fun start(input: InputStream, output: OutputStream) = withContext(ioDispatcher) {
        val reader = BufferedReader(InputStreamReader(input))
        val writer = PrintWriter(output, true)

        var line: String? = reader.readLine()
        while (line != null) {
            val trimmed = line.trim()
            val response = if (trimmed.isNotEmpty()) handleMessage(trimmed) else null
            if (response != null) {
                writer.println(response)
                writer.flush()
            }
            line = reader.readLine()
        }
    }

    suspend fun handleMessage(rawMessage: String): String? {
        val request: JsonRpcRequest = try {
            json.decodeFromString(JsonRpcRequest.serializer(), rawMessage)
        } catch (e: IllegalArgumentException) {
            val errResponse = JsonRpcResponse(
                error = JsonRpcError(-32700, "Parse error: ${e.message}"),
            )
            return json.encodeToString(JsonRpcResponse.serializer(), errResponse)
        }

        // Notifications don't expect a response
        if (request.method.startsWith("notifications/")) {
            return null
        }

        val response = processRequest(request)
        return json.encodeToString(JsonRpcResponse.serializer(), response)
    }

    suspend fun processRequest(request: JsonRpcRequest): JsonRpcResponse =
        when (request.method) {
            "initialize" -> JsonRpcResponse(id = request.id, result = initializeResult())
            "ping" -> JsonRpcResponse(id = request.id, result = buildJsonObject {})
            "tools/list" -> JsonRpcResponse(
                id = request.id,
                result = buildJsonObject { put("tools", json.encodeToJsonElement(listTools())) },
            )
            "tools/call" -> callTool(request)
            else -> JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(-32601, "Method not found: ${request.method}"),
            )
        }

    private fun initializeResult(): JsonObject = buildJsonObject {
        put("protocolVersion", "2024-11-05")
        putJsonObject("capabilities") {
            putJsonObject("tools") {
                put("listChanged", false)
            }
        }
        putJsonObject("serverInfo") {
            put("name", "atomic-mcp-server")
            put("version", "1.0.0")
        }
    }

    private suspend fun callTool(request: JsonRpcRequest): JsonRpcResponse {
        val params = request.params ?: return JsonRpcResponse(
            id = request.id,
            error = JsonRpcError(INVALID_PARAMS, "Invalid params: params object required"),
        )
        val toolName = params["name"]?.jsonPrimitive?.content ?: return JsonRpcResponse(
            id = request.id,
            error = JsonRpcError(INVALID_PARAMS, "Missing tool name"),
        )
        val arguments = params["arguments"]?.jsonObject ?: buildJsonObject {}

        return try {
            val callResult = executeTool(toolName, arguments)
            JsonRpcResponse(id = request.id, result = json.encodeToJsonElement(callResult))
        } catch (e: IllegalArgumentException) {
            toolError(request, toolName, e)
        } catch (e: IllegalStateException) {
            toolError(request, toolName, e)
        } catch (e: java.io.IOException) {
            toolError(request, toolName, e)
        } catch (e: dev.atomic.core.cli.AtomicCliException) {
            toolError(request, toolName, e)
        }
    }

    private fun toolError(request: JsonRpcRequest, toolName: String, e: Exception): JsonRpcResponse {
        val errorResult = McpToolCallResult(
            content = listOf(McpToolContent(text = "Error executing tool '$toolName': ${e.message}")),
            isError = true,
        )
        return JsonRpcResponse(id = request.id, result = json.encodeToJsonElement(errorResult))
    }

    private fun tool(
        name: String,
        description: String,
        properties: List<ToolProperty>,
        required: List<String> = emptyList(),
    ) = McpToolDefinition(
        name = name,
        description = description,
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                for (p in properties) {
                    putJsonObject(p.name) {
                        put("type", p.type)
                        if (p.arrayItemType != null) {
                            putJsonObject("items") { put("type", p.arrayItemType) }
                        }
                        put("description", p.description)
                    }
                }
            }
            if (required.isNotEmpty()) {
                putJsonArray("required") { required.forEach { add(it) } }
            }
        },
    )

    private fun listTools(): List<McpToolDefinition> = listOf(
        tool(
            "atomic_status",
            "Get working tree status, modified/added/deleted files, and the active view.",
            listOf(repoPathProperty),
        ),
        tool(
            "atomic_diff",
            "Inspect patch differences for working copy files or specific paths against recorded state.",
            listOf(
                repoPathProperty,
                ToolProperty("paths", "List of specific file paths to diff", type = "array", arrayItemType = TYPE_STRING),
            ),
        ),
        tool(
            "atomic_log",
            "Query patch history and view Blake3 change hashes.",
            listOf(
                repoPathProperty,
                ToolProperty("path", "Optional file path to filter history"),
                ToolProperty("maxCount", "Maximum number of change summaries to return (default: 50)", type = "integer"),
            ),
        ),
        tool(
            "atomic_change",
            "Get full details and diff patch for a specific Blake3 change hash.",
            listOf(repoPathProperty, ToolProperty(CHANGE_HASH, "The Blake3 change hash to inspect")),
            required = listOf(CHANGE_HASH),
        ),
        tool(
            "atomic_query",
            "Perform semantic graph and intent queries on the Atomic repository.",
            listOf(repoPathProperty, ToolProperty("query", "Natural language or symbol query")),
            required = listOf("query"),
        ),
        tool(
            "atomic_vault_context",
            "Read repository vault context, project memory, and developer intent guidelines.",
            listOf(repoPathProperty, ToolProperty("intent", "Specific intent or goal filter")),
        ),
        tool(
            "atomic_provenance",
            "Inspect provenance metadata and attestation for AI-generated changes.",
            listOf(repoPathProperty, ToolProperty(CHANGE_HASH, "Blake3 change hash to inspect")),
            required = listOf(CHANGE_HASH),
        ),
        tool(
            "atomic_session_start",
            "Start an isolated AI agent session, creating and switching to an isolated draft view.",
            listOf(
                ToolProperty(SESSION_ID, "Unique identifier for this AI session"),
                repoPathProperty,
                ToolProperty("parentView", "Optional parent view to branch from"),
            ),
            required = listOf(SESSION_ID),
        ),
        tool(
            "atomic_session_turn_end",
            "Record working copy modifications made during the current AI turn onto the draft view.",
            listOf(
                ToolProperty(SESSION_ID, "Active AI session ID"),
                ToolProperty("message", "Description of what was done in this turn"),
                repoPathProperty,
            ),
            required = listOf(SESSION_ID, "message"),
        ),
        tool(
            "atomic_session_end",
            "Conclude the AI agent session, attest changes, and restore the original view.",
            listOf(ToolProperty(SESSION_ID, "Active AI session ID"), repoPathProperty),
            required = listOf(SESSION_ID),
        ),
        tool(
            "atomic_attest",
            "Cryptographically attest a view or change set.",
            listOf(repoPathProperty, ToolProperty("viewName", "Optional view name to attest")),
        ),
    )

    private suspend fun executeTool(name: String, args: JsonObject): McpToolCallResult {
        val repoPath = resolveRepoPath(args[REPO_PATH]?.jsonPrimitive?.contentOrNull)

        val text = when (name) {
            "atomic_status" -> statusText(repoPath)
            "atomic_diff" -> diffText(repoPath, args)
            "atomic_log" -> logText(repoPath, args)
            "atomic_change" -> changeText(repoPath, args.required(CHANGE_HASH))
            "atomic_query" -> queryText(repoPath, args.required("query"))
            "atomic_vault_context" -> {
                val context = cli.vaultContext(repoPath, args.optional("intent"))
                context.ifEmpty { "No vault context available for this repository." }
            }
            "atomic_provenance" -> provenanceText(repoPath, args.required(CHANGE_HASH))
            "atomic_session_start" -> sessionStartText(repoPath, args)
            "atomic_session_turn_end" -> {
                val sessionId = args.required(SESSION_ID)
                val result = sessionTracker.recordTurn(sessionId, args.required("message"), repoPath)
                "Turn recorded for session '$sessionId' [hash: ${result.changeHash}]: ${result.message} " +
                    "(${result.filesRecorded.size} files)"
            }
            "atomic_session_end" -> sessionEndText(repoPath, args.required(SESSION_ID))
            "atomic_attest" -> {
                val attestation = cli.attest(repoPath, args.optional("viewName"))
                "Attestation: ${attestation.hash} (Status: ${attestation.status}, Timestamp: ${attestation.timestamp})"
            }
            else -> throw IllegalArgumentException("Unknown tool: $name")
        }
        return McpToolCallResult(listOf(McpToolContent(text = text)))
    }

    private fun JsonObject.required(key: String): String =
        this[key]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing $key")

    private fun JsonObject.optional(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private suspend fun statusText(repoPath: Path): String {
        val status = cli.status(repoPath)
        return buildString {
            appendLine("Active View: ${status.currentView}")
            appendSection("Modified", "M", status.modified)
            appendSection("Added", "A", status.added)
            appendSection("Deleted", "D", status.deleted)
            appendSection("Untracked", "?", status.untracked)
            if (status.conflicted.isNotEmpty()) {
                appendSection("Conflicted", "C", status.conflicted)
            }
        }
    }

    private fun StringBuilder.appendSection(title: String, marker: String, files: List<Path>) {
        appendLine("$title: ${files.size} files")
        files.forEach { appendLine("  $marker $it") }
    }

    private suspend fun diffText(repoPath: Path, args: JsonObject): String {
        val paths = args["paths"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.map { repoPath.resolve(it).normalize() } ?: emptyList()
        return cli.diff(repoPath, paths).rawDiff.ifEmpty { "No working copy modifications detected." }
    }

    private suspend fun logText(repoPath: Path, args: JsonObject): String {
        val path = args.optional("path")?.let { repoPath.resolve(it).normalize() }
        val maxCount = args["maxCount"]?.jsonPrimitive?.intOrNull ?: 50
        val logs = cli.log(repoPath, path, maxCount)
        return buildString {
            appendLine("Change History (${logs.size} changes):")
            for (c in logs) {
                val draftTag = if (c.isDraft) " [DRAFT]" else ""
                appendLine("• ${c.hash.take(12)}$draftTag - ${c.message} (${c.author}, ${c.timestamp})")
            }
        }
    }

    private suspend fun changeText(repoPath: Path, hash: String): String {
        val detail = cli.change(repoPath, hash)
        return buildString {
            appendLine("Change: ${detail.hash}")
            appendLine("Author: ${detail.author}")
            appendLine("Date: ${detail.timestamp}")
            appendLine("Draft: ${detail.isDraft}")
            appendLine("Message:\n${detail.message}\n")
            appendLine("Changed files: ${detail.changedFiles.size}")
            for (f in detail.changedFiles) {
                appendLine("  ${f.changeType} ${f.path}")
            }
            if (detail.rawPatch.isNotEmpty()) {
                appendLine("\nPatch:\n${detail.rawPatch}")
            }
        }
    }

    private suspend fun queryText(repoPath: Path, query: String): String {
        val queryResult = cli.query(repoPath, query)
        return buildString {
            appendLine("Semantic Query Results for '${queryResult.query}':")
            for (m in queryResult.matches) {
                val lineInfo = if (m.line != null) ":${m.line}" else ""
                val scoreInfo = if (m.score != null) " (score: ${m.score})" else ""
                appendLine("• ${m.path}$lineInfo$scoreInfo: ${m.snippet}")
            }
        }
    }

    private suspend fun provenanceText(repoPath: Path, hash: String): String {
        val prov = cli.provenance(repoPath, hash)
        return buildString {
            appendLine("Provenance for $hash:")
            appendLine("Agent: ${prov.agent ?: "unknown"}")
            appendLine("Session: ${prov.sessionId ?: "none"}")
            appendLine("Turn: ${prov.turn ?: 0}")
            appendLine("Model: ${prov.model ?: "unknown"}")
            appendLine("Prompt: ${prov.prompt ?: "none"}")
            appendLine("Attestation Hash: ${prov.attestationHash ?: "unattested"}")
        }
    }

    private suspend fun sessionStartText(repoPath: Path, args: JsonObject): String {
        val sessionId = args.required(SESSION_ID)
        val result = sessionTracker.startSession(sessionId, repoPath, args.optional("parentView"))
        return "AI Session '$sessionId' started on draft view '${result.draftView}' (parent: '${result.parentView}')."
    }

    private suspend fun sessionEndText(repoPath: Path, sessionId: String): String {
        val result = sessionTracker.endSession(sessionId, repoPath)
        val fallbackNote = if (result.fallbackRecorded) " (untracked modifications captured via VFS fallback)" else ""
        return "AI Session '$sessionId' concluded. View restored to '${result.restoredView}'. " +
            "Attestation: ${result.attestation.hash}$fallbackNote"
    }

    private fun resolveRepoPath(customPath: String?): Path {
        if (!customPath.isNullOrBlank()) {
            return Paths.get(customPath).toAbsolutePath().normalize()
        }
        return defaultRepoPath
    }
}
