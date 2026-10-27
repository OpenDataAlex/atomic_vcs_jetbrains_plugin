package dev.atomic.kg

import java.nio.file.Path

enum class KgEditorQueryKind {
    SEARCH_AND_CODE,
    CODE_IN_FILE,
    FILE_ENTITIES,
}

data class KgEditorFocusRequest(
    val selection: String?,
    val filePath: Path?,
)

data class KgEditorQueryChoice(
    val kind: KgEditorQueryKind,
    val query: String,
    val pathGlob: String? = null,
)

object KgEditorFocus {
    private val identifier = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    private const val MAX_IDENTIFIER_LENGTH = 64

    fun choose(request: KgEditorFocusRequest): KgEditorQueryChoice? {
        val selection = request.selection?.trim().orEmpty()
        return when {
            selection.isEmpty() -> {
                val file = request.filePath ?: return null
                KgEditorQueryChoice(KgEditorQueryKind.FILE_ENTITIES, query = file.fileName.toString())
            }
            isIdentifier(selection) -> KgEditorQueryChoice(KgEditorQueryKind.SEARCH_AND_CODE, query = selection)
            else -> KgEditorQueryChoice(
                kind = KgEditorQueryKind.CODE_IN_FILE,
                query = selection,
                pathGlob = request.filePath?.toString(),
            )
        }
    }

    private fun isIdentifier(text: String): Boolean =
        text.length <= MAX_IDENTIFIER_LENGTH &&
            !text.contains('\n') &&
            identifier.matches(text)
}
