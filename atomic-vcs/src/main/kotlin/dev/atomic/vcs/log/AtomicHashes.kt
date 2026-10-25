package dev.atomic.vcs.log

import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl

/**
 * Atomic identifies changes by base32 encoded Blake3 hashes (52 characters), but the IDE's log storage
 * requires hex-based [Hash] values. This object converts between both representations losslessly.
 */
object AtomicHashes {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** Marks a hex string that is not a decoded base32 value but the UTF-8 bytes of an arbitrary identifier. */
    private const val RAW_MARKER = "ffffffffffff"

    fun toIdeHash(atomicHash: String): Hash = HashImpl.build(toHex(atomicHash))

    fun toAtomic(hash: Hash): String = fromHex(hash.asString())

    fun toHex(atomicHash: String): String {
        val bytes = decodeBase32(atomicHash)
        if (bytes != null && encodeBase32(bytes) == atomicHash) return bytes.joinToString("") { "%02x".format(it) }
        return RAW_MARKER + atomicHash.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
    }

    fun fromHex(hex: String): String {
        val lower = hex.lowercase()
        if (lower.length % 2 != 0) return hex
        val bytes = ByteArray(lower.length / 2) { lower.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        if (lower.startsWith(RAW_MARKER)) {
            return String(bytes.copyOfRange(RAW_MARKER.length / 2, bytes.size), Charsets.UTF_8)
        }
        return encodeBase32(bytes)
    }

    private fun decodeBase32(input: String): ByteArray? {
        if (input.isEmpty()) return null
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in input) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return out.toByteArray()
    }

    private fun encodeBase32(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET[(buffer shr (bits - 5)) and 0x1F])
                bits -= 5
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        return sb.toString()
    }
}
