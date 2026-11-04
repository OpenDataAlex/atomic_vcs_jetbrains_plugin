package dev.atomic.vcs.actions

import dev.atomic.core.model.AtomicView

/** Chooses which views a reviewed change can be promoted into. */
object InsertTargets {
    private const val AGENT_PREFIX = "agent-"

    /** Non-draft views, excluding agent session views; the preferred view (if present) comes first. */
    fun candidates(views: List<AtomicView>, preferred: String): List<String> {
        val names = views
            .filter { !it.isDraft && !it.name.startsWith(AGENT_PREFIX) }
            .map { it.name }
            .distinct()
        return if (preferred in names) listOf(preferred) + names.filter { it != preferred } else names
    }
}
