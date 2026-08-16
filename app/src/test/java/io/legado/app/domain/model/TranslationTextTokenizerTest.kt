package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TranslationTextTokenizerTest {

    @Test
    fun classifiesParagraphWhitespaceProtectedAndTextTokens() {
        val layout = TranslationTextTokenizer.tokenize(
            "  第一段<em>{{name}}</em>\r\n\t第二段 https://example.com/a?q=1 ${'$'}{value} %1${'$'}s"
        )

        assertTrue(layout.tokens.any { it is TranslationTextToken.ParagraphToken })
        assertTrue(layout.tokens.any { it is TranslationTextToken.WhitespaceToken })
        assertTrue(layout.tokens.any { it is TranslationTextToken.ProtectedToken })
        assertTrue(layout.tokens.any { it is TranslationTextToken.TextToken })
        assertEquals(
            listOf("<em>", "{{name}}", "</em>", "https://example.com/a?q=1", "${'$'}{value}", "%1${'$'}s"),
            layout.tokens.filterIsInstance<TranslationTextToken.ProtectedToken>().map { it.raw },
        )
    }

    @Test
    fun renderChangesOnlyTextAndPreservesEveryStructuralByte() {
        val source = "\u00A0中文  <b>标签</b>\r\n \t下一段\u2028末段 {{keep_me}}"
        val layout = TranslationTextTokenizer.tokenize(source)

        val rendered = layout.render { text -> text.lowercase().reversed() }
        val originalStructure = layout.tokens.filterNot { it is TranslationTextToken.TextToken }
        val renderedStructure = TranslationTextTokenizer.tokenize(rendered).tokens
            .filterNot { it is TranslationTextToken.TextToken }

        assertEquals(originalStructure, renderedStructure)
    }

    @Test
    fun repeatedProtectedValuesKeepDistinctStableOccurrences() {
        val layout = TranslationTextTokenizer.tokenize(
            "<em>甲</em><em>乙</em><em>丙</em>"
        )

        assertEquals(
            listOf(0, 1, 2, 3, 4, 5),
            layout.tokens
                .filterIsInstance<TranslationTextToken.ProtectedToken>()
                .map { it.occurrence },
        )
        assertEquals(
            listOf("<em>", "</em>", "<em>", "</em>", "<em>", "</em>"),
            layout.tokens
                .filterIsInstance<TranslationTextToken.ProtectedToken>()
                .map { it.raw },
        )
        assertEquals(
            "<em>译</em><em>译</em><em>译</em>",
            layout.render { "译" },
        )
    }

    @Test
    fun randomizedRoundTripNeverChangesStructuralTokens() {
        val random = Random(20260719)
        val whitespace = listOf(" ", "  ", "\t", "\r\n", "\n\n", "\u00A0", "\u2003", "\u2029")
        val protected = listOf("<em>", "</em>", "{{name}}", "${'$'}{id}", "%s", "https://a.test/x")

        repeat(500) {
            val source = buildString {
                repeat(random.nextInt(4, 24)) {
                    when (random.nextInt(3)) {
                        0 -> append("中文ABC").append(random.nextInt(10))
                        1 -> append(whitespace.random(random))
                        else -> append(protected.random(random))
                    }
                }
            }
            val layout = TranslationTextTokenizer.tokenize(source)
            val rendered = layout.render { "V".repeat(it.length.coerceAtLeast(1)) }
            assertEquals(
                layout.tokens.filterNot { it is TranslationTextToken.TextToken },
                TranslationTextTokenizer.tokenize(rendered).tokens
                    .filterNot { it is TranslationTextToken.TextToken },
            )
        }
    }
}
