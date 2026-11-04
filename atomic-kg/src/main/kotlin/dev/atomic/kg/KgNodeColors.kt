package dev.atomic.kg

import java.awt.Color

internal object KgNodeColors {
    private val VIEW = Color(0xC5, 0xE1, 0xA5)
    private val GOAL = Color(0xFF, 0xCC, 0x80)
    private val INTENT = Color(0xCE, 0x93, 0xD8)
    private val CHANGE = Color(0x90, 0xCA, 0xF9)
    private val FILE = Color(0x80, 0xDE, 0xEA)
    private val ENTITY = Color(0xFF, 0xF5, 0x9D)
    private val MEMORY = Color(0xB0, 0xBE, 0xC5)
    private val UNKNOWN = Color(0xEE, 0xEE, 0xEE)
    private val SELECTED_BORDER = Color(0x15, 0x65, 0xC0)
    private val DEFAULT_BORDER = Color(0x55, 0x6B, 0x82)

    fun fill(type: String): Color =
        when (normalize(type)) {
            "view" -> VIEW
            "goal" -> GOAL
            "intent" -> INTENT
            "change" -> CHANGE
            "file" -> FILE
            "entity" -> ENTITY
            "memory" -> MEMORY
            else -> UNKNOWN
        }

    fun border(selected: Boolean): Color = if (selected) SELECTED_BORDER else DEFAULT_BORDER

    private fun normalize(type: String): String = type.substringBefore(":").trim().lowercase()
}
