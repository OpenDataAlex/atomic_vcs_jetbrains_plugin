package dev.atomic.vcs.log

import com.intellij.vcs.log.RefGroup
import com.intellij.vcs.log.VcsLogRefManager
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.VcsRefType
import java.awt.Color
import java.io.DataInput
import java.io.DataOutput

/**
 * Reference type for Atomic views, which the log shows as branch labels on the graph.
 */
object AtomicViewRefType : VcsRefType {
    private val COLOR = Color(0x7FB6E8)

    override fun isBranch(): Boolean = true

    override fun getBackgroundColor(): Color = COLOR
}

class AtomicLogRefManager : VcsLogRefManager {
    private val byName = Comparator<VcsRef> { a, b -> a.name.compareTo(b.name) }

    override fun getBranchLayoutComparator(): Comparator<VcsRef> = byName

    override fun getLabelsOrderComparator(): Comparator<VcsRef> = byName

    override fun groupForBranchFilter(refs: Collection<VcsRef>): List<RefGroup> =
        refs.sortedWith(byName).map { SingleRefGroup(it) }

    override fun groupForTable(refs: Collection<VcsRef>, compact: Boolean, showTagNames: Boolean): List<RefGroup> =
        refs.sortedWith(byName).map { SingleRefGroup(it) }

    override fun serialize(out: DataOutput, type: VcsRefType) {
        // Only one reference type exists, nothing to store.
    }

    override fun deserialize(`in`: DataInput): VcsRefType = AtomicViewRefType

    override fun isFavorite(reference: VcsRef): Boolean = false

    override fun setFavorite(reference: VcsRef, favorite: Boolean) {
        // Favorites are not supported.
    }

    private class SingleRefGroup(private val ref: VcsRef) : RefGroup {
        override fun isExpanded(): Boolean = false

        override fun getName(): String = ref.name

        override fun getRefs(): List<VcsRef> = listOf(ref)

        override fun getColors(): List<Color> = listOf(ref.type.backgroundColor)
    }
}
