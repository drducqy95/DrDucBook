package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.AiImageGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.StoryImageStorageGateway
import io.legado.app.domain.model.AiImageGenerateRequest
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiTranslationStoryEntity
import io.legado.app.domain.model.AiTranslationStoryMemorySnapshot
import io.legado.app.domain.model.AiTranslationWorldEntry
import kotlinx.coroutines.flow.firstOrNull

class StoryIllustrationUseCase(
    private val aiProfileGateway: AiProfileGateway,
    private val aiImageGateway: AiImageGateway,
    private val imageStorageGateway: StoryImageStorageGateway,
    private val storyMemoryUseCase: TranslationStoryMemoryUseCase,
) {

    suspend fun generateEntity(
        bookUrl: String,
        entityRaw: String,
        force: Boolean = false,
    ): String {
        val snapshot = storyMemoryUseCase.loadSnapshot(bookUrl)
        val entity = snapshot.entities.firstOrNull { it.raw.equals(entityRaw, ignoreCase = true) }
            ?: error("Story entity not found")
        require(force || StoryIllustrationPolicy.isEntityReady(entity)) {
            "Not enough verified character data to create a faithful image"
        }
        val prompt = StoryIllustrationPolicy.entityPrompt(entity, snapshot)
        val path = generateAndStore(
            bookUrl = bookUrl,
            subjectKey = "entity:${entity.raw}",
            prompt = prompt,
            size = "1024x1024",
        )
        storyMemoryUseCase.upsertEntity(
            bookUrl,
            entity.copy(
                imagePath = path,
                imagePrompt = prompt,
                imageUpdatedAt = System.currentTimeMillis(),
            )
        )
        return path
    }

    suspend fun generateWorldEntry(
        bookUrl: String,
        entry: AiTranslationWorldEntry,
        force: Boolean = false,
    ): String {
        require(force || StoryIllustrationPolicy.isWorldEntryReady(entry)) {
            "Not enough verified world-building data to create a faithful image"
        }
        val snapshot = storyMemoryUseCase.loadSnapshot(bookUrl)
        val current = snapshot.worldBuilding.firstOrNull {
            TranslationStoryMemoryUseCase.worldKey(it) == TranslationStoryMemoryUseCase.worldKey(entry)
        } ?: error("World-building entry not found")
        val prompt = StoryIllustrationPolicy.worldEntryPrompt(current, snapshot)
        val path = generateAndStore(
            bookUrl = bookUrl,
            subjectKey = "world:${TranslationStoryMemoryUseCase.worldKey(current)}",
            prompt = prompt,
            size = "1024x1024",
        )
        storyMemoryUseCase.upsertWorldEntry(
            bookUrl,
            current.copy(
                imagePath = path,
                imagePrompt = prompt,
                imageUpdatedAt = System.currentTimeMillis(),
            )
        )
        return path
    }

    suspend fun generateWorldMap(
        bookUrl: String,
        force: Boolean = false,
    ): String {
        val snapshot = storyMemoryUseCase.loadSnapshot(bookUrl)
        require(force || StoryIllustrationPolicy.isWorldMapReady(snapshot)) {
            "Not enough verified locations and factions to create a useful world map"
        }
        val prompt = StoryIllustrationPolicy.worldMapPrompt(snapshot)
        val path = generateAndStore(
            bookUrl = bookUrl,
            subjectKey = WORLD_MAP_RAW,
            prompt = prompt,
            size = "1536x1024",
        )
        val previousMap = snapshot.worldBuilding.firstOrNull { it.raw == WORLD_MAP_RAW }
        storyMemoryUseCase.upsertWorldEntry(
            bookUrl,
            AiTranslationWorldEntry(
                raw = WORLD_MAP_RAW,
                target = "Bản đồ thế giới",
                category = WORLD_MAP_CATEGORY,
                description = StoryIllustrationPolicy.worldMapDescription(snapshot),
                entityRefs = snapshot.entities.map { it.target.ifBlank { it.raw } }.take(30),
                chapterIndex = previousMap?.chapterIndex ?: -1,
                imagePath = path,
                imagePrompt = prompt,
                imageUpdatedAt = System.currentTimeMillis(),
            )
        )
        return path
    }

    private suspend fun generateAndStore(
        bookUrl: String,
        subjectKey: String,
        prompt: String,
        size: String,
    ): String {
        val preset = aiProfileGateway.getTaskPreset(AiTaskType.GENERATE_STORY_IMAGE)
            ?: resolveDefaultImagePreset()
            ?: error("Chưa cấu hình preset tạo ảnh cho Story Wiki. Vui lòng thêm preset hoặc chọn model OpenAI/DALL-E trong Cài đặt AI.")
        val requestPrompt = listOf(preset.promptTemplate.trim(), prompt)
            .filter(String::isNotBlank)
            .joinToString("\n\n")
        val result = aiImageGateway.generate(
            AiImageGenerateRequest(
                model = preset.model,
                prompt = requestPrompt,
                size = size,
            )
        )
        return imageStorageGateway.save(
            bookUrl = bookUrl,
            subjectKey = subjectKey,
            bytes = result.bytes,
            mimeType = result.mimeType,
        )
    }

    private suspend fun resolveDefaultImagePreset(): io.legado.app.domain.model.AiTaskPresetConfig? {
        val allModels = aiProfileGateway.observeModels().firstOrNull()?.filter { it.enabled }.orEmpty()
        val imageModel = allModels.firstOrNull { model ->
            model.modelId.contains("dall-e", ignoreCase = true) ||
                model.modelId.contains("flux", ignoreCase = true) ||
                model.modelId.contains("image", ignoreCase = true) ||
                model.displayName.contains("dall-e", ignoreCase = true)
        } ?: allModels.firstOrNull { model ->
            val config = aiProfileGateway.getModelConfig(model.id)
            config?.provider?.protocol == io.legado.app.domain.model.AiProtocol.OPENAI_CHAT_COMPLETIONS ||
                config?.provider?.protocol == io.legado.app.domain.model.AiProtocol.OPENAI_RESPONSES
        } ?: return null

        val modelConfig = aiProfileGateway.getModelConfig(imageModel.id) ?: return null
        return io.legado.app.domain.model.AiTaskPresetConfig(
            id = "default_generate_story_image",
            name = "Default Story Image",
            taskType = AiTaskType.GENERATE_STORY_IMAGE,
            promptTemplate = io.legado.app.domain.model.AiPromptTemplate.DEFAULT_STORY_IMAGE,
            model = modelConfig,
        )
    }

    companion object {
        const val WORLD_MAP_RAW = "__story_world_map__"
        const val WORLD_MAP_CATEGORY = "world_map"
    }
}

