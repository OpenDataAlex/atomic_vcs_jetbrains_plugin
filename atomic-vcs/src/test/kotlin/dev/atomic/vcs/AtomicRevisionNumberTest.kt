package dev.atomic.vcs

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AtomicRevisionNumberTest {
    @Test
    fun testRevisionNumberAsString() {
        val rev = AtomicRevisionNumber("bafkr4ih123456", "2026-10-24T08:00:00Z")
        assertEquals("bafkr4ih123456", rev.asString())
        assertEquals("bafkr4ih123456", rev.hash)
        assertEquals("2026-10-24T08:00:00Z", rev.timestamp)
    }

    @Test
    fun testCompareTo() {
        val rev1 = AtomicRevisionNumber("aaa")
        val rev2 = AtomicRevisionNumber("bbb")
        val rev3 = AtomicRevisionNumber("aaa")

        assertTrue(rev1 < rev2)
        assertEquals(0, rev1.compareTo(rev3))
    }
}
