package dev.atomic.core.process

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class DefaultProcessRunner(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ProcessRunner {
    override suspend fun runProcess(
        command: List<String>,
        workingDir: Path?,
        timeoutMillis: Long,
        env: Map<String, String>,
    ): ProcessResult = withContext(ioDispatcher) {
        val processBuilder = ProcessBuilder(command)
        if (workingDir != null) {
            processBuilder.directory(workingDir.toFile())
        }
        if (env.isNotEmpty()) {
            processBuilder.environment().putAll(env)
        }

        val process = processBuilder.start()
        // Never leave stdin open: a CLI that waits for input would otherwise hang until the timeout.
        runCatching { process.outputStream.close() }

        val result = withTimeoutOrNull(timeoutMillis) {
            coroutineScope {
                val stdoutDeferred = async(ioDispatcher) {
                    process.inputStream.bufferedReader().use { it.readText() }
                }
                val stderrDeferred = async(ioDispatcher) {
                    process.errorStream.bufferedReader().use { it.readText() }
                }

                val exitCode = process.waitFor()
                val stdout = stdoutDeferred.await()
                val stderr = stderrDeferred.await()

                ProcessResult(exitCode, stdout, stderr, isTimeout = false)
            }
        }

        result ?: timedOut(process, command, timeoutMillis)
    }

    private fun timedOut(process: Process, command: List<String>, timeoutMillis: Long): ProcessResult {
        process.destroyForcibly()
        try {
            process.waitFor(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return ProcessResult(
            exitCode = -1,
            stdout = "",
            stderr = "Process timed out after $timeoutMillis ms: ${command.joinToString(" ")}",
            isTimeout = true,
        )
    }
}