object StoryIllustrationPolicy {

    private val supportedWorldCategories = setOf(
        "equipment", "weapon", "technique", "faction", "location", "item", "system", "concept"
    )

    fun isEntityReady(entity: AiTranslationStoryEntity): Boolean =
        entity.raw.isNotBlank() && entity.target.isNotBlank() &&
            (entity.description.length >= 40 ||
                listOf(entity.gender, entity.rank).count(String::isNotBlank) >= 2 ||
                entity.aliases.size >= 2)

    fun isWorldEntryReady(entry: AiTranslationWorldEntry): Boolean =
        entry.raw.isNotBlank() &&
            entry.category.lowercase() in supportedWorldCategories &&
            (entry.description.length >= 32 || entry.entityRefs.size >= 2)

    fun isWorldMapReady(snapshot: AiTranslationStoryMemorySnapshot): Boolean {
        val geography = snapshot.worldBuilding.count {
            it.raw != StoryIllustrationUseCase.WORLD_MAP_RAW &&
                it.category.lowercase() in setOf("location", "faction")
        }
        return geography >= 3 && (snapshot.relationships.isNotEmpty() || snapshot.timelines.isNotEmpty())
    }

    fun entityPrompt(
        entity: AiTranslationStoryEntity,
        snapshot: AiTranslationStoryMemorySnapshot,
    ): String = buildString {
        appendLine("Create a faithful full-character concept portrait for a fiction wiki.")
        appendLine("Use only the verified story facts below. Do not invent insignia, equipment, age, or appearance not stated in the facts.")
        appendLine("No caption, text, logo, watermark, or UI. One subject, coherent neutral background, readable silhouette.")
        appendLine("VERIFIED ENTITY FACTS:")
        appendLine("- canonical name: ${entity.target.ifBlank { entity.raw }}")
        appendLine("- source name: ${entity.raw}")
        appendLine("- type: ${entity.type}")
        appendLine("- description: ${entity.description.ifBlank { "unknown" }}")
        appendLine("- gender: ${entity.gender.ifBlank { "unknown" }}")
        appendLine("- rank/title: ${entity.rank.ifBlank { "unknown" }}")
        val relations = snapshot.relationships.filter { relationship ->
            relationship.source == entity.raw || relationship.target == entity.raw ||
                relationship.source == entity.target || relationship.target == entity.target
        }.take(8)
        if (relations.isNotEmpty()) {
            appendLine("- relationship context (mood only, do not add other characters):")
            relations.forEach { appendLine("  ${it.source} -> ${it.target}: ${it.relationship}; ${it.description}") }
        }
    }.trim()

