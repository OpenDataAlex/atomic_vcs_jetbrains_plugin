package dev.atomic.core.parser

import dev.atomic.core.model.*
import kotlinx.serialization.json.Json
import java.nio.file.Path

private const val AUTHOR_PREFIX = "Author:"
private const val DATE_PREFIX = "Date:"
private const val TIMESTAMP_PREFIX = "Timestamp:"
private const val DRAFT_MARKER = "(draft)"
private const val CHANGE_PREFIX = "Change "
private const val RECORD_PREFIX = "Record "
private const val DEFAULT_VIEW = "main"

private val sectionItemStart = Regex("^[A-Z?].*")
private val recordSummary = Regex("""^\[(\S+)\s+\d+/(\w+)]\s*(.*)$""")

private fun String.startsWithAny(vararg prefixes: String, ignoreCase: Boolean = true): Boolean =
    prefixes.any { startsWith(it, ignoreCase = ignoreCase) }

private fun String.afterColon(): String = substringAfter(":").trim()

private fun isJsonObject(text: String) = text.startsWith("{") && text.endsWith("}")

private fun isJsonArray(text: String) = text.startsWith("[") && text.endsWith("]")

/** Decodes with [block]; returns null if the text is not valid JSON of the expected shape. */
private inline fun <T> decodeOrNull(block: () -> T): T? =
    try {
        block()
    } catch (_: IllegalArgumentException) {
        null
    }

private fun changeTypeOf(code: String): AtomicChangeType =
    when (code.uppercase()) {
        "A" -> AtomicChangeType.ADDED
        "D" -> AtomicChangeType.DELETED
        "C" -> AtomicChangeType.CONFLICTED
        else -> AtomicChangeType.MODIFIED
    }

private fun sectionCodeOf(line: String): String? =
    when {
        line.startsWithAny("Modified:") -> "M"
        line.startsWithAny("Added:", "New:") -> "A"
        line.startsWithAny("Deleted:", "Removed:") -> "D"
        line.startsWithAny("Untracked:", "Unknown:") -> "U"
        line.startsWithAny("Conflicted:", "Conflicts:") -> "C"
        else -> null
    }

private fun isSectionItem(line: String): Boolean =
    line.startsWith("-") || line.startsWith("*") || (!line.contains(":") && !line.matches(sectionItemStart))

object AtomicParsers {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // ---------------------------------------------------------------- status

    /**
     * Parse output of `atomic status [--json]`.
     */
    fun parseStatus(output: String, repoRoot: Path): AtomicStatusResult {
        val trimmed = output.trim()
        if (trimmed.isEmpty()) {
            return AtomicStatusResult(currentView = DEFAULT_VIEW)
        }
        if (isJsonObject(trimmed)) {
            decodeOrNull { statusFromJson(trimmed, repoRoot) }?.let { return it }
        }
        return statusFromText(trimmed, repoRoot)
    }

    private fun statusFromJson(text: String, repoRoot: Path): AtomicStatusResult {
        val dto = json.decodeFromString<JsonStatusDto>(text)

        fun entries(vararg kinds: String) = dto.entries
            .filter { e -> kinds.any { it.equals(e.status, ignoreCase = true) || it.equals(e.code, ignoreCase = true) } }
            .map { it.path }

        fun resolve(paths: List<String>) = paths.map { repoRoot.resolve(it).normalize() }
        return AtomicStatusResult(
            currentView = dto.view,
            modified = resolve(dto.modified + entries("modified", "M", "renamed", "R", "typechange", "T")),
            added = resolve(dto.added + entries("added", "A", "new")),
            deleted = resolve(dto.deleted + entries("deleted", "D", "removed")),
            untracked = resolve(dto.untracked + entries("untracked", "?")),
            conflicted = resolve(dto.conflicted + entries("conflicted", "conflict", "C", "U", "!")),
        )
    }

    private class StatusBuckets {
        val modified = mutableListOf<Path>()
        val added = mutableListOf<Path>()
        val deleted = mutableListOf<Path>()
        val untracked = mutableListOf<Path>()
        val conflicted = mutableListOf<Path>()

        fun add(code: String, path: Path) {
            when (code) {
                "M" -> modified.add(path)
                "A" -> added.add(path)
                "D" -> deleted.add(path)
                "?", "??", "U" -> untracked.add(path)
                "C", "!" -> conflicted.add(path)
                else -> Unit
            }
        }
    }

