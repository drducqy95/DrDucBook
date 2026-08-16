package io.legado.app.data.repository

import io.legado.app.data.dao.AiProfileDao
import io.legado.app.data.entities.AiModelProfile
import io.legado.app.data.entities.AiProviderProfile
import io.legado.app.data.entities.AiTaskPreset
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiSecretStore
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiModelRegistry
import io.legado.app.domain.model.AiProfileDraft
import io.legado.app.domain.model.AiPromptTemplate
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiProviderDraft
import io.legado.app.domain.model.AiTaskRuntimeOptions
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskPresetDraft
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.UUID

class AiProfileRepository(
    private val aiProfileDao: AiProfileDao,
    private val secretStore: AiSecretStore? = null,
) : AiProfileGateway {

    override fun observeProviders(): Flow<List<AiProviderProfile>> = aiProfileDao.observeProviders()

    override fun observeModels(): Flow<List<AiModelProfile>> = aiProfileDao.observeModels()

    override fun observePresets(): Flow<List<AiTaskPreset>> = aiProfileDao.observePresets()

    override suspend fun getProvider(id: String): AiProviderProfile? = withContext(Dispatchers.IO) {
        aiProfileDao.getProvider(id)
    }

    override suspend fun getModel(id: String): AiModelProfile? = withContext(Dispatchers.IO) {
        aiProfileDao.getModel(id)
    }

    override suspend fun getModelConfig(id: String): AiModelConfig? = withContext(Dispatchers.IO) {
        val model = aiProfileDao.getModel(id) ?: return@withContext null
        val provider = aiProfileDao.getProvider(model.providerId) ?: return@withContext null
        model.toConfig(provider, resolveApiKey(provider))
    }

    override suspend fun getTaskPreset(taskType: String): AiTaskPresetConfig? = withContext(Dispatchers.IO) {
        aiProfileDao.getDefaultPreset(taskType)?.toConfig()
    }

    override suspend fun getProviderApiKey(providerId: String): String = withContext(Dispatchers.IO) {
        aiProfileDao.getProvider(providerId)?.let(::resolveApiKey).orEmpty()
    }

    override suspend fun saveProvider(draft: AiProviderDraft): AiProviderProfile = withContext(Dispatchers.IO) {
        require(draft.providerName.isNotBlank()) { "Provider name is required" }
        require(draft.baseUrl.isNotBlank()) { "Base URL is required" }

        val providerId = draft.providerId?.takeIf { it.isNotBlank() } ?: newId("provider")
        val existingProvider = aiProfileDao.getProvider(providerId)
        val storedSecret = secureProviderSecret(draft.apiKey, existingProvider)
        val now = System.currentTimeMillis()
        val provider = AiProviderProfile(
            id = providerId,
            name = draft.providerName,
            protocol = draft.protocol,
            baseUrl = draft.baseUrl,
            modelsUrl = draft.modelsUrl?.takeIf { it.isNotBlank() },
            apiKey = storedSecret.legacyValue,
            authType = draft.authType
                ?: existingProvider?.authType
                ?: AiProviderProfile.AUTH_TYPE_BEARER,
            secretRef = storedSecret.reference,
            headersJson = draft.headers?.let(GSON::toJson) ?: existingProvider?.headersJson,
            chatPath = draft.chatPath ?: existingProvider?.chatPath,
            responsesPath = draft.responsesPath ?: existingProvider?.responsesPath,
            messagesPath = draft.messagesPath ?: existingProvider?.messagesPath,
            modelsPath = draft.modelsPath ?: existingProvider?.modelsPath,
            customHeadersJson = draft.customHeaders?.let(GSON::toJson)
                ?: existingProvider?.customHeadersJson,
            enabled = existingProvider?.enabled ?: true,
            createdAt = existingProvider?.createdAt ?: now,
            updatedAt = now
        )
        aiProfileDao.insertProvider(provider)
        provider
    }

    override suspend fun saveModel(draft: AiModelDraft): AiModelProfile = withContext(Dispatchers.IO) {
        require(draft.providerId.isNotBlank()) { "Provider is required" }
        require(draft.modelId.isNotBlank()) { "Model is required" }

        val existingProvider = aiProfileDao.getProvider(draft.providerId)
        require(existingProvider != null) { "Provider is required" }
        val modelProfileId = draft.modelProfileId?.takeIf { it.isNotBlank() } ?: stableModelId(
            providerId = draft.providerId,
            modelId = draft.modelId
        )
        val existingModel = aiProfileDao.getModel(modelProfileId)
        val now = System.currentTimeMillis()
        val params = AiGenerationParams(temperature = draft.temperature)
        val model = AiModelProfile(
            id = modelProfileId,
            providerId = draft.providerId,
            displayName = draft.modelName.ifBlank { draft.modelId },
            modelId = draft.modelId,
            contextWindow = draft.contextWindow,
            maxOutputTokens = draft.maxOutputTokens,
            capabilities = existingModel?.capabilities.orEmpty(),
            defaultParamsJson = GSON.toJson(params),
            enabled = existingModel?.enabled ?: true,
            sortNumber = draft.sortNumber ?: existingModel?.sortNumber ?: 0,
            createdAt = existingModel?.createdAt ?: now,
            updatedAt = now
        )
        aiProfileDao.insertModel(model)
        model
    }

    override suspend fun importProviderModels(
        providerId: String,
        models: List<AiAvailableModel>
    ): List<AiModelProfile> = withContext(Dispatchers.IO) {
        require(aiProfileDao.getProvider(providerId) != null) { "Provider is required" }
        val now = System.currentTimeMillis()
        models.distinctBy { it.id }.mapIndexed { index, availableModel ->
            val modelProfileId = stableModelId(providerId, availableModel.id)
            val existingModel = aiProfileDao.getModel(modelProfileId)
            AiModelProfile(
                id = modelProfileId,
                providerId = providerId,
                displayName = availableModel.name.ifBlank { availableModel.id },
                modelId = availableModel.id,
                contextWindow = availableModel.contextWindow.takeIf { it > 0 } ?: existingModel?.contextWindow ?: 0,
                maxOutputTokens = availableModel.maxOutputTokens.takeIf { it > 0 } ?: existingModel?.maxOutputTokens ?: 0,
                capabilities = existingModel?.capabilities?.takeIf { it.isNotBlank() }
                    ?: AiModelRegistry.inferCapabilities(availableModel.id).joinToString(","),
                defaultParamsJson = existingModel?.defaultParamsJson ?: GSON.toJson(AiGenerationParams()),
                enabled = existingModel?.enabled ?: true,
                sortNumber = existingModel?.sortNumber ?: index,
                createdAt = existingModel?.createdAt ?: now,
                updatedAt = now
            ).also { aiProfileDao.insertModel(it) }
        }
    }

    override suspend fun setDefaultModel(modelProfileId: String): AiTaskPresetConfig = withContext(Dispatchers.IO) {
        val model = aiProfileDao.getModel(modelProfileId) ?: error("Model is required")
        val params = parseParams(model.defaultParamsJson)
        saveDefaultPresets(modelProfileId, params)
        aiProfileDao.getDefaultPreset(AiTaskType.TRANSLATE_CHAPTER)?.toConfig()
            ?: error("Failed to save default model")
    }

    override suspend fun deleteProvider(providerId: String) = withContext(Dispatchers.IO) {
        aiProfileDao.getProvider(providerId)?.secretRef?.let { secretStore?.delete(it) }
        aiProfileDao.deletePresetsByProvider(providerId)
        aiProfileDao.deleteModelsByProvider(providerId)
        aiProfileDao.deleteProvider(providerId)
    }

    override suspend fun deleteModel(modelId: String) = withContext(Dispatchers.IO) {
        aiProfileDao.deletePresetsByModel(modelId)
        aiProfileDao.deleteModel(modelId)
    }

    override suspend fun saveTaskPreset(
        draft: AiTaskPresetDraft
    ): AiTaskPresetConfig = withContext(Dispatchers.IO) {
        require(draft.taskType.isNotBlank()) { "AI task is required" }
        require(draft.name.isNotBlank()) { "Preset name is required" }
        require(draft.promptTemplate.isNotBlank()) { "System prompt is required" }
        require(aiProfileDao.getModel(draft.modelProfileId) != null) {
            "AI model is required"
        }
        val existing = draft.presetId
            ?.takeIf(String::isNotBlank)
            ?.let { aiProfileDao.getPreset(it) }
        val keepsExistingDefault = existing?.isDefault == true &&
            existing.taskType == draft.taskType
        val isFirstEnabledPresetForTask = aiProfileDao
            .getFirstEnabledPreset(draft.taskType) == null
        val shouldMakeDefault = draft.makeDefault ||
            keepsExistingDefault ||
            isFirstEnabledPresetForTask
        val now = System.currentTimeMillis()
        val preset = AiTaskPreset(
            id = existing?.id ?: newId("preset"),
            taskType = draft.taskType,
            name = draft.name.trim(),
            description = draft.description.trim(),
            modelProfileId = draft.modelProfileId,
            promptTemplate = draft.promptTemplate.trim(),
            paramsJson = GSON.toJson(draft.params),
            chunkPolicyJson = GSON.toJson(draft.runtimeOptions),
            enabled = draft.enabled || shouldMakeDefault,
            isDefault = shouldMakeDefault,
            sortNumber = draft.sortNumber,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        aiProfileDao.upsertTaskPreset(preset)
        if (existing?.isDefault == true && existing.taskType != preset.taskType) {
            aiProfileDao.getFirstEnabledPresetExcluding(existing.taskType, preset.id)?.let {
                aiProfileDao.setDefaultPreset(it)
            }
        }
        preset.toConfig() ?: error("Failed to resolve saved AI prompt preset")
    }

    override suspend fun setDefaultTaskPreset(
        presetId: String
    ): AiTaskPresetConfig = withContext(Dispatchers.IO) {
        val preset = aiProfileDao.getPreset(presetId) ?: error("AI prompt preset not found")
        aiProfileDao.setDefaultPreset(preset)
        aiProfileDao.getPreset(presetId)?.toConfig()
            ?: error("Failed to activate AI prompt preset")
    }

    override suspend fun deleteTaskPreset(presetId: String) = withContext(Dispatchers.IO) {
        val preset = aiProfileDao.getPreset(presetId) ?: return@withContext
        aiProfileDao.deletePreset(presetId)
        if (preset.isDefault) {
            aiProfileDao.getFirstEnabledPreset(preset.taskType)?.let {
                aiProfileDao.setDefaultPreset(it)
            }
        }
    }

    override suspend fun saveTaskPreset(
        taskType: String,
        promptTemplate: String,
        temperature: Float,
        maxOutputTokens: Int
    ): AiTaskPresetConfig = withContext(Dispatchers.IO) {
        runCatching {
            val existingPreset = aiProfileDao.getDefaultPreset(taskType)
            val presetId = existingPreset?.id ?: when (taskType) {
                AiTaskType.TRANSLATE_CHAPTER -> DEFAULT_TRANSLATE_PRESET_ID
                AiTaskType.SUMMARIZE_CHAPTER -> DEFAULT_SUMMARY_PRESET_ID
                AiTaskType.CHAT -> DEFAULT_CHAT_PRESET_ID
                else -> newId("preset")
            }
            val modelsList = aiProfileDao.observeModels().firstOrNull()
            val modelProfileId = existingPreset?.modelProfileId
                ?: modelsList?.firstOrNull()?.id
                ?: ""
            val currentParams = parseParams(existingPreset?.paramsJson)
            val updatedParams = currentParams.copy(
                temperature = temperature,
                maxOutputTokens = maxOutputTokens
            )
            val now = System.currentTimeMillis()
            val preset = AiTaskPreset(
                id = presetId,
                taskType = taskType,
                name = existingPreset?.name ?: when (taskType) {
                    AiTaskType.TRANSLATE_CHAPTER -> "Default Translation"
                    AiTaskType.SUMMARIZE_CHAPTER -> "Default Chapter Summary"
                    AiTaskType.CHAT -> "Default Chat"
                    else -> "Default Preset"
                },
                modelProfileId = modelProfileId,
                promptTemplate = promptTemplate.ifBlank {
                    when (taskType) {
                        AiTaskType.TRANSLATE_CHAPTER -> TranslationConstants.DEFAULT_PROMPT
                        AiTaskType.SUMMARIZE_CHAPTER -> AiPromptTemplate.DEFAULT_CHAPTER_SUMMARY
                        else -> "You are a helpful AI assistant."
                    }
                },
                paramsJson = GSON.toJson(updatedParams),
                chunkPolicyJson = existingPreset?.chunkPolicyJson,
                enabled = existingPreset?.enabled ?: true,
                isDefault = existingPreset?.isDefault ?: true,
                sortNumber = existingPreset?.sortNumber ?: 0,
                createdAt = existingPreset?.createdAt ?: now,
                updatedAt = now
            )
            aiProfileDao.insertPreset(preset)
            preset.toConfig() ?: error("Failed to save task preset")
        }.getOrElse { error ->
            throw IllegalStateException(
                "Failed to save AI preset: ${error.localizedMessage ?: "Unknown error"}",
                error,
            )
        }
    }

    override suspend fun saveDefaultChatProfile(draft: AiProfileDraft): AiTaskPresetConfig = withContext(Dispatchers.IO) {
        require(draft.baseUrl.isNotBlank()) { "Base URL is required" }
        require(draft.modelId.isNotBlank()) { "Model is required" }

        val providerId = draft.providerId?.takeIf { it.isNotBlank() } ?: newId("provider")
        val existingProvider = aiProfileDao.getProvider(providerId)
        val modelProfileId = draft.modelProfileId?.takeIf { it.isNotBlank() } ?: newId("model")
        val existingModel = aiProfileDao.getModel(modelProfileId)
        val resolvedApiKey = draft.apiKey.ifBlank {
            existingProvider?.let(::resolveApiKey).orEmpty()
        }
        require(resolvedApiKey.isNotBlank()) { "API key is required" }
        val storedSecret = secureProviderSecret(resolvedApiKey, existingProvider)
        val now = System.currentTimeMillis()
        val params = AiGenerationParams(temperature = draft.temperature)
        val existingTranslatePreset = aiProfileDao.getDefaultPreset(
            AiTaskType.TRANSLATE_CHAPTER
        )
        val translationRuntimeOptions = existingTranslatePreset
            ?.chunkPolicyJson
            ?.let { parseRuntimeOptions(it) }
            ?: AiTaskRuntimeOptions(
                targetLanguage = draft.translationTargetLanguage,
                maxInputChars = draft.maxInputChars,
                concurrentRequests = draft.concurrentRequests,
                retryCount = draft.retryCount
            )

        aiProfileDao.insertProvider(
            AiProviderProfile(
                id = providerId,
                name = draft.providerName.ifBlank { "Default AI Provider" },
                protocol = draft.protocol,
                baseUrl = draft.baseUrl,
                modelsUrl = existingProvider?.modelsUrl,
                apiKey = storedSecret.legacyValue,
                secretRef = storedSecret.reference,
                headersJson = existingProvider?.headersJson,
                chatPath = existingProvider?.chatPath,
                responsesPath = existingProvider?.responsesPath,
                messagesPath = existingProvider?.messagesPath,
                modelsPath = existingProvider?.modelsPath,
                customHeadersJson = existingProvider?.customHeadersJson,
                createdAt = existingProvider?.createdAt ?: now,
                updatedAt = now
            )
        )
        aiProfileDao.insertModel(
            AiModelProfile(
                id = modelProfileId,
                providerId = providerId,
                displayName = draft.modelName.ifBlank { draft.modelId },
                modelId = draft.modelId,
                contextWindow = draft.contextWindow,
                maxOutputTokens = draft.maxOutputTokens,
                capabilities = existingModel?.capabilities.orEmpty(),
                defaultParamsJson = GSON.toJson(params),
                createdAt = existingModel?.createdAt ?: now,
                updatedAt = now
            )
        )
        saveDefaultPresets(modelProfileId, params, translationRuntimeOptions)
        aiProfileDao.getDefaultPreset(AiTaskType.TRANSLATE_CHAPTER)?.toConfig()
            ?: error("Failed to save AI profile")
    }

    private suspend fun saveDefaultPresets(
        modelProfileId: String,
        params: AiGenerationParams,
        translationRuntimeOptions: AiTaskRuntimeOptions? = null
    ) {
        upsertActivePresetForModel(
            taskType = AiTaskType.TRANSLATE_CHAPTER,
            fallbackId = DEFAULT_TRANSLATE_PRESET_ID,
            fallbackName = "Default Translation",
            fallbackPrompt = TranslationConstants.DEFAULT_PROMPT,
            modelProfileId = modelProfileId,
            params = params,
            runtimeOptions = translationRuntimeOptions,
        )
        upsertActivePresetForModel(
            taskType = AiTaskType.SUMMARIZE_CHAPTER,
            fallbackId = DEFAULT_SUMMARY_PRESET_ID,
            fallbackName = "Default Chapter Summary",
            fallbackPrompt = AiPromptTemplate.DEFAULT_CHAPTER_SUMMARY,
            modelProfileId = modelProfileId,
            params = params,
        )
        upsertActivePresetForModel(
            taskType = AiTaskType.CHAT,
            fallbackId = DEFAULT_CHAT_PRESET_ID,
            fallbackName = "Default Chat",
            fallbackPrompt = "You are a helpful AI assistant.",
            modelProfileId = modelProfileId,
            params = params,
        )
        upsertActivePresetForModel(
            taskType = AiTaskType.AUTHORING_DIRECTOR,
            fallbackId = DEFAULT_AUTHORING_DIRECTOR_PRESET_ID,
            fallbackName = "Story Director",
            fallbackPrompt = AiPromptTemplate.DEFAULT_AUTHORING_DIRECTOR,
            modelProfileId = modelProfileId,
            params = params,
        )
        upsertActivePresetForModel(
            taskType = AiTaskType.AUTHORING_WRITER,
            fallbackId = DEFAULT_AUTHORING_WRITER_PRESET_ID,
            fallbackName = "Story Writer",
            fallbackPrompt = AiPromptTemplate.DEFAULT_AUTHORING_WRITER,
            modelProfileId = modelProfileId,
            params = params,
        )
    }

    private suspend fun upsertActivePresetForModel(
        taskType: String,
        fallbackId: String,
        fallbackName: String,
        fallbackPrompt: String,
        modelProfileId: String,
        params: AiGenerationParams,
        runtimeOptions: AiTaskRuntimeOptions? = null,
    ) {
        val existing = aiProfileDao.getDefaultPreset(taskType)
            ?: aiProfileDao.getPreset(fallbackId)
        val now = System.currentTimeMillis()
        aiProfileDao.upsertTaskPreset(
            AiTaskPreset(
                id = existing?.id ?: fallbackId,
                taskType = taskType,
                name = existing?.name ?: fallbackName,
                modelProfileId = modelProfileId,
                promptTemplate = existing?.promptTemplate
                    ?.takeIf(String::isNotBlank)
                    ?: fallbackPrompt,
                paramsJson = GSON.toJson(params),
                chunkPolicyJson = runtimeOptions
                    ?.let(GSON::toJson)
                    ?: existing?.chunkPolicyJson,
                enabled = true,
                isDefault = true,
                sortNumber = existing?.sortNumber ?: 0,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        )
    }

    private suspend fun AiTaskPreset.toConfig(): AiTaskPresetConfig? {
        val model = aiProfileDao.getModel(modelProfileId) ?: return null
        val provider = aiProfileDao.getProvider(model.providerId) ?: return null
        val presetParams = parseParams(paramsJson)
        val modelParams = parseParams(model.defaultParamsJson)
        val parsedRuntimeOptions = parseRuntimeOptions(chunkPolicyJson)
        val runtimeOptions = if (
            id == DEFAULT_TRANSLATE_PRESET_ID &&
            taskType == AiTaskType.TRANSLATE_CHAPTER &&
            parsedRuntimeOptions.maxInputChars == LEGACY_DEFAULT_TRANSLATION_CHUNK_CHARS &&
            parsedRuntimeOptions.concurrentRequests == AiTaskRuntimeOptions.DEFAULT_CONCURRENT_REQUESTS &&
            parsedRuntimeOptions.retryCount == AiTaskRuntimeOptions.DEFAULT_RETRY_COUNT
        ) {
            parsedRuntimeOptions.copy(maxInputChars = SAFE_DEFAULT_TRANSLATION_CHUNK_CHARS).also { migrated ->
                val now = System.currentTimeMillis()
                aiProfileDao.upsertTaskPreset(
                    copy(
                        chunkPolicyJson = GSON.toJson(migrated),
                        updatedAt = now,
                    )
                )
            }
        } else {
            parsedRuntimeOptions
        }
        val mergedParams = presetParams.mergeWithFallback(
            modelParams = modelParams,
            modelMaxOutputTokens = model.maxOutputTokens,
            taskType = taskType
        )
        return AiTaskPresetConfig(
            id = id,
            taskType = taskType,
            name = name,
            description = description,
            model = model.toConfig(provider, resolveApiKey(provider)),
            promptTemplate = promptTemplate,
            params = mergedParams,
            runtimeOptions = runtimeOptions
        )
    }

    private fun AiModelProfile.toConfig(
        provider: AiProviderProfile,
        resolvedApiKey: String,
    ): AiModelConfig {
        return AiModelConfig(
            id = id,
            provider = AiProviderConfig(
                id = provider.id,
                name = provider.name,
                protocol = provider.protocol,
                baseUrl = provider.baseUrl,
                apiKey = resolvedApiKey,
                authType = provider.authType,
                modelsUrl = provider.modelsUrl,
                headers = parseHeaders(provider.headersJson),
                chatPath = provider.chatPath ?: "/chat/completions",
                responsesPath = provider.responsesPath ?: "/responses",
                messagesPath = provider.messagesPath ?: "/v1/messages",
                modelsPath = provider.modelsPath,
                customHeaders = parseHeaders(provider.customHeadersJson)
            ),
            displayName = displayName,
            modelId = modelId,
            contextWindow = contextWindow,
            maxOutputTokens = maxOutputTokens,
            capabilities = capabilities.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet(),
            defaultParams = parseParams(defaultParamsJson)
        )
    }

    private fun parseParams(json: String?): AiGenerationParams {
        if (json.isNullOrBlank()) return AiGenerationParams()
        return runCatching {
            GSON.fromJson(json, AiGenerationParams::class.java)
        }.getOrDefault(AiGenerationParams())
    }

    private fun parseRuntimeOptions(json: String?): AiTaskRuntimeOptions {
        if (json.isNullOrBlank()) return AiTaskRuntimeOptions()
        return runCatching {
            GSON.fromJson(json, AiTaskRuntimeOptions::class.java)
        }.getOrDefault(AiTaskRuntimeOptions())
    }

    private fun parseHeaders(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            GSON.fromJson(json, Map::class.java)
                .mapKeys { it.key.toString() }
                .mapValues { it.value.toString() }
        }.getOrDefault(emptyMap())
    }

    private fun resolveApiKey(provider: AiProviderProfile): String {
        return provider.secretRef
            ?.let { secretStore?.get(it) }
            ?.takeIf(String::isNotBlank)
            ?: provider.apiKey
    }

    private fun secureProviderSecret(
        submittedValue: String,
        existingProvider: AiProviderProfile?,
    ): StoredProviderSecret {
        val value = submittedValue.ifBlank {
            existingProvider?.let(::resolveApiKey).orEmpty()
        }
        val store = secretStore
        if (store == null || value.isBlank()) {
            return StoredProviderSecret(
                reference = existingProvider?.secretRef,
                legacyValue = value,
            )
        }
        return StoredProviderSecret(
            reference = store.put(value, existingProvider?.secretRef),
            legacyValue = "",
        )
    }

    private data class StoredProviderSecret(
        val reference: String?,
        val legacyValue: String,
    )

    private companion object {
        const val DEFAULT_TRANSLATE_PRESET_ID = "default_translate_chapter"
        const val DEFAULT_SUMMARY_PRESET_ID = "default_summarize_chapter"
        const val DEFAULT_CHAT_PRESET_ID = "default_chat"
        const val DEFAULT_AUTHORING_DIRECTOR_PRESET_ID = "default_authoring_director"
        const val DEFAULT_AUTHORING_WRITER_PRESET_ID = "default_authoring_writer"
        const val LEGACY_DEFAULT_TRANSLATION_CHUNK_CHARS = 10_000
        const val SAFE_DEFAULT_TRANSLATION_CHUNK_CHARS = 1_000

        fun newId(prefix: String): String = "${prefix}_${UUID.randomUUID().toString().replace("-", "")}"

        fun stableModelId(providerId: String, modelId: String): String {
            val uuid = UUID.nameUUIDFromBytes("$providerId:$modelId".toByteArray())
                .toString()
                .replace("-", "")
            return "model_$uuid"
        }
    }
}
