package dev.atomic.kg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class KgNodeColorsTest {
    @Test
    fun `known types use distinct fills`() {
        val fills = listOf("view", "goal", "intent", "change", "file", "entity", "memory").map(KgNodeColors::fill)
        assertEquals(fills.size, fills.toSet().size)
    }

    @Test
    fun `type prefix is case insensitive`() {
        assertEquals(KgNodeColors.fill("File"), KgNodeColors.fill("file"))
        assertEquals(KgNodeColors.fill("entity:function"), KgNodeColors.fill("entity"))
    }

    @Test
    fun `unknown types share a fallback fill`() {
        assertEquals(KgNodeColors.fill("widget"), KgNodeColors.fill(""))
        assertNotEquals(KgNodeColors.fill("file"), KgNodeColors.fill("widget"))
    }

    @Test
    fun `selected border is stronger than default`() {
        assertNotEquals(KgNodeColors.border(true), KgNodeColors.border(false))
    }
}