    private fun statusFromText(text: String, repoRoot: Path): AtomicStatusResult {
        var currentView = DEFAULT_VIEW
        var currentSection: String? = null
        val buckets = StatusBuckets()

        for (line in text.lines()) {
            val l = line.trim()
            if (l.isEmpty()) continue

            val section = sectionCodeOf(l)
            when {
                l.startsWithAny("View:", "On view:") -> currentView = l.afterColon()
                section != null -> currentSection = section
                else -> {
                    val (code, filePath) = statusEntryOf(l, currentSection) ?: continue
                    buckets.add(code, repoRoot.resolve(filePath).normalize())
                }
            }
        }

        return AtomicStatusResult(
            currentView = currentView,
            modified = buckets.modified.distinct(),
            added = buckets.added.distinct(),
            deleted = buckets.deleted.distinct(),
            untracked = buckets.untracked.distinct(),
            conflicted = buckets.conflicted.distinct(),
        )
    }

    /** Returns the status code and path of a list item or compact status line (`M  src/main.rs`). */
    private fun statusEntryOf(line: String, currentSection: String?): Pair<String, String>? {
        if (currentSection != null && isSectionItem(line)) {
            val filePath = line.removePrefix("-").removePrefix("*").trim()
            if (filePath.isNotEmpty()) return currentSection to filePath
        }
        if (line.length > 2 && (line[1] == ' ' || line[2] == ' ')) {
            return line.substring(0, 2).trim().uppercase() to line.substring(2).trim()
        }
        return null
    }

    // ------------------------------------------------------------------ diff

    private class DiffAccumulator(private val repoRoot: Path) {
        val fileDiffs = mutableListOf<AtomicFileDiff>()
        private val hunks = mutableListOf<String>()
        private var oldPath: Path? = null
        private var newPath: Path? = null
        private val currentHunk = StringBuilder()

        fun accept(line: String) {
            if (line.startsWith("diff --atomic ") || line.startsWith("diff --git ") || line.startsWith("--- ")) {
                finishFile()
            }
            when {
                line.startsWith("--- ") -> oldPath = pathOf(line.removePrefix("--- a/").removePrefix("--- ")) ?: oldPath
                line.startsWith("+++ ") -> newPath = pathOf(line.removePrefix("+++ b/").removePrefix("+++ ")) ?: newPath
                line.startsWith("@@") -> {
                    flushHunk()
                    currentHunk.appendLine(line)
                }
                currentHunk.isNotEmpty() -> currentHunk.appendLine(line)
            }
        }

        fun finishFile() {
            flushHunk()
            if (oldPath != null || newPath != null || hunks.isNotEmpty()) {
                fileDiffs.add(AtomicFileDiff(oldPath, newPath, hunks.toList()))
                hunks.clear()
                oldPath = null
                newPath = null
            }
        }

        private fun pathOf(raw: String): Path? {
            val p = raw.trim()
            return if (p != "/dev/null" && p.isNotEmpty()) repoRoot.resolve(p).normalize() else null
        }

        private fun flushHunk() {
            if (currentHunk.isNotEmpty()) {
                hunks.add(currentHunk.toString())
                currentHunk.clear()
            }
        }
    }

    /**
     * Parse output of `atomic diff`.
     */
    fun parseDiff(rawDiff: String, repoRoot: Path): AtomicDiffResult {
        val accumulator = DiffAccumulator(repoRoot)
        rawDiff.lines().forEach(accumulator::accept)
        accumulator.finishFile()
        return AtomicDiffResult(rawDiff = rawDiff, fileDiffs = accumulator.fileDiffs)
    }

    // ------------------------------------------------------------------- log

    /**
     * Parse output of `atomic log [--json]`.
     */
    fun parseLog(output: String): List<AtomicChangeSummary> {
        val trimmed = output.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (isJsonArray(trimmed)) {
            decodeOrNull { logFromJson(trimmed) }?.let { return it }
        }
        return logFromText(trimmed)
    }

    private fun logFromJson(text: String): List<AtomicChangeSummary> =
        json.decodeFromString<List<JsonChangeSummaryDto>>(text).map {
            AtomicChangeSummary(
                hash = it.hash,
                message = it.message,
                author = it.author.ifEmpty { authorNames(it.authors) },
                timestamp = it.timestamp,
                isDraft = it.isDraft,
                sequence = it.sequence,
                state = it.state,
                isTagged = it.isTagged,
                dependencies = it.dependencies,
            )
        }

