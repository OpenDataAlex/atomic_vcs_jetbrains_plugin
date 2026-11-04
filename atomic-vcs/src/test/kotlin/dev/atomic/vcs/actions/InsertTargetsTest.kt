package dev.atomic.vcs.actions

import dev.atomic.core.model.AtomicView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InsertTargetsTest {
    private val views = listOf(
        AtomicView("dev", isCurrent = false),
        AtomicView("main", isCurrent = false),
        AtomicView("agent-s1", isCurrent = true, isDraft = true, parentView = "dev"),
        AtomicView("agent-s2", isCurrent = false),
        AtomicView("scratch", isCurrent = false, isDraft = true),
    )

    @Test
    fun excludesDraftsAndAgentViewsAndPutsPreferredFirst() {
        assertEquals(listOf("main", "dev"), InsertTargets.candidates(views, "main"))
    }

    @Test
    fun keepsOriginalOrderWhenPreferredIsMissing() {
        assertEquals(listOf("dev", "main"), InsertTargets.candidates(views, "release"))
    }

    @Test
    fun emptyWhenOnlyDraftViewsExist() {
        assertEquals(emptyList<String>(), InsertTargets.candidates(views.filter { it.isDraft }, "main"))
    }
}
