package dev.atomic.core.cli

open class AtomicCliException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class AtomicNotFoundException(message: String = "Atomic CLI binary not found. Please install atomic or configure its path.") :
    AtomicCliException(message)

class AtomicCommandException(
    val command: List<String>,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    message: String = "Atomic command failed (${command.joinToString(" ")} [exit $exitCode]): $stderr",
) : AtomicCliException(message)

class AtomicTimeoutException(
    val command: List<String>,
    message: String = "Atomic command timed out: ${command.joinToString(" ")}",
) : AtomicCliException(message)