    private class LogTextParser {
        val results = mutableListOf<AtomicChangeSummary>()
        private var currentHash: String? = null
        private var currentAuthor = ""
        private var currentDate = ""
        private var isDraft = false
        private val messageLines = mutableListOf<String>()

        fun accept(line: String) {
            val l = line.trim()
            when {
                l.startsWithAny("Change ", "Record ", "commit ", "patch ", ignoreCase = false) -> {
                    flush()
                    currentHash = l.split(" ").getOrNull(1) ?: ""
                    isDraft = l.contains(DRAFT_MARKER, ignoreCase = true)
                }
                l.startsWithAny(AUTHOR_PREFIX) -> currentAuthor = l.afterColon()
                l.startsWithAny(DATE_PREFIX, TIMESTAMP_PREFIX) -> currentDate = l.afterColon()
                currentHash != null && line.isNotBlank() -> messageLines.add(l)
            }
        }

        fun flush() {
            val hash = currentHash ?: return
            results.add(
                AtomicChangeSummary(
                    hash = hash,
                    message = messageLines.joinToString("\n").trim(),
                    author = currentAuthor,
                    timestamp = currentDate,
                    isDraft = isDraft,
                ),
            )
            currentHash = null
            currentAuthor = ""
            currentDate = ""
            isDraft = false
            messageLines.clear()
        }
    }

    private fun logFromText(text: String): List<AtomicChangeSummary> {
        val parser = LogTextParser()
        text.lines().forEach(parser::accept)
        parser.flush()
        return parser.results
    }

    // ---------------------------------------------------------------- change

    private fun changedFilesFromHunks(hunks: List<JsonHunkDto>, repoRoot: Path): List<AtomicChangedFile> {
        val byPath = linkedMapOf<String, AtomicChangeType>()
        for (h in hunks) {
            if (h.path.isEmpty() || h.hunkType.startsWith("Dir")) continue
            val type = when {
                h.hunkType.contains("Add", ignoreCase = true) -> AtomicChangeType.ADDED
                h.hunkType.contains("Del", ignoreCase = true) -> AtomicChangeType.DELETED
                else -> AtomicChangeType.MODIFIED
            }
            if (byPath[h.path] == null || type != AtomicChangeType.MODIFIED) byPath[h.path] = type
        }
        return byPath.map { (p, t) -> AtomicChangedFile(repoRoot.resolve(p).normalize(), t) }
    }

    /**
     * Parse output of `atomic change <hash> --format json`.
     */
    fun parseChange(output: String, repoRoot: Path): AtomicChangeDetail {
        val trimmed = output.trim()
        if (isJsonObject(trimmed)) {
            decodeOrNull { changeFromJson(trimmed, repoRoot) }?.let { return it }
        }
        return changeFromText(trimmed, repoRoot)
    }

    private fun changeFromJson(text: String, repoRoot: Path): AtomicChangeDetail {
        val dto = json.decodeFromString<JsonChangeDetailDto>(text)
        val changedFiles = if (dto.files.isNotEmpty()) {
            dto.files.map { AtomicChangedFile(repoRoot.resolve(it.path).normalize(), changeTypeOf(it.status)) }
        } else {
            changedFilesFromHunks(dto.hunks, repoRoot)
        }
        return AtomicChangeDetail(
            hash = dto.hash,
            message = dto.message,
            author = dto.author.ifEmpty { authorNames(dto.authors) },
            timestamp = dto.timestamp,
            isDraft = dto.isDraft,
            parents = (dto.parents + dto.dependencies).distinct(),
            changedFiles = changedFiles,
            rawPatch = dto.rawPatch,
            provenance = toProvenance(dto.provenance),
            ledgers = dto.ledger.map { toLedger(it) },
        )
    }

    private fun changeFromText(text: String, repoRoot: Path): AtomicChangeDetail {
        var hash = ""
        var author = ""
        var date = ""
        var isDraft = false
        val messageLines = mutableListOf<String>()
        val files = mutableListOf<AtomicChangedFile>()

        for (line in text.lines()) {
            val l = line.trim()
            when {
                l.startsWithAny(CHANGE_PREFIX, RECORD_PREFIX, ignoreCase = false) -> {
                    hash = l.split(" ").getOrNull(1) ?: ""
                    isDraft = l.contains(DRAFT_MARKER, ignoreCase = true)
                }
                l.startsWithAny(AUTHOR_PREFIX) -> author = l.afterColon()
                l.startsWithAny(DATE_PREFIX) -> date = l.afterColon()
                l.startsWithAny("M ", "A ", "D ", "C ", ignoreCase = false) ->
                    files.add(AtomicChangedFile(repoRoot.resolve(l.substring(2).trim()).normalize(), changeTypeOf(l.substring(0, 1))))
                hash.isNotEmpty() && !l.startsWith("diff") && !l.startsWith("---") && !l.startsWith("+++") ->
                    messageLines.add(l)
            }
        }

        return AtomicChangeDetail(
            hash = hash,
            message = messageLines.joinToString("\n").trim(),
            author = author,
            timestamp = date,
            isDraft = isDraft,
            changedFiles = files,
            rawPatch = text,
        )
    }

