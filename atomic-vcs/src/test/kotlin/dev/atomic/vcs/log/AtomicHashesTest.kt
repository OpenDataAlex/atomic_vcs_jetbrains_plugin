package dev.atomic.vcs.log

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AtomicHashesTest {
    @Test
    fun roundTripsRealAtomicHash() {
        val hash = "O3ND23JYFPIQ5AIACK6QR33B5ZW27QPGVAD5IBKYUIGLGLCG7RGA"
        val hex = AtomicHashes.toHex(hash)
        assertEquals(64, hex.length)
        assertTrue(hex.all { it in "0123456789abcdef" })
        assertEquals(hash, AtomicHashes.fromHex(hex))
    }

    @Test
    fun roundTripsNonBase32Identifiers() {
        val id = "not-a-hash!"
        assertEquals(id, AtomicHashes.fromHex(AtomicHashes.toHex(id)))
    }

    @Test
    fun differentHashesGiveDifferentHex() {
        assertTrue(
            AtomicHashes.toHex("GQNJ5IXRFM7JESVQRIZDGBACFMNWAMD3ZBV4Z7T2BFVLWRWBCC6A") !=
                AtomicHashes.toHex("CVIROELL2N2FHSWOMELLGU6APHFQPUYCZSUJM2GOKZ3PIUFW3CXA"),
        )
    }
}
