package dev.atomic.core.format

import dev.atomic.core.model.AtomicChangeDetail
import dev.atomic.core.model.AtomicChangeProvenance
import dev.atomic.core.model.AtomicLedger
import dev.atomic.core.model.AtomicLedgerNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProvenanceFormatterTest {
    private fun detail(
        provenance: AtomicChangeProvenance? = null,
        ledgers: List<AtomicLedger> = emptyList(),
    ) = AtomicChangeDetail(
        hash = "H",
        message = "m",
        author = "a",
        timestamp = "t",
        provenance = provenance,
        ledgers = ledgers,
    )

    @Test
    fun changeWithoutAgentDataHasNoProvenanceBlock() {
        assertNull(ProvenanceFormatter.format(detail()))
    }

    @Test
    fun formatsProvenanceFields() {
        val text = ProvenanceFormatter.format(
            detail(
                provenance = AtomicChangeProvenance(
                    vendor = "anthropic",
                    model = "claude-opus",
                    tool = "Cli(\"claude-code\")",
                    suggestionType = "edit",
                    inputTokens = 10,
                    outputTokens = 20,
                    totalTokens = 30,
                    costMicros = 40_000,
                    currency = "USD",
                    sessionId = "s-1",
                    promptHash = "abc",
                    metadata = mapOf("turn_number" to "3"),
                ),
            ),
        )!!

        assertTrue(text.startsWith("AI provenance"))
        assertTrue(text.contains("anthropic"), text)
        assertTrue(text.contains("claude-opus"), text)
        assertTrue(text.contains("claude-code"), text)
        assertTrue(!text.contains("Cli("), text)
        assertTrue(text.contains("s-1 (turn 3)"), text)
        assertTrue(text.contains("10 in / 20 out / 30 total"), text)
        assertTrue(text.contains("$0.0400 USD"), text)
        assertTrue(text.contains("abc"), text)
    }

    @Test
    fun blankFieldsAreSkipped() {
        val text = ProvenanceFormatter.format(detail(provenance = AtomicChangeProvenance(model = "m1")))!!

        assertTrue(text.contains("Model:"), text)
        assertTrue(!text.contains("Vendor:"), text)
        assertTrue(!text.contains("Tokens:"), text)
        assertTrue(!text.contains("Cost:"), text)
    }

    @Test
    fun ledgerAddsAgentGoalAndSteps() {
        val ledger = AtomicLedger(
            sessionId = "s-1",
            agentName = "claude-code",
            agentDisplayName = "Claude Code",
            agentVendor = "Anthropic",
            nodes = listOf(
                AtomicLedgerNode("n1", "goal", "Add tests"),
                AtomicLedgerNode("n2", "patch", "Edit file"),
                AtomicLedgerNode("n3", "patch", ""),
            ),
        )

        val text = ProvenanceFormatter.format(detail(ledgers = listOf(ledger)))!!

        assertTrue(text.contains("Claude Code (Anthropic)"), text)
        assertTrue(text.contains("Goal:"), text)
        assertTrue(text.contains("Add tests"), text)
        assertTrue(text.contains("Steps:"), text)
        assertTrue(text.contains("patch: Edit file"), text)
        assertTrue(text.contains("patch: n3"), text)
        assertTrue(text.contains("s-1"), text)
    }

    @Test
    fun agentNameFallsBackWhenNoDisplayName() {
        val ledger = AtomicLedger(agentName = "cursor")

        val text = ProvenanceFormatter.format(detail(ledgers = listOf(ledger)))!!

        assertTrue(text.contains("cursor"), text)
    }

    @Test
    fun cleanToolUnwrapsTheToolConstructor() {
        assertEquals("claude-code", ProvenanceFormatter.cleanTool("Cli(\"claude-code\")"))
        assertEquals("plain", ProvenanceFormatter.cleanTool("plain"))
    }

    @Test
    fun costFormatsCurrencies() {
        assertEquals("", ProvenanceFormatter.cost(AtomicChangeProvenance()))
        assertEquals("$1.5000 USD", ProvenanceFormatter.cost(AtomicChangeProvenance(costMicros = 1_500_000, currency = "USD")))
        assertEquals("$0.0100 USD", ProvenanceFormatter.cost(AtomicChangeProvenance(costMicros = 10_000)))
        assertEquals("2.0000 EUR", ProvenanceFormatter.cost(AtomicChangeProvenance(costMicros = 2_000_000, currency = "EUR")))
    }

    @Test
    fun formatReturnsNonNullForProvenanceOnly() {
        assertNotNull(ProvenanceFormatter.format(detail(provenance = AtomicChangeProvenance())))
    }
}