    // ----------------------------------------------------------------- views

    /**
     * Parse output of `atomic view list [--json]`.
     */
    fun parseViews(output: String): List<AtomicView> {
        val trimmed = output.trim()
        if (trimmed.isEmpty()) return emptyList()

        val dtos: List<JsonViewDto>? = decodeOrNull {
            when {
                isJsonObject(trimmed) -> json.decodeFromString<JsonViewListDto>(trimmed).views
                isJsonArray(trimmed) -> json.decodeFromString<List<JsonViewDto>>(trimmed)
                else -> null
            }
        }
        if (dtos != null) {
            return dtos.map {
                AtomicView(
                    name = it.name,
                    isCurrent = it.current,
                    isDraft = it.draft || it.scope.equals("draft", ignoreCase = true),
                    parentView = it.parent,
                )
            }
        }

        // Text parsing fallback e.g. "* main", "  agent-session-1 (draft of main)"
        return trimmed.lines().mapNotNull { viewFromTextLine(it.trim()) }
    }

    private fun viewFromTextLine(l: String): AtomicView? {
        if (l.isEmpty()) return null
        val clean = l.removePrefix("*").trim()
        val name = clean.substringBefore(" ").trim()
        if (name.isEmpty()) return null

        val parent = if (clean.contains("from ") || clean.contains("of ")) {
            clean
                .substringAfter("from ")
                .substringAfter("of ")
                .substringBefore(")")
                .trim()
        } else {
            null
        }
        return AtomicView(
            name = name,
            isCurrent = l.startsWith("*"),
            isDraft = clean.contains("(draft", ignoreCase = true),
            parentView = parent,
        )
    }

    // ---------------------------------------------------------------- record

    /**
     * Parse output of `atomic record -m "..." [--json]`.
     */
    fun parseRecord(output: String, repoRoot: Path): AtomicRecordResult {
        val trimmed = output.trim()
        if (isJsonObject(trimmed)) {
            decodeOrNull { recordFromJson(trimmed, repoRoot) }?.let { return it }
        }
        return recordFromText(trimmed, repoRoot)
    }

    private fun recordFromJson(text: String, repoRoot: Path): AtomicRecordResult {
        val dto = json.decodeFromString<JsonRecordDto>(text)
        return AtomicRecordResult(
            changeHash = dto.hash,
            message = dto.message,
            filesRecorded = dto.files.map { repoRoot.resolve(it).normalize() },
        )
    }

    /**
     * Understands the v0.20 format (`[dev 1/65PYZBZJ45YG] message` followed by one ` path` line per file)
     * and older "Recorded change 7f83b165...: message" output.
     */
    private fun recordFromText(text: String, repoRoot: Path): AtomicRecordResult {
        var hash = ""
        var msg = ""
        val files = mutableListOf<Path>()
        var afterSummary = false

        for (line in text.lines()) {
            val l = line.trim()
            val m = recordSummary.matchEntire(l)
            when {
                m != null -> {
                    hash = m.groupValues[2]
                    msg = m.groupValues[3]
                    afterSummary = true
                }
                afterSummary -> if (l.isNotEmpty()) files.add(repoRoot.resolve(l).normalize())
                l.contains("Recorded change", ignoreCase = true) || l.contains("Record:", ignoreCase = true) -> {
                    hash = l.split(" ").find { it.length >= 12 && !it.contains(":") } ?: ""
                    msg = l.afterColon()
                }
                l.startsWithAny("A ", "M ", "D ", ignoreCase = false) -> files.add(repoRoot.resolve(l.substring(2).trim()).normalize())
            }
        }

        if (hash.isEmpty() && text.isNotEmpty()) {
            hash = text.split(" ").firstOrNull { it.length >= 8 } ?: "recorded-change"
        }

        return AtomicRecordResult(changeHash = hash, message = msg, filesRecorded = files)
    }

