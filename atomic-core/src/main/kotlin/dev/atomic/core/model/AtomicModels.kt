package dev.atomic.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.nio.file.Path

/**
 * Result of executing `atomic status`.
 */
data class AtomicStatusResult(
    val currentView: String,
    val modified: List<Path> = emptyList(),
    val added: List<Path> = emptyList(),
    val deleted: List<Path> = emptyList(),
    val untracked: List<Path> = emptyList(),
    val conflicted: List<Path> = emptyList(),
) {
    val isClean: Boolean
        get() = modified.isEmpty() && added.isEmpty() && deleted.isEmpty() && untracked.isEmpty() && conflicted.isEmpty()

    val allDirtyFiles: List<Path>
        get() = (modified + added + deleted + untracked + conflicted).distinct()
}

/**
 * Result of executing `atomic diff`.
 */
data class AtomicDiffResult(
    val rawDiff: String,
    val fileDiffs: List<AtomicFileDiff> = emptyList(),
)

data class AtomicFileDiff(
    val oldPath: Path?,
    val newPath: Path?,
    val hunks: List<String> = emptyList(),
)

/**
 * Summary of a patch/record from `atomic log`.
 */
data class AtomicChangeSummary(
    val hash: String, // Blake3 content hash
    val message: String,
    val author: String,
    val timestamp: String,
    val isDraft: Boolean = false,
    val sequence: Long = -1,
    val state: String = "",
    val isTagged: Boolean = false,
    /** Hashes of the changes this change depends on (the edges of the change graph). */
    val dependencies: List<String> = emptyList(),
)

/**
 * Detailed patch description from `atomic change <hash>`.
 */
data class AtomicChangeDetail(
    val hash: String,
    val message: String,
    val author: String,
    val timestamp: String,
    val isDraft: Boolean = false,
    val parents: List<String> = emptyList(),
    val changedFiles: List<AtomicChangedFile> = emptyList(),
    val rawPatch: String = "",
    /** AI attribution recorded with this change, if an agent produced it. */
    val provenance: AtomicChangeProvenance? = null,
    /** Agent session ledgers (goals, tool activity, decisions) that explain this change. */
    val ledgers: List<AtomicLedger> = emptyList(),
)

/**
 * AI attribution embedded in a change (the `provenance` block of `atomic change --format json`).
 */