    fun worldEntryPrompt(
        entry: AiTranslationWorldEntry,
        snapshot: AiTranslationStoryMemorySnapshot,
    ): String = buildString {
        val visualKind = when (entry.category.lowercase()) {
            "weapon", "equipment", "item" -> "isolated artifact concept art"
            "technique" -> "clear technique visualization at the decisive moment"
            "location" -> "wide environmental concept art"
            "faction" -> "faction headquarters or emblematic environment without written text"
            else -> "fiction world-building concept art"
        }
        appendLine("Create $visualKind for a fiction wiki.")
        appendLine("Use only verified facts. Unknown visual details must remain restrained and generic, never presented as canon.")
        appendLine("No caption, readable text, logo, watermark, or UI.")
        appendLine("VERIFIED SUBJECT FACTS:")
        appendLine("- canonical name: ${entry.target.ifBlank { entry.raw }}")
        appendLine("- source name: ${entry.raw}")
        appendLine("- category: ${entry.category}")
        appendLine("- description: ${entry.description.ifBlank { "unknown" }}")
        if (entry.entityRefs.isNotEmpty()) appendLine("- related entities: ${entry.entityRefs.joinToString()}")
        val knownRefs = snapshot.entities.filter { entity ->
            entry.entityRefs.any { ref -> ref == entity.raw || ref == entity.target }
        }.take(10)
        if (knownRefs.isNotEmpty()) {
            appendLine("- verified related context:")
            knownRefs.forEach { appendLine("  ${it.target.ifBlank { it.raw }}: ${it.description}") }
        }
    }.trim()

    fun worldMapPrompt(snapshot: AiTranslationStoryMemorySnapshot): String = buildString {
        appendLine("Create a coherent landscape-format cartographic world map for a fiction wiki.")
        appendLine("Use only the verified locations, factions, routes, and relationships below. Do not add continents, cities, borders, or routes that are not supported.")
        appendLine("Use visual markers and terrain shapes; avoid readable labels because source names may not render reliably. No logo, watermark, or UI.")
        appendLine("VERIFIED WORLD FACTS:")
        snapshot.worldBuilding.asSequence()
            .filter { it.raw != StoryIllustrationUseCase.WORLD_MAP_RAW }
            .filter { it.category.lowercase() in setOf("location", "faction", "system", "concept") }
            .take(60)
            .forEach { entry ->
                appendLine("- [${entry.category}] ${entry.target.ifBlank { entry.raw }}: ${entry.description}; related=${entry.entityRefs.joinToString()}")
            }
        val relevantNames = snapshot.worldBuilding.flatMap(AiTranslationWorldEntry::entityRefs).toSet()
        snapshot.relationships.asSequence()
            .filter { it.source in relevantNames || it.target in relevantNames }
            .take(30)
            .forEach { appendLine("- relation ${it.source} -> ${it.target}: ${it.relationship}; ${it.description}") }
    }.trim()

    fun worldMapDescription(snapshot: AiTranslationStoryMemorySnapshot): String =
        snapshot.worldBuilding.asSequence()
            .filter { it.raw != StoryIllustrationUseCase.WORLD_MAP_RAW }
            .filter { it.category.lowercase() in setOf("location", "faction") }
            .take(20)
            .joinToString("; ") { "${it.target.ifBlank { it.raw }}: ${it.description}" }
}
