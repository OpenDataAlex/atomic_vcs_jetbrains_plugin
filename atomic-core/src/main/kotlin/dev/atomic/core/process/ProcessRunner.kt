package dev.atomic.core.process

import java.nio.file.Path

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val isTimeout: Boolean = false,
) {
    val isSuccess: Boolean
        get() = exitCode == 0 && !isTimeout
}

interface ProcessRunner {
    suspend fun runProcess(
        command: List<String>,
        workingDir: Path? = null,
        timeoutMillis: Long = 30_000L,
        env: Map<String, String> = emptyMap(),
    ): ProcessResult
}
