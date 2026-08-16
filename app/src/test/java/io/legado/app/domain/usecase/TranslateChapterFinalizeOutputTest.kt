package io.legado.app.domain.usecase

import io.legado.app.domain.model.TranslationConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TranslateChapterFinalizeOutputTest {

    @Test
    fun vietnameseTargetReturnsDecodedLayoutText() {
        val output = finalizeAiTranslationOutput(
            translatedText = "[[P0]]\nFirst paragraph\n\n[[P1]]\nSecond paragraph",
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            encodedParagraphCount = 2,
        )

        assertEquals("First paragraph\n\nSecond paragraph", output)
        assertFalse(output.contains("[[P"))
    }

    @Test
    fun missingLayoutMarkerFallsBackToMarkerStrippedText() {
        val output = finalizeAiTranslationOutput(
            translatedText = "[[P0]]\nFirst paragraph\n\nSecond paragraph",
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            encodedParagraphCount = 2,
        )

        assertEquals("First paragraph\n\nSecond paragraph", output)
        assertFalse(output.contains("[[P"))
    }

    @Test
    fun chineseTargetStillFiltersEnglishParagraphsAfterDecoding() {
        val output = finalizeAiTranslationOutput(
            translatedText = "[[P0]]\nEnglish filler.\n\n[[P1]]\n\u4e2d\u6587\u7ed3\u679c",
            targetLanguage = "zh",
            encodedParagraphCount = 2,
        )

        assertEquals("\u4e2d\u6587\u7ed3\u679c", output)
    }

    @Test
    fun markdownFenceAroundLayoutMarkersDoesNotLeakIntoDecodedOutput() {
        val output = finalizeAiTranslationOutput(
            translatedText = "```text\n[[P0]]\nDoan mot\n\n[[P1]]\nDoan hai\n```",
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            encodedParagraphCount = 2,
        )

        assertEquals("Doan mot\n\nDoan hai", output)
        assertFalse(output.contains("```"))
    }

    @Test
    fun jsonResultPayloadIsDecodedWithoutSourceWrapper() {
        val output = finalizeAiTranslationOutput(
            translatedText = """
                {
                  "source": "\u7b2c\u4e00\u6bb5",
                  "result": "[[P0]]\nDoan mot\n\n[[P1]]\nDoan hai"
                }
            """.trimIndent(),
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            encodedParagraphCount = 2,
        )

        assertEquals("Doan mot\n\nDoan hai", output)
    }

    @Test
    fun jsonArrayResultPayloadIsMergedAndDecodedWithoutSourceWrapper() {
        val output = finalizeAiTranslationOutput(
            translatedText = """
                {
                  "source": "\u7b2c\u4e00\u6bb5",
                  "result": [
                    "[[P0]]\nDoan mot",
                    "[[P1]]\nDoan hai"
                  ]
                }
            """.trimIndent(),
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            encodedParagraphCount = 2,
        )

        assertEquals("Doan mot\n\nDoan hai", output)
    }

    @Test
    fun nestedJsonMessageContentPayloadIsDecoded() {
        val output = finalizeAiTranslationOutput(
            translatedText = """
                {
                  "message": {
                    "role": "assistant",
                    "content": "[[P0]]\nDoan mot"
                  }
                }
            """.trimIndent(),
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            encodedParagraphCount = 1,
        )

        assertEquals("Doan mot", output)
    }

    @Test
    fun openAiChoicesMessageContentPayloadIsDecoded() {
        val output = finalizeAiTranslationOutput(
            translatedText = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "[[P0]]\nDoan mot"
                      }
                    }
                  ]
                }
            """.trimIndent(),
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            encodedParagraphCount = 1,
        )

        assertEquals("Doan mot", output)
    }

    @Test
    fun geminiCandidatesPartsTextPayloadIsDecoded() {
        val output = finalizeAiTranslationOutput(
            translatedText = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "[[P0]]\nDoan mot"
                          }
                        ]
                      }
                    }
                  ]
                }
            """.trimIndent(),
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            encodedParagraphCount = 1,
        )

        assertEquals("Doan mot", output)
    }

    @Test
    fun geminiCandidatesPartsFragmentsAreConcatenatedBeforeDecoding() {
        val output = finalizeAiTranslationOutput(
            translatedText = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "[[P0]]\nDoan " },
                          { "text": "mot" }
                        ]
                      }
                    }
                  ]
                }
            """.trimIndent(),
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            encodedParagraphCount = 1,
        )

        assertEquals("Doan mot", output)
    }

    @Test
    fun openAiResponsesContentFragmentsAreConcatenatedBeforeDecoding() {
        val output = finalizeAiTranslationOutput(
            translatedText = """
                {
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        { "type": "output_text", "text": "[[P0]]\nDoan " },
                        { "type": "output_text", "text": "mot" }
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            encodedParagraphCount = 1,
        )

        assertEquals("Doan mot", output)
    }

    @Test
    fun untrustedPrefixBeforeMarkerDoesNotPolluteDecodedParagraph() {
        val output = finalizeAiTranslationOutput(
            translatedText = "NOTE [[P0]]\nDoan mot",
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            encodedParagraphCount = 1,
        )

        assertEquals("Doan mot", output)
    }
}
