package dev.atomic.core.parser

import dev.atomic.core.format.ProvenanceFormatter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Paths

/**
 * Uses output captured from `atomic` v0.20.0 for a change recorded by an agent session.
 */
class AgentProvenanceParsingTest {
    private val repoRoot = Paths.get("/repo")

    private val changeJson = """
        {
          "hash": "CPMKFQJZYL3N7UNEZP3MBYP4ITPH3WSIHIPN6R4DKFTY6SLMSXXA",
          "message": "add retry logic",
          "authors": [ { "name": "Claude Code" } ],
          "timestamp": "2026-10-25T17:09:46.353207656+00:00",
          "dependencies": [ "AZDOGK7SNLMASUKP2YX3ITSNVKUHH37MK4E332XIO2M3IBL2CRMQ" ],
          "hunks": [ { "hunk_type": "FileAdd", "path": "b.txt" }, { "hunk_type": "Edit", "path": "a.txt" } ],
          "has_provenance": true,
          "provenance": {
            "vendor": "Anthropic",
            "model": "claude-sonnet-4-5",
            "tool": "Cli(\"claude-code\")",
            "suggestion_type": "Complete",
            "tokens": { "input": 1200, "output": 800, "total": 2000 },
            "cost": { "amount_micros": 40000, "currency": "USD" },
            "timestamp": 1792948186,
            "session_id": "sess-1",
            "prompt_hash": "B4SURYZX",
            "metadata": [ { "key": "turn_number", "value": "1" }, { "key": "agent_name", "value": "claude-code" } ]
          },
          "ledger": [
            {
              "graph_hash": "KIFYFMC4",
              "session_id": "sess-1",
              "agent_name": "claude-code",
              "agent_display_name": "Claude Code",
              "agent_vendor": "anthropic",
              "nodes": [
                { "id": "sess-1", "kind": "goal", "timestamp": 1, "summary": "add retry logic", "classified": false },
                { "id": "sess-2", "kind": "patch_proposal", "timestamp": 2, "summary": "Change CPMKFQJZ: 2 files",
                  "detail": { "files": [ "b.txt", "a.txt" ] }, "classified": false }
              ],
              "edges": [ { "from": "sess-1", "to": "sess-2", "kind": "led_to" } ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesAgentAuthorFromLogJson() {
        val log = """
            [
              { "sequence": 3, "hash": "H3", "state": "S", "message": "add retry logic",
                "authors": [ { "name": "Claude Code" } ], "timestamp": "2026-10-25T17:09:46+00:00", "is_tagged": false },
              { "sequence": 2, "hash": "H2", "state": "S", "message": "base", "timestamp": "2026-10-25T17:09:45+00:00", "is_tagged": false }
            ]
        """.trimIndent()

        val changes = AtomicParsers.parseLog(log)
        assertEquals(2, changes.size)
        assertEquals("Claude Code", changes[0].author)
        assertEquals("", changes[1].author)
    }

    @Test
    fun parsesProvenanceAndLedger() {
        val detail = AtomicParsers.parseChange(changeJson, repoRoot)

        assertEquals("Claude Code", detail.author)
        val p = detail.provenance!!
        assertEquals("Anthropic", p.vendor)
        assertEquals("claude-sonnet-4-5", p.model)
        assertEquals(1200L, p.inputTokens)
        assertEquals(800L, p.outputTokens)
        assertEquals(2000L, p.totalTokens)
        assertEquals(40000L, p.costMicros)
        assertEquals("sess-1", p.sessionId)
        assertEquals("1", p.metadata["turn_number"])

        assertEquals(1, detail.ledgers.size)
        val ledger = detail.ledgers[0]
        assertEquals("Claude Code", ledger.agentDisplayName)
        assertEquals(listOf("goal", "patch_proposal"), ledger.nodes.map { it.kind })
        assertEquals("led_to", ledger.edges.single().kind)
        assertEquals(2, detail.changedFiles.size)
    }

    @Test
    fun changeWithoutAgentMetadataHasNoProvenance() {
        val plain = """
            { "hash": "H", "message": "m", "authors": [], "timestamp": "t", "dependencies": [], "hunks": [] }
        """.trimIndent()

        val detail = AtomicParsers.parseChange(plain, repoRoot)
        assertNull(detail.provenance)
        assertTrue(detail.ledgers.isEmpty())
        assertNull(ProvenanceFormatter.format(detail))
    }

    @Test
    fun formatsProvenanceText() {
        val text = ProvenanceFormatter.format(AtomicParsers.parseChange(changeJson, repoRoot))!!

        assertTrue(text.startsWith("AI provenance"))
        assertTrue(text.contains("Claude Code (anthropic)"), text)
        assertTrue(text.contains("claude-sonnet-4-5"), text)
        assertTrue(text.contains("claude-code"), text)
        assertFalse(text.contains("Cli("), text)
        assertTrue(text.contains("1200 in / 800 out / 2000 total"), text)
        assertTrue(text.contains("\$0.0400 USD"), text)
        assertTrue(text.contains("sess-1 (turn 1)"), text)
        assertTrue(text.contains("Goal:"), text)
        assertTrue(text.contains("patch_proposal: Change CPMKFQJZ: 2 files"), text)
    }
}
