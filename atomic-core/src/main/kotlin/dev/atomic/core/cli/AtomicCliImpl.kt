package dev.atomic.core.cli

import dev.atomic.core.diff.RecordedContentReconstructor
import dev.atomic.core.model.*
import dev.atomic.core.parser.AtomicParsers
import dev.atomic.core.process.DefaultProcessRunner
import dev.atomic.core.process.ProcessResult
import dev.atomic.core.process.ProcessRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private const val FLAG_JSON = "--json"
private const val FLAG_FORMAT = "--format"
private const val FLAG_NO_COLOR = "--no-color"
private const val FORMAT_JSON = "json"
private const val DOT_ATOMIC_DIR = ".atomic"

class AtomicCliImpl(
    val processRunner: ProcessRunner = DefaultProcessRunner(),
    val executablePathProvider: () -> String? = { null },
) : AtomicCli {
    override suspend fun findExecutable(customPath: String?): Path? {
        val configured = customPath ?: executablePathProvider()
        if (!configured.isNullOrBlank()) {
            val p = Paths.get(expandUserHome(configured))
            if (Files.isExecutable(p)) return p
        }

        val names = executableNames()
        val pathDirs = System
            .getenv("PATH")
            .orEmpty()
            .split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .map { Paths.get(expandUserHome(it)) }

        return (pathDirs + standardDirs()).firstNotNullOfOrNull { dir -> firstExecutable(dir, names) }
    }

    private fun executableNames(): List<String> =
        if (System.getProperty("os.name", "").contains("win", ignoreCase = true)) {
            listOf("atomic.exe", "atomic.cmd", "atomic.bat", "atomic")
        } else {
            listOf("atomic")
        }

    private fun standardDirs(): List<Path> {
        val userHome = userHome()
        return listOf(
            Paths.get("/usr/local/bin"),
            Paths.get("/usr/bin"),
            Paths.get("/bin"),
            Paths.get("/opt/homebrew/bin"),
            Paths.get(userHome, ".cargo/bin"),
            Paths.get(userHome, ".local/bin"),
            Paths.get(userHome, "bin"),
        )
    }

    private fun firstExecutable(dir: Path, names: List<String>): Path? =
        names.map { dir.resolve(it) }.firstOrNull { Files.isExecutable(it) }

    private fun userHome(): String = System.getProperty("user.home") ?: System.getenv("HOME") ?: ""

    private fun expandUserHome(path: String): String {
        val trimmed = path.trim()
        return when {
            trimmed.startsWith("~/") || trimmed.startsWith("~\\") -> userHome() + trimmed.substring(1)
            trimmed == "~" -> userHome()
            else -> trimmed
        }
    }

    private suspend fun resolveExe(): String {
        val exe = findExecutable()
        return exe?.toAbsolutePath()?.toString() ?: "atomic"
    }

    suspend fun runCommand(
        repoPath: Path,
        args: List<String>,
        timeoutMillis: Long = 30_000L,
        allowFailure: Boolean = false,
    ): ProcessResult {
        val exe = resolveExe()
        val command = listOf(exe) + args
        val result = processRunner.runProcess(
            command = command,
            workingDir = repoPath,
            timeoutMillis = timeoutMillis,
        )

        if (result.isTimeout) {
            throw AtomicTimeoutException(command)
        }

        if (!allowFailure && result.exitCode != 0) {
            throw AtomicCommandException(
                command = command,
                exitCode = result.exitCode,
                stdout = result.stdout,
                stderr = result.stderr,
            )
        }

        return result
    }

    override suspend fun isRepository(repoPath: Path): Boolean {
        if (Files.isDirectory(repoPath.resolve(DOT_ATOMIC_DIR))) {
            return true
        }
        val result = runCommand(repoPath, listOf("status"), timeoutMillis = 5_000L, allowFailure = true)
        return result.exitCode == 0
    }

    override suspend fun init(repoPath: Path): Boolean {
        val result = runCommand(repoPath, listOf("init"), allowFailure = true)
        return result.exitCode == 0
    }

    override suspend fun status(repoPath: Path): AtomicStatusResult {
        // Try with --json flag first
        val output = jsonOrPlain(repoPath, listOf("status", FLAG_JSON), listOf("status", "--short", FLAG_NO_COLOR))
        return AtomicParsers.parseStatus(output, repoPath)
    }

    override suspend fun recordedContent(repoPath: Path, file: Path): String? {
        val absolute = if (file.isAbsolute) file else repoPath.resolve(file)
        val relative = relativeOrAbsolute(repoPath, absolute)
        val current = if (Files.isRegularFile(absolute)) {
            readTextOrNull(absolute) ?: return null
        } else {
            null
        }
        val result = runCommand(repoPath, listOf("diff", FLAG_JSON, FLAG_NO_COLOR, "--", relative))
        return RecordedContentReconstructor.reconstruct(result.stdout, current)
    }

    private fun relativeOrAbsolute(repoPath: Path, absolute: Path): String =
        try {
            repoPath
                .toAbsolutePath()
                .normalize()
                .relativize(absolute.toAbsolutePath().normalize())
                .toString()
        } catch (_: IllegalArgumentException) {
            absolute.toString()
        }

    private fun readTextOrNull(file: Path): String? =
        try {
            Files.readString(file)
        } catch (_: java.io.IOException) {
            null
        }

    /** Runs the JSON form of a command and falls back to the plain form if the CLI rejects it. */
    private suspend fun jsonOrPlain(repoPath: Path, jsonArgs: List<String>, plainArgs: List<String>): String {
        val result = runCommand(repoPath, jsonArgs, allowFailure = true)
        return if (result.exitCode == 0) result.stdout else runCommand(repoPath, plainArgs).stdout
    }

    override suspend fun diff(repoPath: Path, paths: List<Path>): AtomicDiffResult {
        val args = mutableListOf("diff", FLAG_NO_COLOR)
        if (paths.isNotEmpty()) {
            args.addAll(paths.map { repoPath.relativize(it).toString() })
        }
        val result = runCommand(repoPath, args)
        return AtomicParsers.parseDiff(result.stdout, repoPath)
    }

    override suspend fun log(repoPath: Path, path: Path?, maxCount: Int): List<AtomicChangeSummary> {
        val pathArgs = if (path != null) listOf("--path", toRepoRelative(repoPath, path)) else emptyList()
        val output = jsonOrPlain(
            repoPath,
            listOf("log", "-n", maxCount.toString(), FLAG_FORMAT, FORMAT_JSON) + pathArgs,
            listOf("log", "-n", maxCount.toString()) + pathArgs,
        )
        return AtomicParsers.parseLog(output)
    }

    override suspend fun changeGraph(repoPath: Path, maxCount: Int): List<AtomicChangeSummary> {
        val result = runCommand(
            repoPath,
            listOf("log", "--all", "-n", maxCount.toString(), FLAG_FORMAT, FORMAT_JSON),
            allowFailure = true,
        )
        val changes = AtomicParsers.parseLog(if (result.exitCode == 0) result.stdout else "")
        if (changes.isEmpty()) return log(repoPath, null, maxCount)

        val gate = Semaphore(8)
        return coroutineScope {
            changes
                .map { summary ->
                    async {
                        gate.withPermit { summary.copy(dependencies = dependenciesOf(repoPath, summary)) }
                    }
                }.awaitAll()
        }
    }

    private suspend fun dependenciesOf(repoPath: Path, summary: AtomicChangeSummary): List<String> =
        try {
            val detail = runCommand(repoPath, listOf("change", summary.hash, FLAG_FORMAT, FORMAT_JSON), allowFailure = true)
            if (detail.exitCode == 0) AtomicParsers.parseChange(detail.stdout, repoPath).parents else summary.dependencies
        } catch (e: CancellationException) {
            throw e
        } catch (_: AtomicCliException) {
            summary.dependencies
        }

    override suspend fun viewHeads(repoPath: Path): Map<String, String> {
        val heads = linkedMapOf<String, String>()
        for (view in listViews(repoPath)) {
            val result = runCommand(
                repoPath,
                listOf("log", "--view", view.name, "-n", "1", FLAG_FORMAT, FORMAT_JSON),
                allowFailure = true,
            )
            if (result.exitCode != 0) continue
            AtomicParsers.parseLog(result.stdout).firstOrNull()?.let { heads[view.name] = it.hash }
        }
        return heads
    }

    override suspend fun change(repoPath: Path, changeHash: String): AtomicChangeDetail {
        val output = jsonOrPlain(
            repoPath,
            listOf("change", changeHash, FLAG_FORMAT, FORMAT_JSON),
            listOf("change", changeHash),
        )
        return AtomicParsers.parseChange(output, repoPath)
    }

    override suspend fun record(repoPath: Path, message: String, paths: List<Path>): AtomicRecordResult {
        // `atomic record` has no --json flag (v0.20), so only the plain text form is used.
        val args = mutableListOf("record", "-m", message)
        if (paths.isNotEmpty()) {
            args.add("--")
            args.addAll(paths.map { toRepoRelative(repoPath, it) })
        }
        val result = runCommand(repoPath, args)
        val raw = result.stdout + "\n" + result.stderr
        val command = (listOf(resolveExe()) + args).joinToString(" ") { if (it.any(Char::isWhitespace)) "'$it'" else it }
        return AtomicParsers.parseRecord(raw, repoPath).copy(
            command = "$command (cwd=$repoPath, exit=${result.exitCode})",
            rawOutput = raw.trim(),
        )
    }

    private fun toRepoRelative(repoPath: Path, path: Path): String {
        val abs = if (path.isAbsolute) path.normalize() else repoPath.resolve(path).normalize()
        val root = repoPath.toAbsolutePath().normalize()
        return if (abs.startsWith(root)) root.relativize(abs).toString() else abs.toString()
    }

    override suspend fun unrecord(repoPath: Path, changeHash: String?) {
        val args = mutableListOf("unrecord")
        if (!changeHash.isNullOrBlank()) {
            args.add(changeHash)
        }
        runCommand(repoPath, args)
    }

    override suspend fun restore(repoPath: Path, paths: List<Path>) {
        if (paths.isEmpty()) return
        val args = mutableListOf("restore")
        args.addAll(paths.map { repoPath.relativize(it).toString() })
        runCommand(repoPath, args)
    }

    override suspend fun add(repoPath: Path, paths: List<Path>) {
        if (paths.isEmpty()) return
        val args = mutableListOf("add")
        args.addAll(paths.map { repoPath.relativize(it).toString() })
        runCommand(repoPath, args)
    }

    override suspend fun listViews(repoPath: Path): List<AtomicView> {
        val output = jsonOrPlain(repoPath, listOf("view", "list", FLAG_JSON), listOf("view", "list"))
        return AtomicParsers.parseViews(output)
    }

    override suspend fun switchView(repoPath: Path, viewName: String) {
        runCommand(repoPath, listOf("view", "switch", viewName))
    }

    override suspend fun createDraftView(repoPath: Path, viewName: String, parentView: String?): AtomicView {
        val args = mutableListOf("view", "create", "--draft", viewName)
        if (parentView != null) {
            args.add(parentView)
        }
        runCommand(repoPath, args)
        return AtomicView(name = viewName, isCurrent = false, isDraft = true, parentView = parentView)
    }

    override suspend fun query(repoPath: Path, queryString: String): AtomicQueryResult {
        val output = jsonOrPlain(repoPath, listOf("query", queryString, FLAG_JSON), listOf("query", queryString))
        return AtomicParsers.parseQuery(output, repoPath)
    }

    override suspend fun vaultContext(repoPath: Path, intent: String?): String {
        val args = mutableListOf("vault", "context")
        if (intent != null) {
            args.add(intent)
        }
        val result = runCommand(repoPath, args, allowFailure = true)
        return result.stdout.ifEmpty { result.stderr }
    }

    override suspend fun provenance(repoPath: Path, changeHash: String): AtomicProvenanceDetail {
        val output = jsonOrPlain(repoPath, listOf("provenance", changeHash, FLAG_JSON), listOf("provenance", changeHash))
        return AtomicParsers.parseProvenance(output, changeHash)
    }

    override suspend fun attest(repoPath: Path, viewName: String?): AtomicAttestation {
        val output = jsonOrPlain(
            repoPath,
            listOfNotNull("attest", FLAG_JSON, viewName),
            listOfNotNull("attest", viewName),
        )
        return AtomicParsers.parseAttestation(output)
    }
}
