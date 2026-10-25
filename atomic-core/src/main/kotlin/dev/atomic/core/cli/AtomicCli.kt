package dev.atomic.core.cli

import dev.atomic.core.model.*
import java.nio.file.Path

interface AtomicCli {
    /**
     * Resolves the atomic executable path.
     */
    suspend fun findExecutable(customPath: String? = null): Path?

    /**
     * Checks if directory is an atomic repository root or subdirectory.
     */
    suspend fun isRepository(repoPath: Path): Boolean

    /**
     * Initializes a new Atomic repository in the specified directory.
     */
    suspend fun init(repoPath: Path): Boolean

    /**
     * Queries status of working tree.
     */
    suspend fun status(repoPath: Path): AtomicStatusResult

    /**
     * Obtains diff against current recorded state.
     */
    suspend fun diff(repoPath: Path, paths: List<Path> = emptyList()): AtomicDiffResult

    /**
     * Returns the text of [file] as of the last recorded state, or `null` if it has no recorded version
     * (new, untracked or binary file).
     */
    suspend fun recordedContent(repoPath: Path, file: Path): String?

    /**
     * Obtains log of recorded patches.
     */
    suspend fun log(repoPath: Path, path: Path? = null, maxCount: Int = 100): List<AtomicChangeSummary>

    /**
     * Obtains the full change history including the dependency edges between changes (for the graph view).
     */
    suspend fun changeGraph(repoPath: Path, maxCount: Int = 1000): List<AtomicChangeSummary>

    /**
     * Maps each view name to the hash of its most recent change (the "branch heads" of the graph).
     */
    suspend fun viewHeads(repoPath: Path): Map<String, String>

    /**
     * Obtains details and patch for a specific change hash.
     */
    suspend fun change(repoPath: Path, changeHash: String): AtomicChangeDetail

    /**
     * Records a change with a message and optional explicit path list.
     */
    suspend fun record(repoPath: Path, message: String, paths: List<Path> = emptyList()): AtomicRecordResult

    /**
     * Unrecords the last change or specified change hash from current view.
     */
    suspend fun unrecord(repoPath: Path, changeHash: String? = null)

    /**
     * Restores/reverts working copy files to their recorded state.
     */
    suspend fun restore(repoPath: Path, paths: List<Path>)

    /**
     * Adds files to tracking.
     */
    suspend fun add(repoPath: Path, paths: List<Path>)

    /**
     * Lists all views in the repository.
     */
    suspend fun listViews(repoPath: Path): List<AtomicView>

    /**
     * Switches the working tree view context.
     */
    suspend fun switchView(repoPath: Path, viewName: String)

    /**
     * Creates a new draft view optionally branched from parent.
     */
    suspend fun createDraftView(repoPath: Path, viewName: String, parentView: String? = null): AtomicView

    /**
     * Performs semantic/graph search on the repository.
     */
    suspend fun query(repoPath: Path, queryString: String): AtomicQueryResult

    /**
     * Reads vault context for current repo/intent.
     */
    suspend fun vaultContext(repoPath: Path, intent: String? = null): String

    /**
     * Queries provenance metadata for a change hash.
     */
    suspend fun provenance(repoPath: Path, changeHash: String): AtomicProvenanceDetail

    /**
     * Attests view/session with an attestation hash.
     */
    suspend fun attest(repoPath: Path, viewName: String? = null): AtomicAttestation
}
