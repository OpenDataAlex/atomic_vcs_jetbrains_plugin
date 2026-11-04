package dev.atomic.core.parser

import dev.atomic.core.model.AtomicInsertResult
import dev.atomic.core.model.AtomicScopeSummary
import dev.atomic.core.model.ScopeFile
import dev.atomic.core.model.ScopeFileStatus

/** Parsers for the scope-check (`diff --stat/--name-status`) and promote (`insert`) outputs. */
object ScopeParsers {
    private val nameStatusLine = Regex("""^([A-Z])\d*\s+(.+?)\s*$""")
    private val insertionsRegex = Regex("""(\d+)\s+insertions?\(\+\)""")
    private val deletionsRegex = Regex("""(\d+)\s+deletions?\(-\)""")
    private val newStateRegex = Regex("""New state:\s*(\S+)""")
    private val insertedCountRegex = Regex("""Inserted\s+(\d+)\s+change""")

    fun parseScope(fromView: String, toView: String, nameStatus: String, stat: String): AtomicScopeSummary {
        val files = nameStatus
            .lineSequence()
            .mapNotNull { line -> nameStatusLine.matchEntire(line.trimEnd())?.let { toScopeFile(it) } }
            .distinctBy { it.path }
            .toList()
        return AtomicScopeSummary(
            fromView = fromView,
            toView = toView,
            files = files,
            insertions = insertionsRegex
                .find(stat)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull() ?: 0,
            deletions = deletionsRegex
                .find(stat)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull() ?: 0,
        )
    }

    private fun toScopeFile(match: MatchResult): ScopeFile =
        ScopeFile(ScopeFileStatus.fromLetter(match.groupValues[1][0]), match.groupValues[2])

    fun parseInsert(changeHash: String, targetView: String, output: String): AtomicInsertResult {
        val inserted = insertedCountRegex
            .find(output)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.let { it > 0 } ?: false
        return AtomicInsertResult(
            changeHash = changeHash,
            targetView = targetView,
            inserted = inserted,
            newState = newStateRegex.find(output)?.groupValues?.get(1),
            rawOutput = output.trim(),
        )
    }
}