data class AtomicChangeProvenance(
    val vendor: String = "",
    val model: String = "",
    val tool: String = "",
    val suggestionType: String = "",
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null,
    val costMicros: Long? = null,
    val currency: String = "",
    val sessionId: String = "",
    val promptHash: String = "",
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * An agent session's decision graph that explains one or more changes.
 */
data class AtomicLedger(
    val sessionId: String = "",
    val agentName: String = "",
    val agentDisplayName: String = "",
    val agentVendor: String = "",
    val nodes: List<AtomicLedgerNode> = emptyList(),
    val edges: List<AtomicLedgerEdge> = emptyList(),
)

data class AtomicLedgerNode(
    val id: String,
    val kind: String,
    val summary: String = "",
)

data class AtomicLedgerEdge(
    val from: String,
    val to: String,
    val kind: String = "",
)

data class AtomicChangedFile(
    val path: Path,
    val changeType: AtomicChangeType,
)

enum class AtomicChangeType {
    MODIFIED,
    ADDED,
    DELETED,
    CONFLICTED,
}

/**
 * View description from `atomic view list`.
 */
data class AtomicView(
    val name: String,
    val isCurrent: Boolean,
    val isDraft: Boolean = false,
    val parentView: String? = null,
)

/**
 * Result of `atomic record`.
 */
data class AtomicRecordResult(
    val changeHash: String,
    val message: String,
    val filesRecorded: List<Path> = emptyList(),
    /** The full command line that was executed (diagnostics). */
    val command: String = "",
    /** Raw CLI output (stdout + stderr) (diagnostics). */
    val rawOutput: String = "",
)

/**
 * Semantic query match from `atomic query`.
 */
data class AtomicQueryResult(
    val query: String,
    val matches: List<AtomicQueryMatch> = emptyList(),
)

data class AtomicQueryMatch(
    val path: Path,
    val line: Int? = null,
    val snippet: String = "",
    val score: Double? = null,
)

/**
 * Provenance metadata for AI-generated changes from `atomic provenance <hash>`.
 */
data class AtomicProvenanceDetail(
    val changeHash: String,
    val agent: String? = null,
    val sessionId: String? = null,
    val turn: Int? = null,
    val prompt: String? = null,
    val model: String? = null,
    val attestationHash: String? = null,
)

/**
 * Attestation of a completed session/view from `atomic attest`.
 */
data class AtomicAttestation(
    val hash: String,
    val timestamp: String,
    val signature: String? = null,
    val status: String = "verified",
)

// ==========================================
// JSON DTOs for CLI Serialization / Deserialization
// ==========================================

@Serializable
data class JsonStatusEntryDto(
    val path: String,
    val status: String = "",
    val code: String = "",
)

@Serializable
data class JsonStatusDto(
    val view: String = "main",
    val entries: List<JsonStatusEntryDto> = emptyList(),
    val modified: List<String> = emptyList(),
    val added: List<String> = emptyList(),
    val deleted: List<String> = emptyList(),
    val untracked: List<String> = emptyList(),
    val conflicted: List<String> = emptyList(),
)

@Serializable
data class JsonChangeSummaryDto(
    val hash: String,
    val message: String = "",
    val author: String = "",
    val authors: List<JsonElement> = emptyList(),
    val timestamp: String = "",
    @SerialName("is_draft") val isDraft: Boolean = false,
    val sequence: Long = -1,
    val state: String = "",
    @SerialName("is_tagged") val isTagged: Boolean = false,
    val dependencies: List<String> = emptyList(),
)

@Serializable
data class JsonHunkDto(
    @SerialName("hunk_type") val hunkType: String = "",
    val path: String = "",
)

@Serializable
data class JsonChangeDetailDto(
    val hash: String,
    val message: String = "",
    val author: String = "",
    val timestamp: String = "",
    @SerialName("is_draft") val isDraft: Boolean = false,
    val parents: List<String> = emptyList(),
    val authors: List<JsonElement> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val hunks: List<JsonHunkDto> = emptyList(),
    val files: List<JsonChangedFileDto> = emptyList(),
    @SerialName("raw_patch") val rawPatch: String = "",
    val provenance: JsonChangeProvenanceDto? = null,
    val ledger: List<JsonLedgerDto> = emptyList(),
)

@Serializable
data class JsonProvenanceTokensDto(
    val input: Long? = null,
    val output: Long? = null,
    val total: Long? = null,
)

@Serializable
data class JsonProvenanceCostDto(
    @SerialName("amount_micros") val amountMicros: Long? = null,
    val currency: String = "",
)

@Serializable
data class JsonMetadataEntryDto(
    val key: String = "",
    val value: String = "",
)

@Serializable
data class JsonChangeProvenanceDto(
    val vendor: String = "",
    val model: String = "",
    val tool: String = "",
    @SerialName("suggestion_type") val suggestionType: String = "",
    val tokens: JsonProvenanceTokensDto? = null,
    val cost: JsonProvenanceCostDto? = null,
    @SerialName("session_id") val sessionId: String = "",
    @SerialName("prompt_hash") val promptHash: String = "",
    val metadata: List<JsonMetadataEntryDto> = emptyList(),
)

@Serializable
data class JsonLedgerNodeDto(
    val id: String = "",
    val kind: String = "",
    val summary: String = "",
)

@Serializable
data class JsonLedgerEdgeDto(
    val from: String = "",
    val to: String = "",
    val kind: String = "",
)

@Serializable
data class JsonLedgerDto(
    @SerialName("session_id") val sessionId: String = "",
    @SerialName("agent_name") val agentName: String = "",
    @SerialName("agent_display_name") val agentDisplayName: String = "",
    @SerialName("agent_vendor") val agentVendor: String = "",
    val nodes: List<JsonLedgerNodeDto> = emptyList(),
    val edges: List<JsonLedgerEdgeDto> = emptyList(),
)

@Serializable
data class JsonChangedFileDto(
    val path: String,
    val status: String = "M",
)

@Serializable
data class JsonViewDto(
    val name: String,
    val current: Boolean = false,
    val draft: Boolean = false,
    val scope: String = "",
    val parent: String? = null,
)

@Serializable
data class JsonViewListDto(
    @SerialName("current_view") val currentView: String = "",
    val views: List<JsonViewDto> = emptyList(),
)

@Serializable
data class JsonRecordDto(
    val hash: String,
    val message: String = "",
    val files: List<String> = emptyList(),
)

@Serializable
data class JsonQueryResultDto(
    val query: String = "",
    val matches: List<JsonQueryMatchDto> = emptyList(),
)

@Serializable
data class JsonQueryMatchDto(
    val file: String,
    val line: Int? = null,
    val snippet: String = "",
    val score: Double? = null,
)

@Serializable
data class JsonProvenanceDto(
    val hash: String,
    val agent: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    val turn: Int? = null,
    val prompt: String? = null,
    val model: String? = null,
    val attestation: String? = null,
)

@Serializable
data class JsonAttestationDto(
    val hash: String,
    val timestamp: String = "",
    val signature: String? = null,
    val status: String = "verified",
)
