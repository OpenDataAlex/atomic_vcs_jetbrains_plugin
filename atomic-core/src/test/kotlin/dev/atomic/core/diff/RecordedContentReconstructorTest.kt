package dev.atomic.core.diff

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Inputs mirror `atomic diff --json <file>` output from atomic v0.20.0.
 */
class RecordedContentReconstructorTest {
    private fun line(status: String, content: String, old: Int?, new: Int?) =
        """{ "status": "$status", "content": "$content", "old_line": ${old ?: "null"}, "new_line": ${new ?: "null"} }"""

    private fun diff(status: String, vararg lines: String) = """
        { "schema_version": 1, "view": "dev", "change": null,
          "files": [ { "path": "f", "status": "$status", "binary": false,
            "hunks": [ { "old_start": 1, "old_count": 1, "new_start": 1, "new_count": 1, "lines": [ ${lines.joinToString(",")} ] } ] } ] }
        """.trimIndent()

    @Test
    fun replacedLineAndAppendedClass() {
        val json = diff(
            "modified",
            line("removed", "testing changes", 1, null),
            line("added", "class TestDiffClass", null, 1),
            line("added", "", null, 2),
            line("added", "    def __init__(self):", null, 3),
            line("added", "", null, 4),
            line("added", "        pass", null, 5),
        )
        val current = "class TestDiffClass\n\n    def __init__(self):\n\n        pass\n"

        assertEquals("testing changes\n", RecordedContentReconstructor.reconstruct(json, current))
    }

    @Test
    fun skippedUnchangedLineInsideHunkIsRestoredFromCurrentText() {
        // Real output: line l9 is not listed between context l8 and the added l10.
        val json = diff(
            "modified",
            line("removed", "l5", 5, null),
            line("added", "CHG", null, 5),
            line("context", "l6", 6, 6),
            line("context", "l7", 7, 7),
            line("context", "l8", 8, 8),
            line("added", "l10", null, 10),
        )
        val current = "l1\nl2\nl3\nl4\nCHG\nl6\nl7\nl8\nl9\nl10\n"

        assertEquals("l1\nl2\nl3\nl4\nl5\nl6\nl7\nl8\nl9\n", RecordedContentReconstructor.reconstruct(json, current))
    }

    @Test
    fun removedLineAtTheStartAndAppendedLines() {
        val json = diff(
            "modified",
            line("removed", "l2", 2, null),
            line("context", "l3", 3, 2),
            line("context", "l4", 4, 3),
            line("context", "l5", 5, 4),
        )
        val current = "l1\nl3\nl4\nl5\nl6\n"

        assertEquals("l1\nl2\nl3\nl4\nl5\nl6\n", RecordedContentReconstructor.reconstruct(json, current))
    }

    @Test
    fun deletedFileComesBackFromRemovedLines() {
        val json = diff("deleted", line("removed", "a", 1, null), line("removed", "b", 2, null), line("removed", "c", 3, null))

        assertEquals("a\nb\nc\n", RecordedContentReconstructor.reconstruct(json, null))
    }

    @Test
    fun pureDeletionInTheMiddle() {
        val json = diff("modified", line("removed", "b", 2, null), line("removed", "c", 3, null))

        assertEquals("a\nb\nc\nd\n", RecordedContentReconstructor.reconstruct(json, "a\nd\n"))
    }

    @Test
    fun unchangedFileReturnsCurrentContent() {
        assertEquals("same\n", RecordedContentReconstructor.reconstruct("""{ "files": [] }""", "same\n"))
    }

    @Test
    fun newFileHasNoRecordedVersion() {
        val json = diff("added", line("added", "x", null, 1))

        assertNull(RecordedContentReconstructor.reconstruct(json, "x\n"))
    }

    @Test
    fun garbageOutputHasNoRecordedVersion() {
        assertNull(RecordedContentReconstructor.reconstruct("not in a repository", "x\n"))
    }
}
