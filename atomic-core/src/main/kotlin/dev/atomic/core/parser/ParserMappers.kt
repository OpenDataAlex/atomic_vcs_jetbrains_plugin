package dev.atomic.core.parser

import dev.atomic.core.model.AtomicChangeProvenance
import dev.atomic.core.model.AtomicLedger
import dev.atomic.core.model.AtomicLedgerEdge
import dev.atomic.core.model.AtomicLedgerNode
import dev.atomic.core.model.JsonChangeProvenanceDto
import dev.atomic.core.model.JsonLedgerDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Author entries are objects like `{"name": "Claude Code"}`; plain strings are accepted too. */
internal fun authorNames(authors: List<JsonElement>): String = authors
    .mapNotNull { e ->
        when (e) {
            is JsonPrimitive -> e.content
            is JsonObject -> (e["name"] as? JsonPrimitive)?.content
            else -> null
        }?.takeIf { it.isNotBlank() }
    }.joinToString(", ")

internal fun toProvenance(dto: JsonChangeProvenanceDto?): AtomicChangeProvenance? {
    if (dto == null) return null
    return AtomicChangeProvenance(
        vendor = dto.vendor,
        model = dto.model,
        tool = dto.tool,
        suggestionType = dto.suggestionType,
        inputTokens = dto.tokens?.input,
        outputTokens = dto.tokens?.output,
        totalTokens = dto.tokens?.total,
        costMicros = dto.cost?.amountMicros,
        currency = dto.cost?.currency.orEmpty(),
        sessionId = dto.sessionId,
        promptHash = dto.promptHash,
        metadata = dto.metadata.associate { it.key to it.value },
    )
}

internal fun toLedger(dto: JsonLedgerDto) = AtomicLedger(
    sessionId = dto.sessionId,
    agentName = dto.agentName,
    agentDisplayName = dto.agentDisplayName,
    agentVendor = dto.agentVendor,
    nodes = dto.nodes.map { AtomicLedgerNode(it.id, it.kind, it.summary) },
    edges = dto.edges.map { AtomicLedgerEdge(it.from, it.to, it.kind) },
)
