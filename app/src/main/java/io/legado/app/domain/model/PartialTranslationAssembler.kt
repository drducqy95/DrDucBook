package io.legado.app.domain.model

/**
 * Assembles mixed original/translated content as translation progresses.
 * Uses original chunk order and replaces chunks with translations when available.
 */
object PartialTranslationAssembler {

    /**
     * Assemble mixed content from original chunks and translated portions.
     *
     * @param originalChunks The original text chunks in order
     * @param translatedMap Map of chunk index to translated content
     * @return Mixed content string with translated chunks replacing originals
     */
    fun assemble(originalChunks: List<TextChunk>, translatedMap: Map<Int, String>): String {
        if (originalChunks.isEmpty()) return ""

        val result = StringBuilder()
        for (chunk in originalChunks) {
            val translated = translatedMap[chunk.index]
            val content = translated ?: ContentChunker.sourceWithLayout(chunk)
            result.append(content)
        }

        return result.toString()
    }

    /** Builds a display-only preview. Partial values are never persisted as translated chunks. */
    fun assembleStreaming(
        originalChunks: List<TextChunk>,
        translatedMap: Map<Int, String>,
        partialMap: Map<Int, String>,
    ): String {
        if (originalChunks.isEmpty()) return ""
        return buildString {
            originalChunks.forEach { chunk ->
                append(
                    translatedMap[chunk.index]
                        ?: partialMap[chunk.index]?.let { partial ->
                            ContentChunker.previewWithLayout(chunk, partial)
                        }
                        ?: ContentChunker.sourceWithLayout(chunk)
                )
            }
        }
    }

    /**
     * Check if we have any translations yet.
     */
    fun hasPartialTranslation(translatedMap: Map<Int, String>): Boolean {
        return translatedMap.isNotEmpty()
    }

    /**
     * Get the count of translated chunks vs total.
     */
    fun progress(translatedMap: Map<Int, String>, totalChunks: Int): Pair<Int, Int> {
        return Pair(translatedMap.size, totalChunks)
    }
}
