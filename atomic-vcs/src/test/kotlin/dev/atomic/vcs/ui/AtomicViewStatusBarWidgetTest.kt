package dev.atomic.vcs.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AtomicViewStatusBarWidgetTest {
    @Test
    fun testWidgetFactoryIdAndDisplay() {
        val factory = AtomicViewStatusBarWidgetFactory()
        assertEquals("AtomicViewStatusBarWidget", factory.id)
        assertEquals("Atomic View Switcher", factory.displayName)
    }
}
