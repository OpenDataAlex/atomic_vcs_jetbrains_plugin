package dev.atomic.kg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class KgEditorFocusTest {
    private val file = Paths.get("src/Main.kt")

    @Test
    fun `short identifier uses search and code`() {
        val choice = KgEditorFocus.choose(KgEditorFocusRequest("verify_token", file))
        assertEquals(KgEditorQueryKind.SEARCH_AND_CODE, choice?.kind)
        assertEquals("verify_token", choice?.query)
        assertNull(choice?.pathGlob)
    }

    @Test
    fun `long or multi-line selection uses code with file glob`() {
        val snippet = KgEditorFocus.choose(KgEditorFocusRequest("fun verify_token() {\n  return true\n}", file))
        assertEquals(KgEditorQueryKind.CODE_IN_FILE, snippet?.kind)
        assertEquals("src/Main.kt", snippet?.pathGlob)

        val longLine = "x".repeat(80)
        val choice = KgEditorFocus.choose(KgEditorFocusRequest(longLine, file))
        assertEquals(KgEditorQueryKind.CODE_IN_FILE, choice?.kind)
        assertEquals(longLine, choice?.query)
    }

    @Test
    fun `no selection uses file entities`() {
        val choice = KgEditorFocus.choose(KgEditorFocusRequest(null, file))
        assertEquals(KgEditorQueryKind.FILE_ENTITIES, choice?.kind)
        assertEquals("Main.kt", choice?.query)
    }

    @Test
    fun `no selection and no file is ignored`() {
        assertNull(KgEditorFocus.choose(KgEditorFocusRequest("  ", null)))
    }
}
