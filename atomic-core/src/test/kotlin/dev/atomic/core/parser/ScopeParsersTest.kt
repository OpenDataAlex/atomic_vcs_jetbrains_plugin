package dev.atomic.core.parser

import dev.atomic.core.model.AtomicScopeSummary
import dev.atomic.core.model.ScopeFileStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScopeParsersTest {
    private val stat = "        | 1 +\n z.txt  | 1 +\n 2 files changed, 2 insertions(+), 0 deletions(-)\n"

    @Test
    fun parsesNameStatusAndStatTotals() {
        val summary = ScopeParsers.parseScope("dev", "agent-x", "M  b.txt\nA  z.txt\n", stat)

        assertEquals(listOf("b.txt", "z.txt"), summary.files.map { it.path })
        assertEquals(listOf(ScopeFileStatus.MODIFIED, ScopeFileStatus.ADDED), summary.files.map { it.status })
        assertEquals(2, summary.insertions)
        assertEquals(0, summary.deletions)
        assertEquals("2 files changed (+2 / -0)", summary.headline())
        assertTrue(summary.warnings().isEmpty())
    }

    @Test
    fun deduplicatesRepeatedPathsAndIgnoresNoise() {
        val summary = ScopeParsers.parseScope("a", "b", "A  z.txt\nM  z.txt\nM  z.txt\nnot a status line\n", "")

        assertEquals(1, summary.files.size)
        assertEquals(0, summary.insertions)
    }

    @Test
    fun emptyOutputGivesEmptySummary() {
        val summary = ScopeParsers.parseScope("dev", "agent-x", "", "")

        assertTrue(summary.isEmpty)
        assertEquals("No changes between 'dev' and 'agent-x'", summary.headline())
    }

    @Test
    fun warnsAboutDeletionsRenamesAndLargeChanges() {
        val summary = ScopeParsers.parseScope(
            "dev",
            "agent-x",
            "D  old.kt\nR100  moved.kt\nM  keep.kt\n",
            " 3 files changed, 400 insertions(+), 200 deletions(-)\n",
        )

        val warnings = summary.warnings()
        assertEquals(3, warnings.size)
        assertTrue(warnings[0].startsWith("1 deleted: old.kt"))
        assertTrue(warnings[1].startsWith("1 renamed"))
        assertTrue(warnings[2].contains("600 lines"))
        assertFalse(AtomicScopeSummary("a", "b").warnings().isNotEmpty())
    }

    @Test
    fun parsesInsertSuccess() {
        val out = "ℹ Inserting change ABC...\n\n✓ Inserted 1 change(s)\n  New state: IBJE7OBN\n"
        val result = ScopeParsers.parseInsert("ABC", "dev", out)

        assertTrue(result.inserted)
        assertEquals("IBJE7OBN", result.newState)
    }

    @Test
    fun parsesInsertNoOp() {
        val result = ScopeParsers.parseInsert("ABC", "dev", "ℹ Inserting change ABC...\n\n⚠ No changes inserted.\n")

        assertFalse(result.inserted)
        assertNull(result.newState)
    }
}
