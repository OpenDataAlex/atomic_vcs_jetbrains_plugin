package dev.atomic.core.diff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Rebuilds the last recorded text of a file from the current working copy text and the
 * structured output of `atomic diff --json <file>`, by undoing the diff.
 *
 * The per-line `old_line` / `new_line` numbers are used rather than the hunk headers, because the
 * CLI's hunk headers do not always match the lines inside the hunk.
 */
object RecordedContentReconstructor {
    private class DiffLine(val status: String, val content: String, val oldLine: Int?, val newLine: Int?)

    /**
     * @param diffJson output of `atomic diff --json <file>`
     * @param current the working copy text, or `null` if the file no longer exists
     * @return the recorded text; [current] when the file is unchanged; `null` when there is no
     * recorded version (new or binary file) or the output could not be understood
     */
    fun reconstruct(diffJson: String, current: String?): String? {
        val files = parseFiles(diffJson) ?: return null
        if (files.isEmpty()) return current
        val file = files[0].jsonObject
        if (file.string("status") == "added" || file["binary"]?.toString() == "true") return null

        val diffLines = (file["hunks"] as? JsonArray)
            .orEmpty()
            .flatMap { (it.jsonObject["lines"] as? JsonArray).orEmpty() }
            .map { it.jsonObject }
            .map { DiffLine(it.string("status"), it.string("content"), it.int("old_line"), it.int("new_line")) }

        return undoDiff(diffLines, current)
    }

    private fun parseFiles(diffJson: String): JsonArray? {
        val start = diffJson.indexOf('{')
        if (start < 0) return null
        val root = try {
            Json.parseToJsonElement(diffJson.substring(start)).jsonObject
        } catch (_: IllegalArgumentException) {
            return null
        }
        return root["files"] as? JsonArray
    }

    private fun undoDiff(diffLines: List<DiffLine>, current: String?): String {
        val endsWithNewline = current.isNullOrEmpty() || current.endsWith("\n")
        val newLines = if (current.isNullOrEmpty()) emptyList() else current.removeSuffix("\n").split("\n")

        val rebuilder = Rebuilder(newLines)
        for (line in diffLines) {
            if (line.status == "removed") {
                rebuilder.applyRemoved(line)
            } else {
                rebuilder.applyKept(line)
            }
        }
        rebuilder.copyCurrentUpTo(Int.MAX_VALUE)

        val out = rebuilder.out
        if (out.isEmpty()) return ""
        return out.joinToString("\n") + if (endsWithNewline) "\n" else ""
    }

    /** Rebuilds the recorded text line by line; [next] is the next not-yet-consumed line (1-based) of the current text. */
    private class Rebuilder(private val newLines: List<String>) {
        val out = ArrayList<String>()
        private var next = 1

        fun copyCurrentUpTo(exclusive: Int) {
            while (next < exclusive && next <= newLines.size) out.add(newLines[next++ - 1])
        }

        fun applyRemoved(line: DiffLine) {
            val old = line.oldLine
            if (old != null) copyCurrentUpTo(next + (old - out.size - 1))
            out.add(line.content)
        }

        fun applyKept(line: DiffLine) {
            val n = line.newLine ?: return
            copyCurrentUpTo(n)
            if (line.status != "added") out.add(line.content)
            next = n + 1
        }
    }

    private fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
}
