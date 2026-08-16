package io.legado.app.ui.book.read.page.provider

/** Conservative dialogue detection for reader-only styling. */
object DialogueStyleAnalyzer {

    private val quotePairs = mapOf(
        '“' to '”',
        '‘' to '’',
        '「' to '」',
        '『' to '』',
        '«' to '»',
        '"' to '"',
    )
    private val dialogueDashes = setOf('—', '–', '-')

    fun italicMask(text: String): BooleanArray {
        val mask = BooleanArray(text.length)
        markQuotedDialogue(text, mask)
        markDialogueLines(text, mask)
        return mask
    }

    private fun markQuotedDialogue(text: String, mask: BooleanArray) {
        var index = 0
        while (index < text.length) {
            val closing = quotePairs[text[index]]
            if (closing == null) {
                index++
                continue
            }
            val closingIndex = text.indexOf(closing, index + 1)
            if (closingIndex <= index + 1) {
                index++
                continue
            }
            for (position in index..closingIndex) mask[position] = true
            index = closingIndex + 1
        }
    }

    private fun markDialogueLines(text: String, mask: BooleanArray) {
        var lineStart = 0
        while (lineStart < text.length) {
            val lineEnd = text.indexOf('\n', lineStart).takeIf { it >= 0 } ?: text.length
            val contentStart = (lineStart until lineEnd).firstOrNull { !text[it].isWhitespace() }
            if (contentStart != null && text[contentStart] in dialogueDashes) {
                val next = text.getOrNull(contentStart + 1)
                if (next?.isWhitespace() == true) {
                    for (position in contentStart until lineEnd) mask[position] = true
                }
            }
            lineStart = lineEnd + 1
        }
    }
}