    // ----------------------------------------------------------------- query

    /**
     * Parse output of `atomic query <query> [--json]`.
     */
    fun parseQuery(output: String, repoRoot: Path): AtomicQueryResult {
        val trimmed = output.trim()
        if (isJsonObject(trimmed)) {
            decodeOrNull { queryFromJson(trimmed, repoRoot) }?.let { return it }
        }
        val matches = trimmed
            .lines()
            .map { it.trim() }
            .filter { it.contains(":") }
            .map { queryMatchFromLine(it, repoRoot) }
        return AtomicQueryResult(query = "", matches = matches)
    }

    private fun queryFromJson(text: String, repoRoot: Path): AtomicQueryResult {
        val dto = json.decodeFromString<JsonQueryResultDto>(text)
        return AtomicQueryResult(
            query = dto.query,
            matches = dto.matches.map {
                AtomicQueryMatch(
                    path = repoRoot.resolve(it.file).normalize(),
                    line = it.line,
                    snippet = it.snippet,
                    score = it.score,
                )
            },
        )
    }

    private fun queryMatchFromLine(line: String, repoRoot: Path): AtomicQueryMatch {
        val rest = line.substringAfter(":")
        val lineNum = rest.substringBefore(":").toIntOrNull()
        val snippet = if (lineNum != null) rest.substringAfter(":") else rest
        return AtomicQueryMatch(
            path = repoRoot.resolve(line.substringBefore(":")).normalize(),
            line = lineNum,
            snippet = snippet.trim(),
        )
    }

    // ------------------------------------------------------------ provenance

    /**
     * Parse output of `atomic provenance <hash> [--json]`.
     */
    fun parseProvenance(output: String, changeHash: String): AtomicProvenanceDetail {
        val trimmed = output.trim()
        if (isJsonObject(trimmed)) {
            decodeOrNull { provenanceFromJson(trimmed, changeHash) }?.let { return it }
        }

        var agent: String? = null
        var sessionId: String? = null
        var turn: Int? = null
        var prompt: String? = null
        var model: String? = null

        for (line in trimmed.lines()) {
            val l = line.trim()
            when {
                l.startsWithAny("Agent:") -> agent = l.afterColon()
                l.startsWithAny("Session:") -> sessionId = l.afterColon()
                l.startsWithAny("Turn:") -> turn = l.afterColon().toIntOrNull()
                l.startsWithAny("Prompt:") -> prompt = l.afterColon()
                l.startsWithAny("Model:") -> model = l.afterColon()
            }
        }

        return AtomicProvenanceDetail(
            changeHash = changeHash,
            agent = agent,
            sessionId = sessionId,
            turn = turn,
            prompt = prompt,
            model = model,
        )
    }

    private fun provenanceFromJson(text: String, changeHash: String): AtomicProvenanceDetail {
        val dto = json.decodeFromString<JsonProvenanceDto>(text)
        return AtomicProvenanceDetail(
            changeHash = dto.hash.ifEmpty { changeHash },
            agent = dto.agent,
            sessionId = dto.sessionId,
            turn = dto.turn,
            prompt = dto.prompt,
            model = dto.model,
            attestationHash = dto.attestation,
        )
    }

    // ----------------------------------------------------------- attestation

    /**
     * Parse output of `atomic attest [--json]`.
     */
    fun parseAttestation(output: String): AtomicAttestation {
        val trimmed = output.trim()
        if (isJsonObject(trimmed)) {
            decodeOrNull { attestationFromJson(trimmed) }?.let { return it }
        }

        var hash = ""
        var timestamp = ""
        for (line in trimmed.lines()) {
            val l = line.trim()
            when {
                l.startsWithAny("Attestation:", "Hash:") -> hash = l.afterColon()
                l.startsWithAny(TIMESTAMP_PREFIX, DATE_PREFIX) -> timestamp = l.afterColon()
            }
        }

        return AtomicAttestation(
            hash = hash.ifEmpty { "attestation-${System.currentTimeMillis()}" },
            timestamp = timestamp,
        )
    }

    private fun attestationFromJson(text: String): AtomicAttestation {
        val dto = json.decodeFromString<JsonAttestationDto>(text)
        return AtomicAttestation(
            hash = dto.hash,
            timestamp = dto.timestamp,
            signature = dto.signature,
            status = dto.status,
        )
    }
}
