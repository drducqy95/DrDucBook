package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTranslationRefinePipelineTest {

    @Test
    fun contextPackKeepsOnlyPresentLockedDictionaryTermsAndAddsQtDrafts() {
        val pack = AiTranslationRefinePipeline.buildContextPack(
            text = "\u53f6\u957f\u751f\u6765\u4e86\u3002\n\n\u5927\u95e8\u6253\u5f00\u3002",
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            targetLanguageName = "Vietnamese",
            context = AiTranslationChunkContext(previous = "before", next = "after"),
            dictionaries = listOf(
                DictPair("\u53f6\u957f\u751f", "Diep Truong Sinh", QuickDictionaryType.NAME),
                DictPair("\u4e0d\u5b58\u5728", "Khong ton tai", QuickDictionaryType.TERM),
            ),
            promptStages = emptyMap(),
            includeRetranslateStage = false,
            quickDraft = { "QT:$it" },
        )

        assertEquals(listOf(1, 2), pack.raw_segments.map { it.id })
        assertEquals("QT:\u53f6\u957f\u751f\u6765\u4e86\u3002", pack.raw_segments.first().qt)
        assertEquals(
            mapOf("\u53f6\u957f\u751f" to "Diep Truong Sinh"),
            pack.locked_dictionary.characters,
        )
        assertFalse(pack.locked_dictionary.glossary.containsKey("\u4e0d\u5b58\u5728"))
    }

    @Test
    fun parsesStrictJsonAndPreservesExpectedOrder() {
        val result = AiTranslationRefinePipeline.parseRefinerOutput(
            rawOutput = """
                {
                  "refined_segments": [
                    {"id": 2, "refined_translation": "Doan hai."},
                    {"id": 1, "refined_translation": "Doan mot."}
                  ],
                  "new_entities": [{"raw":"\u53f6\u957f\u751f","target":"Diep Truong Sinh","type":"character"}],
                  "relationships": [],
                  "grammar_notes": ["keep pronouns stable"]
                }
            """.trimIndent(),
            expectedIds = listOf(1, 2),
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
        )

        assertEquals("Doan mot.\n\nDoan hai.", AiTranslationRefinePipeline.assemble(result))
        assertEquals("Diep Truong Sinh", result.new_entities.single().target)
        assertEquals(listOf("keep pronouns stable"), result.grammar_notes)
    }

    @Test
    fun parsesRefinerObjectAfterProseAndUnrelatedJson() {
        val result = AiTranslationRefinePipeline.parseRefinerOutput(
            rawOutput = """
                Preliminary metadata: {"status":"draft","note":"keep {this} literal"}
                Final result:
                ```json
                {"refined_segments":[{"id":1,"refined_translation":"Doan mot."}]}
                ```
            """.trimIndent(),
            expectedIds = listOf(1),
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
        )

        assertEquals("Doan mot.", AiTranslationRefinePipeline.assemble(result))
    }

    @Test
    fun diagnosesTruncatedStructuredOutputWithoutLoggingItsContent() {
        assertEquals(
            "chars=66 json=truncated",
            AiTranslationRefinePipeline.describeJsonOutput(
                """{"refined_segments":[{"id":1,"refined_translation":"Doan { mot."}]"""
            ),
        )
        assertEquals(
            "chars=65 json=balanced",
            AiTranslationRefinePipeline.describeJsonOutput(
                """{"refined_segments":[{"id":1,"refined_translation":"Doan mot."}]}"""
            ),
        )
    }

    @Test
    fun rejectsMissingSegmentId() {
        val error = runCatching {
            AiTranslationRefinePipeline.parseRefinerOutput(
                rawOutput = """{"refined_segments":[{"id":1,"refined_translation":"Doan mot."}]}""",
                expectedIds = listOf(1, 2),
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("missing segment id"))
    }

    @Test
    fun rejectsSegmentObjectWithoutExplicitId() {
        val error = runCatching {
            AiTranslationRefinePipeline.parseRefinerOutput(
                rawOutput =
                    """{"refined_segments":[{"refined_translation":"Doan mot."}]}""",
                expectedIds = listOf(1),
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("missing id"))
    }

    @Test
    fun rejectsCjkInVietnameseOutput() {
        val error = runCatching {
            AiTranslationRefinePipeline.parseRefinerOutput(
                rawOutput = """{"refined_segments":[{"id":1,"refined_translation":"Diep \u957f Sinh"}]}""",
                expectedIds = listOf(1),
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("CJK"))
    }

    @Test
    fun rejectsLegacyResultDictionaryOutput() {
        val error = runCatching {
            AiTranslationRefinePipeline.parseRefinerOutput(
                rawOutput = "[result]\n[[P0]]\nDoan mot.\n\n[[P1]]\nDoan hai.\n[dictionary]\nA -> B",
                expectedIds = listOf(1, 2),
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("valid refiner JSON object"))
    }

    @Test
    fun parsesAndCarriesTypedStoryMemoryDelta() {
        val result = AiTranslationRefinePipeline.parseRefinerOutput(
            rawOutput = """
                {
                  "refined_segments":[{"id":1,"refined_translation":"Diep rut kiem."}],
                  "story_memory": {
                    "entities":[{"raw":"叶长生","target":"Diep Truong Sinh","type":"character","aliases":["叶兄"]}],
                    "relationships":[{"source":"叶长生","target":"大梦学宫","relationship":"member_of"}],
                    "world_building":[{"raw":"青锋剑","target":"Thanh Phong Kiem","category":"weapon"}],
                    "timeline":{"summary":"Diep gia nhap hoc cung.","events":["Rut kiem"]}
                  }
                }
            """.trimIndent(),
            expectedIds = listOf(1),
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
        )

        assertEquals("叶长生", result.story_memory?.entities?.single()?.raw)
        assertEquals("member_of", result.story_memory?.relationships?.single()?.relationship)
        assertEquals("weapon", result.story_memory?.worldBuilding?.single()?.category)
        assertEquals("Diep gia nhap hoc cung.", result.story_memory?.timeline?.summary)
    }

    @Test
    fun mergesCanonicalTopLevelMemoryWithLegacyNestedMemory() {
        val result = AiTranslationRefinePipeline.parseRefinerOutput(
            rawOutput = """
                {
                  "refined_segments":[{"id":1,"refined_translation":"Diep rut kiem."}],
                  "new_entities":[{"raw":"Diep","target":"Diep Truong Sinh","type":"character"}],
                  "relationships":[{"source":"Diep","target":"Hoc Cung","relationship":"member_of"}],
                  "world_building":[{"raw":"Thanh Phong","target":"Thanh Phong Kiem","category":"weapon"}],
                  "story_timeline":{"summary":"Diep gia nhap hoc cung.","events":["Rut kiem"]},
                  "story_memory":{"entities":[],"relationships":[],"world_building":[]},
                  "grammar_notes":[]
                }
            """.trimIndent(),
            expectedIds = listOf(1),
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
        )

        assertEquals(listOf("Diep"), result.story_memory?.entities?.map { it.raw })
        assertEquals("member_of", result.story_memory?.relationships?.single()?.relationship)
        assertEquals("weapon", result.story_memory?.worldBuilding?.single()?.category)
        assertEquals("Diep gia nhap hoc cung.", result.story_memory?.timeline?.summary)
    }

    @Test
    fun systemPromptUsesOneCanonicalMemorySchema() {
        val prompt = AiTranslationRefinePipeline.buildSystemPrompt(
            configuredPrompt = TranslationConstants.DEFAULT_PROMPT,
            targetLanguageName = "Vietnamese",
            retryInstruction = "",
            protectedInstruction = "",
        )

        assertTrue(prompt.contains("\"story_timeline\""))
        assertTrue(prompt.contains("\"world_building\""))
        assertFalse(prompt.contains("\"story_memory\""))
    }
}
