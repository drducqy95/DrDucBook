package io.legado.app.ui.quickdict

import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.DisplaySourceSegment
import io.legado.app.domain.model.MappedDisplayText
import io.legado.app.domain.model.alignedParagraphMapping
import io.legado.app.domain.model.QuickDictionaryCatalog
import io.legado.app.domain.model.QuickDictionaryCatalogEntry
import io.legado.app.domain.model.QuickDictionaryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickDictionarySelectionResolverTest {

    @Test
    fun mapsTranslatedQuickTranslatorSelectionToRawPhrase() {
        val source = "秦老看着叶长生说道"
        val display = "Tần lão nhìn Diệp Trường Sinh nói"
        val selected = "Diệp Trường Sinh"
        val start = display.indexOf(selected)

        val anchor = resolveQuickDictionarySelection(
            request = QuickDictionaryRequest(
                bookUrl = "book",
                selectedText = selected,
                sourceText = source,
                displayText = display,
                selectionStart = start,
                selectionEnd = start + selected.length,
                sourceLocation = "",
            ),
            quickTranslationGateway = FakeQuickTranslationGateway(),
            candidateTranslator = { candidate ->
                if (candidate == "叶长生") selected else candidate
            },
            candidatePhoneticReader = { candidate -> candidate },
        )

        assertEquals("叶长生", anchor?.rawText)
    }

    @Test
    fun explicitProvenanceWinsWithoutRetranslatingTheWholeDisplay() {
        val source = "\u524d\u6587\u53f6\u957f\u751f\u6765\u4e86"
        val display = "Mo dau Diep Truong Sinh den roi"
        val selected = "Diep Truong Sinh"
        val displayStart = display.indexOf(selected)
        val sourceStart = source.indexOf("\u53f6\u957f\u751f")
        val request = QuickDictionaryRequest(
            bookUrl = "book",
            selectedText = selected,
            sourceText = source,
            displayText = display,
            selectionStart = displayStart,
            selectionEnd = displayStart + selected.length,
            sourceLocation = "",
            mappedDisplayText = MappedDisplayText(
                sourceText = source,
                displayText = display,
                engine = "qt",
                segments = listOf(
                    DisplaySourceSegment(
                        sourceStart = sourceStart,
                        sourceEnd = sourceStart + 3,
                        displayStart = displayStart,
                        displayEnd = displayStart + selected.length,
                        confidence = 1f,
                    )
                ),
            ),
        )

        val anchor = resolveQuickDictionarySelection(request, FakeQuickTranslationGateway())

        assertEquals("\u53f6\u957f\u751f", anchor?.rawText)
    }

    @Test
    fun lowConfidenceParagraphMappingRequiresConfirmationInsteadOfSavingWrongRaw() {
        val source = "\u7b2c\u4e00\u4e2a\u540d\u5b57 \u7b2c\u4e8c\u4e2a\u540d\u5b57"
        val display = "Ten da bien tap hoan toan"
        val selected = "bien tap"
        val start = display.indexOf(selected)
        val request = QuickDictionaryRequest(
            bookUrl = "book",
            selectedText = selected,
            sourceText = source,
            displayText = display,
            selectionStart = start,
            selectionEnd = start + selected.length,
            sourceLocation = "",
            mappedDisplayText = alignedParagraphMapping(source, display, "ai"),
        )

        val resolution = resolveQuickDictionarySelectionResult(
            request = request,
            quickTranslationGateway = FakeQuickTranslationGateway(),
            candidateTranslator = { "unrelated" },
            candidatePhoneticReader = { "unrelated" },
        )

        assertNull(resolution.anchor)
        assertTrue(resolution.requiresConfirmation)
        assertEquals(source, resolution.alternatives.single().rawText)
    }

    @Test
    fun mapsSelectionInsideAlignedParagraphInsteadOfWholeChapterRatio() {
        val source = "甲甲甲甲甲甲甲甲甲甲\n叶长生来了"
        val display = "Một đoạn mở đầu rất dài sau khi dịch\nDiệp Trường Sinh đến rồi"
        val selected = "Diệp Trường Sinh"
        val start = display.indexOf(selected)

        val anchor = resolveQuickDictionarySelection(
            request = QuickDictionaryRequest(
                bookUrl = "book",
                selectedText = selected,
                sourceText = source,
                displayText = display,
                selectionStart = start,
                selectionEnd = start + selected.length,
                sourceLocation = "",
            ),
            quickTranslationGateway = FakeQuickTranslationGateway(),
            candidateTranslator = { candidate ->
                when (candidate) {
                    "叶长生" -> selected
                    "叶长生来" -> "$selected đến"
                    else -> candidate
                }
            },
            candidatePhoneticReader = { candidate -> candidate },
        )

        assertEquals("叶长生", anchor?.rawText)
    }

    @Test
    fun keepsExactRangeWhenSourceAndDisplayAreSame() {
        val source = "前文 叶长生 来了"
        val selected = "叶长生"
        val start = source.indexOf(selected)

        val anchor = resolveQuickDictionarySelection(
            request = QuickDictionaryRequest(
                bookUrl = "book",
                selectedText = selected,
                sourceText = source,
                displayText = source,
                selectionStart = start,
                selectionEnd = start + selected.length,
                sourceLocation = "",
            ),
            quickTranslationGateway = FakeQuickTranslationGateway(),
        )

        assertEquals("叶长生", anchor?.rawText)
    }

    @Test
    fun doesNotGuessNearbyRawWhenTranslatedSelectionCannotBeMatched() {
        val source = "\u795E\u667A\uFF0C\u4ED6\u7A81\u7136\u610F\u8BC6\u5230"
        val display = "Binh phuc tinh nhan, anh ay dot nhien nhan ra"
        val selected = "tinh nhan"
        val start = display.indexOf(selected)

        val anchor = resolveQuickDictionarySelection(
            request = QuickDictionaryRequest(
                bookUrl = "book",
                selectedText = selected,
                sourceText = source,
                displayText = display,
                selectionStart = start,
                selectionEnd = start + selected.length,
                sourceLocation = "",
            ),
            quickTranslationGateway = FakeQuickTranslationGateway(),
            candidateTranslator = { candidate ->
                when (candidate) {
                    "\u795E\u667A" -> "than tri"
                    "\u610F\u8BC6\u5230" -> "nhan ra"
                    else -> candidate
                }
            },
            candidatePhoneticReader = { candidate -> candidate },
        )

        assertEquals(null, anchor)
    }

    @Test
    fun mapsTranslatedSelectionWhenDisplayHasDifferentParagraphStructure() {
        val rawName = "\u53f6\u957f\u751f"
        val source = "\u524d\u6587\u5f88\u77ed\n${rawName}\u6765\u4e86"
        val selected = "Diep Truong Sinh"
        val display = "Doan mo dau dai hon\nco them mot dong\n$selected den roi"
        val start = display.indexOf(selected)

        val anchor = resolveQuickDictionarySelection(
            request = QuickDictionaryRequest(
                bookUrl = "book",
                selectedText = selected,
                sourceText = source,
                displayText = display,
                selectionStart = start,
                selectionEnd = start + selected.length,
                sourceLocation = "",
            ),
            quickTranslationGateway = FakeQuickTranslationGateway(),
            candidateTranslator = { candidate ->
                if (candidate == rawName) selected else candidate
            },
            candidatePhoneticReader = { candidate -> candidate },
        )

        assertEquals(rawName, anchor?.rawText)
    }

    @Test
    fun mapsSelectionWhenReaderDisplayIncludesChapterTitle() {
        val rawName = "\u53f6\u957f\u751f"
        val source = "Chapter title\n${rawName}\u6765\u4e86"
        val display = "Chapter title\nDiep Truong Sinh den roi"
        val selected = "Diep Truong Sinh"
        val start = display.indexOf(selected)

        val anchor = resolveQuickDictionarySelection(
            request = QuickDictionaryRequest(
                bookUrl = "book",
                selectedText = selected,
                sourceText = source,
                displayText = display,
                selectionStart = start,
                selectionEnd = start + selected.length,
                sourceLocation = "",
            ),
            quickTranslationGateway = FakeQuickTranslationGateway(),
            candidateTranslator = { candidate ->
                if (candidate == rawName) selected else candidate
            },
            candidatePhoneticReader = { candidate -> candidate },
        )

        assertEquals(rawName, anchor?.rawText)
    }

    @Test
    fun mapsTranslatedReaderTitleToRawTitle() {
        val rawTitle = "\u7b2c\u4e00\u7ae0"
        val displayTitle = "Chuong mot"
        val source = "$rawTitle\n\u53f6\u957f\u751f\u6765\u4e86"
        val display = "$displayTitle\nDiep Truong Sinh den roi"

        val anchor = resolveQuickDictionarySelection(
            request = QuickDictionaryRequest(
                bookUrl = "book",
                selectedText = displayTitle,
                sourceText = source,
                displayText = display,
                selectionStart = 0,
                selectionEnd = displayTitle.length,
                sourceLocation = "",
            ),
            quickTranslationGateway = FakeQuickTranslationGateway(),
            candidateTranslator = { candidate ->
                if (candidate == rawTitle) displayTitle else candidate
            },
            candidatePhoneticReader = { candidate -> candidate },
        )

        assertEquals(rawTitle, anchor?.rawText)
    }

    @Test
    fun mapsOnlyTheRawCharactersCoveredByASelectedTranslatedSubphrase() {
        val rawName = "\u53f6\u957f\u751f"
        val source = "${rawName}\u6765\u4e86"
        val display = "Diep Truong Sinh den roi"
        val selected = "Truong Sinh"
        val start = display.indexOf(selected)

        val anchor = resolveQuickDictionarySelection(
            request = QuickDictionaryRequest(
                bookUrl = "book",
                selectedText = selected,
                sourceText = source,
                displayText = display,
                selectionStart = start,
                selectionEnd = start + selected.length,
                sourceLocation = "",
            ),
            quickTranslationGateway = FakeQuickTranslationGateway(),
            candidateTranslator = { candidate ->
                if (candidate == rawName) "Diep Truong Sinh" else candidate
            },
            candidatePhoneticReader = { candidate -> candidate },
        )

        assertEquals("\u957f\u751f", anchor?.rawText)
    }

    @Test
    fun mapsCorrectOccurrenceWhenNameAppearsMultipleTimesInParagraph() {
        val rawName = "叶长生"
        val source = "叶长生说了话， party 叶长生走了， party 叶长生回来了"
        val display = "Diệp Trường Sinh nói chuyện, party Diệp Trường Sinh đi rồi, party Diệp Trường Sinh trở về"
        val selected = "Diệp Trường Sinh"
        val secondStart = display.indexOf(selected, startIndex = 20)

        val anchor = resolveQuickDictionarySelection(
            request = QuickDictionaryRequest(
                bookUrl = "book",
                selectedText = selected,
                sourceText = source,
                displayText = display,
                selectionStart = secondStart,
                selectionEnd = secondStart + selected.length,
                sourceLocation = "",
            ),
            quickTranslationGateway = FakeQuickTranslationGateway(),
            candidateTranslator = { candidate ->
                if (candidate == rawName) selected else candidate
            },
            candidatePhoneticReader = { candidate -> candidate },
        )

        assertEquals(rawName, anchor?.rawText)
        assertEquals(source.indexOf(rawName, startIndex = 10), anchor?.start)
    }

    @Test
    fun mapsSelectionWhenTextContainsHtmlTagsAndExtraWhitespace() {
        val rawName = "叶长生"
        val source = "<p>  叶长生   来了  </p>"
        val display = "<p> Diệp Trường Sinh  đến  </p>"
        val selected = "Diệp Trường Sinh"
        val start = display.indexOf(selected)

        val anchor = resolveQuickDictionarySelection(
            request = QuickDictionaryRequest(
                bookUrl = "book",
                selectedText = selected,
                sourceText = source,
                displayText = display,
                selectionStart = start,
                selectionEnd = start + selected.length,
                sourceLocation = "",
            ),
            quickTranslationGateway = FakeQuickTranslationGateway(),
            candidateTranslator = { candidate ->
                if (candidate == rawName) selected else candidate
            },
            candidatePhoneticReader = { candidate -> candidate },
        )

        assertEquals(rawName, anchor?.rawText)
    }

    private class FakeQuickTranslationGateway : QuickTranslationGateway {
        override val packVersion: String = "test"

        override fun translate(
            text: String,
            projectTerms: List<DictPair>,
            customPhonetics: List<DictPair>,
        ): String = text

        override fun hanViet(
            text: String,
            customPhonetics: List<DictPair>,
        ): String = text

        override fun getBuiltInCatalogs(): List<QuickDictionaryCatalog> = emptyList()

        override fun searchBuiltInEntries(
            type: QuickDictionaryType,
            query: String,
            limit: Int,
            catalogId: String?,
        ): List<QuickDictionaryCatalogEntry> = emptyList()
    }
}
