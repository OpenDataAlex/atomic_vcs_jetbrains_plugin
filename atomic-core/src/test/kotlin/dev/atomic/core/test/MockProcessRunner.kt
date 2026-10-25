package dev.atomic.core.test

import dev.atomic.core.process.ProcessResult
import dev.atomic.core.process.ProcessRunner
import java.nio.file.Path

class MockProcessRunner : ProcessRunner {
    val recordedCommands = mutableListOf<List<String>>()
    val cannedResponses = mutableMapOf<String, ProcessResult>()
    var defaultResponse = ProcessResult(exitCode = 0, stdout = "", stderr = "")

    fun onCommand(commandPrefix: String, result: ProcessResult) {
        cannedResponses[commandPrefix] = result
    }

    override suspend fun runProcess(
        command: List<String>,
        workingDir: Path?,
        timeoutMillis: Long,
        env: Map<String, String>,
    ): ProcessResult {
        recordedCommands.add(command)
        val cmdString = command.joinToString(" ")
        for ((prefix, res) in cannedResponses) {
            if (cmdString.contains(prefix)) {
                return res
            }
        }
        return defaultResponse
    }
}
