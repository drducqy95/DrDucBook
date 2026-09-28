package io.legado.app.domain.usecase

import com.google.gson.JsonParser
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.QuickDictionaryType

class EnrichEntitiesWithAiUseCase(
    private val aiProfileGateway: AiProfileGateway,
    private val aiTextGateway: AiTextGateway,
) {
    data class EnrichmentInput(
        val raw: String,
        val hanViet: String,
        val currentTarget: String,
        val currentType: String,
        val occurrences: Int,
        val context: String,
    )

    data class EnrichmentResult(
        val raw: String,
        val suggestedTarget: String,
        val suggestedType: QuickDictionaryType,
        val isValid: Boolean,
        val description: String,
    )

    suspend operator fun invoke(
        bookName: String,
        candidates: List<EnrichmentInput>,
    ): List<EnrichmentResult> {
        if (candidates.isEmpty()) return emptyList()

        val preset = aiProfileGateway.getTaskPreset(AiTaskType.TRANSLATE_CHAPTER)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: error("No AI preset configured")

        val systemPrompt = buildSystemPrompt()
        val userPrompt = buildUserPrompt(bookName, candidates)

        val request = AiGenerateRequest(
            model = preset.model,
            messages = listOf(
                AiMessage(AiMessageRole.SYSTEM, systemPrompt),
                AiMessage(AiMessageRole.USER, userPrompt),
            ),
            params = preset.params.copy(
                temperature = 0.2f,
                maxOutputTokens = 4096,
            ),
            taskType = AiTaskType.TRANSLATE_CHAPTER,
            routeProfileId = preset.runtimeOptions.routeProfileId,
        )

        val response = aiTextGateway.generate(request).getOrThrow().text
        return parseResponse(response, candidates)
    }

    private fun buildSystemPrompt(): String = """
        You are an expert at analyzing Chinese web novel entities.
        Given candidate entities extracted by an n-gram algorithm from a novel:
        1. Determine if each is a valid entity (name/term/location/faction/technique/item) or a false positive (common verb, adjective, preposition, idiom).
        2. Classify: "character" (or person/name) vs "term" (faction, location, technique, weapon, item, concept).
        3. Suggest the most accurate and natural Vietnamese translation. For foreign names transliterated into Chinese, restore Latin/Romaji spelling if appropriate.
        4. Provide a brief Vietnamese description/role.

        Return ONLY a JSON array with one object per input candidate in the same order:
        [{"raw":"...","valid":true,"type":"character|term","target":"...","description":"..."}]
    """.trimIndent()

    private fun buildUserPrompt(
        bookName: String,
        candidates: List<EnrichmentInput>,
    ): String = buildString {
        appendLine("Book: $bookName")
        appendLine("Candidates to analyze:")
        candidates.forEachIndexed { i, c ->
            appendLine("${i + 1}. raw=\"${c.raw}\" hanViet=\"${c.hanViet}\" currentTarget=\"${c.currentTarget}\" occurrences=${c.occurrences}")
            appendLine("   context: ${c.context.take(150)}")
        }
    }

    private fun parseResponse(
        response: String,
        inputs: List<EnrichmentInput>,
    ): List<EnrichmentResult> {
        val inputMap = inputs.associateBy { it.raw }
        val results = mutableListOf<EnrichmentResult>()
        val seen = mutableSetOf<String>()

        try {
            val trimmed = response.trim()
            val jsonStr = when {
                trimmed.contains("```json") -> trimmed.substringAfter("```json").substringBefore("```").trim()
                trimmed.contains("```") -> trimmed.substringAfter("```").substringBefore("```").trim()
                trimmed.startsWith("[") && trimmed.endsWith("]") -> trimmed
                trimmed.contains("[") && trimmed.contains("]") -> {
                    val start = trimmed.indexOf('[')
                    val end = trimmed.lastIndexOf(']')
                    trimmed.substring(start, end + 1)
                }
                else -> trimmed
            }

            val jsonArray = JsonParser.parseString(jsonStr).asJsonArray
            for (element in jsonArray) {
                if (!element.isJsonObject) continue
                val obj = element.asJsonObject
                val raw = obj.get("raw")?.asString?.trim().orEmpty()
                if (raw.isBlank() || raw !in inputMap || !seen.add(raw)) continue

                val valid = obj.get("valid")?.asBoolean ?: true
                val typeStr = obj.get("type")?.asString?.lowercase()?.trim().orEmpty()
                val target = obj.get("target")?.asString?.trim().orEmpty()
                val desc = obj.get("description")?.asString?.trim().orEmpty()

                val fallback = inputMap.getValue(raw)
                val finalType = if (typeStr in setOf("character", "person", "name")) {
                    QuickDictionaryType.NAME
                } else if (typeStr in setOf("term", "faction", "location", "technique", "weapon", "item", "rank")) {
                    QuickDictionaryType.TERM
                } else {
                    if (fallback.currentType.equals("NAME", ignoreCase = true)) {
                        QuickDictionaryType.NAME
                    } else {
                        QuickDictionaryType.TERM
                    }
                }

                results.add(
                    EnrichmentResult(
                        raw = raw,
                        suggestedTarget = target.ifBlank { fallback.currentTarget },
                        suggestedType = finalType,
                        isValid = valid,
                        description = desc,
                    )
                )
            }
        } catch (_: Throwable) {
            // If parsing fails, return candidates as-is
        }

        // Fill any missing inputs with defaults
        inputs.forEach { input ->
            if (input.raw !in seen) {
                results.add(
                    EnrichmentResult(
                        raw = input.raw,
                        suggestedTarget = input.currentTarget,
                        suggestedType = if (input.currentType.equals("NAME", ignoreCase = true)) {
                            QuickDictionaryType.NAME
                        } else {
                            QuickDictionaryType.TERM
                        },
                        isValid = true,
                        description = "",
                    )
                )
            }
        }

        return results
    }
}
