package dev.atomic.core.format

import dev.atomic.core.model.AtomicChangeDetail
import dev.atomic.core.model.AtomicChangeProvenance
import dev.atomic.core.model.AtomicLedger
import java.util.Locale

/**
 * Renders the metadata that AI agents record with a change (model, tokens, cost, session, goal and
 * the agent's steps) as human-readable text, e.g. for the commit details in the VCS log.
 */
object ProvenanceFormatter {
    /** Returns the "AI provenance" text block, or `null` when the change carries no agent metadata. */
    fun format(detail: AtomicChangeDetail): String? {
        val provenance = detail.provenance
        val ledgers = detail.ledgers
        if (provenance == null && ledgers.isEmpty()) return null

        val lines = mutableListOf("AI provenance")
        lines += headerFields(provenance, ledgers.firstOrNull()).filter { it.isNotBlank() }
        lines += ledgers
            .flatMap { it.nodes }
            .filter { it.kind == "goal" && it.summary.isNotBlank() }
            .map { field("Goal", it.summary) }
        val steps = ledgers.flatMap { stepLines(it) }
        if (steps.isNotEmpty()) {
            lines += "  Steps:"
            lines += steps
        }
        return lines.joinToString("\n")
    }

    private fun headerFields(provenance: AtomicChangeProvenance?, ledger: AtomicLedger?): List<String> {
        val agent = ledger?.let { agentLabel(it) }.orEmpty()
        val agentField = field("Agent", agent)
        return when {
            provenance != null -> listOf(
                agentField,
                field("Vendor", provenance.vendor),
                field("Model", provenance.model),
                field("Tool", cleanTool(provenance.tool)),
                field("Type", provenance.suggestionType),
                field("Session", sessionLabel(provenance)),
                field("Tokens", tokens(provenance)),
                field("Cost", cost(provenance)),
                field("Prompt", provenance.promptHash),
            )
            ledger != null -> listOf(agentField, field("Session", ledger.sessionId))
            else -> listOf(agentField)
        }
    }

    /** `Cli("claude-code")` -> `claude-code`. */
    fun cleanTool(tool: String): String =
        Regex("""^\w+\("(.*)"\)$""").matchEntire(tool.trim())?.groupValues?.get(1) ?: tool

    /** Cost recorded in micro-units of the currency, e.g. 40000 USD micros -> `$0.0400 USD`. */
    fun cost(provenance: AtomicChangeProvenance): String {
        val micros = provenance.costMicros ?: return ""
        val amount = String.format(Locale.ROOT, "%.4f", micros / 1_000_000.0)
        val currency = provenance.currency.ifBlank { "USD" }
        return if (currency == "USD") "$$amount USD" else "$amount $currency"
    }

    private fun tokens(p: AtomicChangeProvenance): String {
        val parts = mutableListOf<String>()
        p.inputTokens?.let { parts += "$it in" }
        p.outputTokens?.let { parts += "$it out" }
        val total = p.totalTokens?.let { "$it total" }
        return (parts + listOfNotNull(total)).joinToString(" / ")
    }

    private fun sessionLabel(p: AtomicChangeProvenance): String {
        val turn = p.metadata["turn_number"]
        return when {
            p.sessionId.isBlank() -> ""
            turn.isNullOrBlank() -> p.sessionId
            else -> "${p.sessionId} (turn $turn)"
        }
    }

    private fun agentLabel(ledger: AtomicLedger): String {
        val name = ledger.agentDisplayName.ifBlank { ledger.agentName }
        return when {
            name.isBlank() -> ""
            ledger.agentVendor.isBlank() -> name
            else -> "$name (${ledger.agentVendor})"
        }
    }

    private fun stepLines(ledger: AtomicLedger): List<String> =
        ledger.nodes.filter { it.kind != "goal" }.map { node ->
            val summary = node.summary.ifBlank { node.id }
            "    ${node.kind}: $summary"
        }

    private fun field(label: String, value: String): String =
        if (value.isBlank()) "" else "  ${(label + ":").padEnd(9)} $value"
}
