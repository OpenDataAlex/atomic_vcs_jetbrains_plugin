package dev.atomic.vcs

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import dev.atomic.core.cli.AtomicCliException
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path

private val LOG = Logger.getInstance("dev.atomic.vcs.AtomicCliSupport")

/**
 * Runs a CLI call on the current (background) thread and returns `null` if the CLI is missing or fails.
 * The failure is logged at debug level; callers fall back to a safe default.
 */
internal fun <T> blockingCliOrNull(block: suspend () -> T): T? =
    try {
        runBlocking { block() }
    } catch (e: AtomicCliException) {
        LOG.debug("Atomic CLI call failed", e)
        null
    } catch (e: IOException) {
        LOG.debug("Atomic CLI could not be started", e)
        null
    }

internal fun String.toNioPathOrNull(): Path? =
    try {
        Path.of(this)
    } catch (_: InvalidPathException) {
        null
    }

internal fun VirtualFile.toNioPathOrNull(): Path? = path.toNioPathOrNull()
