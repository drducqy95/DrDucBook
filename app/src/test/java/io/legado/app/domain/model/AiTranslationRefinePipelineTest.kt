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
    fun parsesCjkBeforeQualityValidation() {
        val result = AiTranslationRefinePipeline.parseRefinerStructureOutput(
                rawOutput = """{"refined_segments":[{"id":1,"refined_translation":"Diep \u957f Sinh"}]}""",
                expectedIds = listOf(1),
            )

        assertEquals(1, result.refined_segments.size)
        val error = runCatching {
            AiTranslationRefinePipeline.validateQuality(
                result,
                TranslationConstants.TARGET_VIETNAMESE,
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
        assertFalse(prompt.contains("Application-owned translation pipeline instructions"))
    }

    @Test
    fun systemPromptPromotesConfiguredStagesInPipelineOrder() {
        val prompt = AiTranslationRefinePipeline.buildSystemPrompt(
            configuredPrompt = "",
            targetLanguageName = "Vietnamese",
            retryInstruction = "",
            protectedInstruction = "",
            promptStages = mapOf(
                TranslationPromptStage.TRANSLATE to listOf("TRANSLATE_STAGE_MARKER"),
                TranslationPromptStage.PREPARE to listOf("PREPARE_STAGE_MARKER"),
                TranslationPromptStage.FILTER to listOf("FILTER_STAGE_MARKER"),
                TranslationPromptStage.DICTIONARY to listOf("DICTIONARY_STAGE_MARKER"),
                TranslationPromptStage.RETRANSLATE to listOf("RETRANSLATE_STAGE_MARKER"),
            ),
            includeRetranslateStage = true,
        )

        assertTrue(prompt.contains("Application-owned translation pipeline instructions"))
        assertTrue(prompt.indexOf("PREPARE_STAGE_MARKER") < prompt.indexOf("FILTER_STAGE_MARKER"))
        assertTrue(prompt.indexOf("FILTER_STAGE_MARKER") < prompt.indexOf("DICTIONARY_STAGE_MARKER"))
        assertTrue(prompt.indexOf("DICTIONARY_STAGE_MARKER") < prompt.indexOf("TRANSLATE_STAGE_MARKER"))
        assertTrue(prompt.indexOf("TRANSLATE_STAGE_MARKER") < prompt.indexOf("RETRANSLATE_STAGE_MARKER"))
    }

    @Test
    fun systemPromptDoesNotIncludeRetranslateStageOutsideRetry() {
        val prompt = AiTranslationRefinePipeline.buildSystemPrompt(
            configuredPrompt = "",
            targetLanguageName = "Vietnamese",
            retryInstruction = "",
            protectedInstruction = "",
            promptStages = mapOf(
                TranslationPromptStage.TRANSLATE to listOf("TRANSLATE_STAGE_MARKER"),
                TranslationPromptStage.RETRANSLATE to listOf("RETRANSLATE_STAGE_MARKER"),
            ),
            includeRetranslateStage = false,
        )

        assertTrue(prompt.contains("TRANSLATE_STAGE_MARKER"))
        assertFalse(prompt.contains("RETRANSLATE_STAGE_MARKER"))
    }

    @Test
    fun buildUserPromptDoesNotTreatStageInstructionsAsNovelData() {
        val pack = AiTranslationRefinePipeline.buildContextPack(
            text = "Source.",
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            targetLanguageName = "Vietnamese",
            context = AiTranslationChunkContext(),
            dictionaries = emptyList(),
            promptStages = mapOf(
                TranslationPromptStage.TRANSLATE to listOf("TRANSLATE_STAGE_MARKER"),
            ),
            includeRetranslateStage = false,
            quickDraft = { "" },
        )

        assertFalse(AiTranslationRefinePipeline.buildUserPrompt(pack).contains("TRANSLATE_STAGE_MARKER"))
    }

    @Test
    fun buildUserPromptUsesCompactJsonWithoutRawQtDuplication() {
        val pack = AiTranslationRefinePipeline.buildContextPack(
            text = "Doan 1.\n\nDoan 2.",
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            targetLanguageName = "Vietnamese",
            context = AiTranslationChunkContext(),
            dictionaries = emptyList(),
            promptStages = emptyMap(),
            includeRetranslateStage = false,
            quickDraft = { "QT:$it" },
        )

        val userPrompt = AiTranslationRefinePipeline.buildUserPrompt(pack)
        assertTrue(userPrompt.contains("=== CONTEXT_PACK_JSON ==="))
        assertFalse("User prompt should not duplicate SEGMENTS_RAW_QT", userPrompt.contains("=== SEGMENTS_RAW_QT ==="))
        assertFalse("User prompt should not contain empty locked_dictionary", userPrompt.contains("\"locked_dictionary\":{\"characters\":{},\"glossary\":{}}"))
        assertFalse("User prompt should not contain empty pronouns_addressing", userPrompt.contains("\"pronouns_addressing\":{}"))
    }

    @Test
    fun contextPackInjectsForeignNameCandidatesAndHybridPronouns() {
        val storyContext = AiTranslationStoryContext(
            currentEntities = listOf(
                AiTranslationStoryEntity(raw = "蒙恩", target = "Moen", type = "character"),
                AiTranslationStoryEntity(raw = "乔恩", target = "Jon", type = "character"),
            ),
            currentRelationships = listOf(
                AiTranslationStoryRelationship(source = "安格尔", target = "乔恩", relationship = "student_of"),
            ),
        )

        val pack = AiTranslationRefinePipeline.buildContextPack(
            text = "安格尔看着乔恩。",
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            targetLanguageName = "Vietnamese",
            context = AiTranslationChunkContext(),
            storyContext = storyContext,
            dictionaries = emptyList(),
            promptStages = emptyMap(),
            includeRetranslateStage = false,
            quickDraft = { "" },
            configuredPrompt = "<vai_tro>Dịch giả kỳ huyễn phương Tây</vai_tro>",
        )

        assertTrue(pack.name_candidates.any { it["raw"] == "蒙恩" })
        assertEquals("SELF=con, OTHER=thầy", pack.pronouns_addressing["安格尔→乔恩"])
    }

    @Test
    fun validateOutputHardDetectsSelfReferencingRelationships() {
        val res = AiTranslationRefinePipeline.validateOutputHard(
            refinedSegments = listOf(AiTranslationRefinedSegment(1, "Text")),
            expectedSegmentIds = listOf(1),
            rawText = "Text",
            lockedTerms = emptyMap(),
            relationships = listOf(
                mapOf("source" to "A", "target" to "A", "relationship" to "self"),
            ),
        )

        assertTrue(res.issues.any { it.contains("Self-referencing relationship") })
        assertEquals(90, res.score)
    }

    @Test
    fun systemPromptContainsPronounAddressingRule() {
        val prompt = AiTranslationRefinePipeline.buildSystemPrompt(
            configuredPrompt = TranslationConstants.DEFAULT_PROMPT,
            targetLanguageName = "Tiếng Việt",
            retryInstruction = "",
            protectedInstruction = "",
        )
        assertTrue("System prompt should contain Hard Rule 13", prompt.contains("13. pronouns_addressing maps"))
    }

    @Test
    fun qtDraftPreProcessingSubstitutesForeignNames() {
        val storyContext = AiTranslationStoryContext(
            currentEntities = listOf(
                AiTranslationStoryEntity(raw = "汉密尔顿", target = "Hamilton", type = "character"),
            ),
        )
        val pack = AiTranslationRefinePipeline.buildContextPack(
            text = "那是汉密尔顿侦探。",
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            targetLanguageName = "Tiếng Việt",
            context = AiTranslationChunkContext(),
            storyContext = storyContext,
            dictionaries = emptyList(),
            promptStages = emptyMap(),
            includeRetranslateStage = false,
            quickDraft = { source ->
                // source received by quickDraft was pre-processed with "Hamilton"
                "Đó là $source trinh thám."
            },
            configuredPrompt = "<vai_tro>Dịch giả kỳ huyễn phương Tây</vai_tro>",
        )

        assertEquals(1, pack.raw_segments.size)
        assertEquals("那是汉密尔顿侦探。", pack.raw_segments[0].text)
        assertTrue(pack.raw_segments[0].qt.contains("Hamilton"))
    }

    @Test
    fun systemPromptContainsWebnovelSlangRule() {
        val prompt = AiTranslationRefinePipeline.buildSystemPrompt(
            configuredPrompt = TranslationConstants.DEFAULT_PROMPT,
            targetLanguageName = "Tiếng Việt",
            retryInstruction = "",
            protectedInstruction = "",
        )
        assertTrue("System prompt should contain Hard Rule 14", prompt.contains("14. Translate Chinese internet, webnovel, and pop-culture slang"))
    }

    @Test
    fun scanKinshipPronounsDetectsBrothers() {
        val source = "里昂笑道：“我亲爱的弟弟，安格尔，早安。”"
        val kinship = AiTranslationRefinePipeline.scanKinshipPronouns(
            source = source,
            entities = listOf("里昂", "安格尔"),
            isAncient = false,
        )
        assertEquals("SELF=anh, OTHER=em", kinship["里昂→安格尔"])
        assertEquals("SELF=em, OTHER=anh", kinship["安格尔→里昂"])
        assertEquals("SELF=anh, OTHER=em", kinship["里奥→安格尔"])
        assertEquals("SELF=em, OTHER=anh", kinship["安格尔→里奥"])
    }

    @Test
    fun scanKinshipPronounsChapterOneNoFalsePositivesAndMentorship() {
        val rawExcerpt = """
            帕尔夏深吸一口气，低声对迪姆说：“如果我没看错，那旌旗上的图案，似乎是蒙恩家族的族徽。”
            玛娜放下手中的竹篮，向青年骑士福礼：“午安，里奥少爷。”
            玛娜低下头，恭敬的说：“安格尔少爷正在乔恩先生那里学习。”
            推开栅栏，吊脚楼的院子里载了些蔬菜瓜果。
            里昂走近，哪怕他已经刻意收敛了步伐的力度。
            “我亲爱的弟弟，安格尔。听你口气，你莫非知道我今天会过来？”里昂笑道。
        """.trimIndent()
        val kinship = AiTranslationRefinePipeline.scanKinshipPronouns(
            source = rawExcerpt,
            entities = listOf("帕尔夏", "迪姆", "玛娜", "里奥", "里昂", "安格尔", "乔恩"),
            isAncient = false,
            genreStyle = AiTranslationRefinePipeline.GenreStyle.WESTERN,
        )
        // Lyon / Leo is older brother to Angel
        assertEquals("SELF=anh, OTHER=em", kinship["里昂→安格尔"])
        assertEquals("SELF=em, OTHER=anh", kinship["安格尔→里昂"])
        assertEquals("SELF=anh, OTHER=em", kinship["里奥→安格尔"])
        assertEquals("SELF=em, OTHER=anh", kinship["安格尔→里奥"])
        assertEquals("SELF=anh, OTHER=em", kinship["Lyon→Angel"])
        assertEquals("SELF=em, OTHER=anh", kinship["Angel→Lyon"])

        // Jon is teacher to Angel
        assertEquals("SELF=thầy, OTHER=trò", kinship["乔恩→安格尔"])
        assertEquals("SELF=trò, OTHER=thầy", kinship["安格尔→乔恩"])
        assertEquals("SELF=thầy, OTHER=trò", kinship["Jon→Angel"])
        assertEquals("SELF=trò, OTHER=thầy", kinship["Angel→Jon"])

        // No false brother pairs between Parsha, Dim, Mana and Angel
        assertFalse(kinship.containsKey("帕尔夏→安格尔"))
        assertFalse(kinship.containsKey("迪姆→安格尔"))
        assertFalse(kinship.containsKey("玛娜→安格尔"))
    }

    @Test
    fun inferPronounHintReturnsComprehensiveKinship() {
        // Siblings
        assertEquals("SELF=anh, OTHER=em", AiTranslationRefinePipeline.inferPronounHint("older brother", isAncient = false))
        assertEquals("SELF=em, OTHER=anh", AiTranslationRefinePipeline.inferPronounHint("younger brother", isAncient = false))
        assertEquals("SELF=vi huynh, OTHER=hiền đệ", AiTranslationRefinePipeline.inferPronounHint("older brother", isAncient = true))
        assertEquals("SELF=chị, OTHER=em", AiTranslationRefinePipeline.inferPronounHint("older sister", isAncient = false))
        assertEquals("SELF=em, OTHER=chị", AiTranslationRefinePipeline.inferPronounHint("younger sister", isAncient = false))

        // Uncles & Aunts
        assertEquals("SELF=bác, OTHER=cháu", AiTranslationRefinePipeline.inferPronounHint("older uncle", isAncient = false))
        assertEquals("SELF=chú, OTHER=cháu", AiTranslationRefinePipeline.inferPronounHint("paternal uncle", isAncient = false))
        assertEquals("SELF=cô, OTHER=cháu", AiTranslationRefinePipeline.inferPronounHint("paternal aunt", isAncient = false))
        assertEquals("SELF=cậu, OTHER=cháu", AiTranslationRefinePipeline.inferPronounHint("maternal uncle", isAncient = false))
        assertEquals("SELF=dì, OTHER=cháu", AiTranslationRefinePipeline.inferPronounHint("maternal aunt", isAncient = false))
        assertEquals("SELF=cháu, OTHER=chú", AiTranslationRefinePipeline.inferPronounHint("nephew", isAncient = false))

        // Grandparents
        assertEquals("SELF=ông, OTHER=cháu", AiTranslationRefinePipeline.inferPronounHint("grandfather", isAncient = false))
        assertEquals("SELF=bà, OTHER=cháu", AiTranslationRefinePipeline.inferPronounHint("grandmother", isAncient = false))
        assertEquals("SELF=lão nhân gia, OTHER=tôn nhi", AiTranslationRefinePipeline.inferPronounHint("grandfather", isAncient = true))

        // Parents
        assertEquals("SELF=cha, OTHER=con", AiTranslationRefinePipeline.inferPronounHint("father", isAncient = false))
        assertEquals("SELF=mẹ, OTHER=con", AiTranslationRefinePipeline.inferPronounHint("mother", isAncient = false))

        // Mentorship
        assertEquals("SELF=thầy, OTHER=trò", AiTranslationRefinePipeline.inferPronounHint("mentor", isAncient = false, genreStyle = AiTranslationRefinePipeline.GenreStyle.WESTERN))
        assertEquals("SELF=vi sư, OTHER=đồ nhi", AiTranslationRefinePipeline.inferPronounHint("master", isAncient = true))
    }

    @Test
    fun normalizeRawSourceRestoresMangledMiddleDot() {
        assertEquals("诺亚·帕特", AiTranslationRefinePipeline.normalizeRawSource("诺亚?帕特"))
        assertEquals("安格尔·帕特", AiTranslationRefinePipeline.normalizeRawSource("安格尔?帕特"))
        // Valid questions ending sentence are untouched
        assertEquals("你好吗?", AiTranslationRefinePipeline.normalizeRawSource("你好吗?"))
    }

    @Test
    fun lockedDictionaryRejectsCjkAndUntranslatedTargetsForVietnamese() {
        val pairs = listOf(
            DictPair("咆哮突击队", "咆哮突击队", QuickDictionaryType.TERM),
            DictPair("洛克", "洛克", QuickDictionaryType.NAME),
            DictPair("美队", "Đội trưởng Mỹ", QuickDictionaryType.NAME),
            DictPair("神盾局", "S.H.I.E.L.D.", QuickDictionaryType.TERM),
            DictPair("九头蛇", "Hydra", QuickDictionaryType.TERM),
        )
        val text = "美队带领咆哮突击队与洛克一起对抗九头蛇，神盾局在后方支援。"
        val locked = AiTranslationRefinePipeline.lockedDictionaryFor(
            sourceAndContext = text,
            dictionaries = pairs,
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
        )

        assertEquals(mapOf("美队" to "Đội trưởng Mỹ"), locked.characters)
        assertEquals(
            mapOf("九头蛇" to "Hydra", "神盾局" to "S.H.I.E.L.D."),
            locked.glossary,
        )
        assertFalse(locked.characters.containsKey("洛克"))
        assertFalse(locked.glossary.containsKey("咆哮突击队"))
    }

    @Test
    fun hasCjkTextCodePointsDetectsCjkAccurately() {
        assertTrue(AiTranslationRefinePipeline.hasCjkTextCodePoints("咆哮突击队"))
        assertTrue(AiTranslationRefinePipeline.hasCjkTextCodePoints("Đội 咆哮"))
        assertTrue(AiTranslationRefinePipeline.hasCjkTextCodePoints("こんにちは"))
        assertTrue(AiTranslationRefinePipeline.hasCjkTextCodePoints("안녕하세요"))
        assertFalse(AiTranslationRefinePipeline.hasCjkTextCodePoints("Đội trưởng Mỹ"))
        assertFalse(AiTranslationRefinePipeline.hasCjkTextCodePoints("Captain America"))
        assertFalse(AiTranslationRefinePipeline.hasCjkTextCodePoints("S.H.I.E.L.D."))
        assertFalse(AiTranslationRefinePipeline.hasCjkTextCodePoints(""))
    }
}
