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

    @Test
    fun mapsSelectionWhenSourceAndDisplayLineCountsDiffer() {
        val rawLines = (1..80).map { i ->
            if (i == 40) "苏晓站在房顶上看着下方" else "这是第${i}段的内容用来填充章节"
        }
        val displayLines = (1..79).map { i ->
            if (i == 40) "Tô Hiểu đứng trên nóc nhà nhìn xuống dưới" else "Đây là nội dung đoạn thứ $i để làm đầy chương"
        }
        val source = rawLines.joinToString("\n")
        val display = displayLines.joinToString("\n")
        val selected = "Tô Hiểu"
        val start = display.indexOf(selected)

        val resolution = resolveQuickDictionarySelectionResult(
            request = QuickDictionaryRequest(
                bookUrl = "book",
                selectedText = selected,
                sourceText = source,
                displayText = display,
                selectionStart = start,
                selectionEnd = start + selected.length,
                sourceLocation = "",
                mappedDisplayText = alignedParagraphMapping(source, display, "reader"),
            ),
            quickTranslationGateway = object : QuickTranslationGateway {
                override val packVersion: String = "test"
                override fun translate(text: String, projectTerms: List<DictPair>, customPhonetics: List<DictPair>): String =
                    if (text == "苏晓") "Tô Hiểu" else text
                override fun hanViet(text: String, customPhonetics: List<DictPair>): String =
                    if (text == "苏晓") "Tô Hiểu" else if (text == "房顶") "Phòng Đỉnh" else text
                override fun getBuiltInCatalogs(): List<QuickDictionaryCatalog> = emptyList()
                override fun searchBuiltInEntries(type: QuickDictionaryType, query: String, limit: Int, catalogId: String?): List<QuickDictionaryCatalogEntry> = emptyList()
            },
            candidateTranslator = { candidate ->
                if (candidate == "苏晓") "Tô Hiểu" else candidate
            },
            candidatePhoneticReader = { candidate ->
                if (candidate == "苏晓") "tô hiểu" else candidate
            },
        )

        assertEquals("苏晓", resolution.anchor?.rawText)
        org.junit.Assert.assertFalse(resolution.requiresConfirmation)
    }

    @Test
    fun mapsSelectionWithDriftedSelectionStartDueToIndentation() {
        val source = "甲甲甲甲甲\n苏晓拔出了长刀"
        val display = "    Đoạn một có thụt lề rất dài    \n    Tô Hiểu rút ra trường đao"
        val selected = "Tô Hiểu"
        val actualStart = display.indexOf(selected)
        // Simulate a drift of +40 characters in selectionStart
        val driftedStart = (actualStart + 40).coerceAtMost(display.length)

        val anchor = resolveQuickDictionarySelection(
            request = QuickDictionaryRequest(
                bookUrl = "book",
                selectedText = selected,
                sourceText = source,
                displayText = display,
                selectionStart = driftedStart,
                selectionEnd = driftedStart + selected.length,
                sourceLocation = "",
                mappedDisplayText = alignedParagraphMapping(source, display, "reader"),
            ),
            quickTranslationGateway = object : QuickTranslationGateway {
                override val packVersion: String = "test"
                override fun translate(text: String, projectTerms: List<DictPair>, customPhonetics: List<DictPair>): String =
                    if (text == "苏晓") "Tô Hiểu" else text
                override fun hanViet(text: String, customPhonetics: List<DictPair>): String =
                    if (text == "苏晓") "tô hiểu" else text
                override fun getBuiltInCatalogs(): List<QuickDictionaryCatalog> = emptyList()
                override fun searchBuiltInEntries(type: QuickDictionaryType, query: String, limit: Int, catalogId: String?): List<QuickDictionaryCatalogEntry> = emptyList()
            },
            candidateTranslator = { candidate ->
                if (candidate == "苏晓") "Tô Hiểu" else candidate
            },
            candidatePhoneticReader = { candidate ->
                if (candidate == "苏晓") "tô hiểu" else candidate
            },
        )

        assertEquals("苏晓", anchor?.rawText)
    }

    @Test
    fun neverOffersWholeParagraphAsAlternativeWhenResolutionIsAmbiguous() {
        val longParagraph = "这是一个超过十六个字符的长段落用来测试当完全没有匹配时不会把整段当做词典候选项展示给用户"
        val display = "Đây là một đoạn văn dài hơn mười sáu ký tự để kiểm thử khi hoàn toàn không khớp thì không đưa cả đoạn làm từ điển"
        val selected = "từ điển"
        val start = display.indexOf(selected)

        val resolution = resolveQuickDictionarySelectionResult(
            request = QuickDictionaryRequest(
                bookUrl = "book",
                selectedText = selected,
                sourceText = longParagraph,
                displayText = display,
                selectionStart = start,
                selectionEnd = start + selected.length,
                sourceLocation = "",
                mappedDisplayText = alignedParagraphMapping(longParagraph, display, "reader"),
            ),
            quickTranslationGateway = FakeQuickTranslationGateway(),
            candidateTranslator = { "khong-khop" },
            candidatePhoneticReader = { "khong-khop" },
        )

        // The alternatives list must NEVER contain the full paragraph of 40+ chars
        resolution.alternatives.forEach { alternative ->
            assertTrue(
                "Alternative should not be longer than 16 chars: ${alternative.rawText}",
                alternative.rawText.length <= 16,
            )
            org.junit.Assert.assertFalse(
                "Alternative should not contain newline",
                alternative.rawText.contains('\n'),
            )
        }
    }

    @Test
    fun alignedParagraphMappingDoesNotCollapseWhenLineCountsDiffer() {
        val source = (1..80).joinToString("\n") { "段落 $it" }
        val display = (1..79).joinToString("\n") { "Đoạn $it" }

        val mapped = alignedParagraphMapping(source, display, "test")

        assertTrue("Should have multiple segments, not collapsed to 1", mapped.segments.size > 1)
        mapped.segments.forEach { segment ->
            assertTrue("Segment confidence should be >= 0.60f", segment.confidence >= 0.60f)
        }
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
