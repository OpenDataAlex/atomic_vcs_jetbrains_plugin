package dev.atomic.core.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AtomicCliExceptionTest {
    @Test
    fun commandExceptionMessageContainsCommandExitCodeAndStderr() {
        val e = AtomicCommandException(listOf("atomic", "record", "-m", "x"), 2, "", "bad flag")

        assertTrue(e.message!!.contains("atomic record -m x"), e.message)
        assertTrue(e.message!!.contains("exit 2"), e.message)
        assertTrue(e.message!!.contains("bad flag"), e.message)
        assertEquals(2, e.exitCode)
        assertEquals("bad flag", e.stderr)
    }

    @Test
    fun timeoutExceptionNamesTheCommand() {
        val e = AtomicTimeoutException(listOf("atomic", "log"))

        assertTrue(e.message!!.contains("atomic log"), e.message)
    }

    @Test
    fun notFoundExceptionHasHelpfulDefaultMessage() {
        assertTrue(AtomicNotFoundException().message!!.contains("not found"))
    }

    @Test
    fun allExceptionsShareTheCliBaseType() {
        assertInstanceOf(AtomicCliException::class.java, AtomicNotFoundException())
        assertInstanceOf(AtomicCliException::class.java, AtomicCommandException(emptyList(), 1, "", ""))
        assertInstanceOf(AtomicCliException::class.java, AtomicTimeoutException(emptyList()))
    }
}
