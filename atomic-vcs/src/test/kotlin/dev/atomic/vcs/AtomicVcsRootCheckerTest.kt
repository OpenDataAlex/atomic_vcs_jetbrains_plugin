package dev.atomic.vcs

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AtomicVcsRootCheckerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun testIsVcsDir() {
        val checker = AtomicVcsRootChecker()
        assertTrue(checker.isVcsDir(".atomic"))
        assertTrue(checker.isVcsDir("/path/to/.atomic"))
        assertTrue(checker.isVcsDir("C:\\repo\\.atomic"))
        assertFalse(checker.isVcsDir(".git"))
        assertFalse(checker.isVcsDir("src"))
    }

    @Test
    fun testGetSupportedVcs() {
        val checker = AtomicVcsRootChecker()
        assertEquals(AtomicVcs.getKey(), checker.supportedVcs)
    }
}
