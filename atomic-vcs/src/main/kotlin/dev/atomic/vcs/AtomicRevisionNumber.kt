package dev.atomic.vcs

import com.intellij.openapi.vcs.history.VcsRevisionNumber

data class AtomicRevisionNumber(
    val hash: String,
    val timestamp: String = "",
) : VcsRevisionNumber {
    override fun asString(): String = hash

    override fun compareTo(other: VcsRevisionNumber?): Int {
        if (other !is AtomicRevisionNumber) return -1
        if (this.hash == other.hash) return 0
        return this.hash.compareTo(other.hash)
    }

    companion object {
        val HEAD = AtomicRevisionNumber("HEAD", "")
    }
}
