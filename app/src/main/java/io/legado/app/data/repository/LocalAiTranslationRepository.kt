package io.legado.app.data.repository

import android.content.Context
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.LocalAiEmptyOutputException
import io.legado.app.domain.gateway.LocalAiEngineGateway
import io.legado.app.domain.gateway.LocalAiTranslationGateway
import io.legado.app.domain.gateway.LocalAiTranslationResult
import io.legado.app.domain.gateway.LocalAiModelMetadata
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.AiTranslationChunkContext
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.LocalAiModelCatalog
import io.legado.app.domain.model.LocalAiTranslationPrompt
import io.legado.app.ui.config.translation.TranslationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalAiTranslationRepository(
    private val context: Context,
    private val localAiEngineGateway: LocalAiEngineGateway,
) : LocalAiTranslationGateway {

    @Volatile
    private var cachedMetadata: LocalAiModelMetadata? = null

    override val isAvailable: Boolean
        get() = localAiEngineGateway.nativeRuntimeAvailable && resolveModelPath().isNotBlank()

    override val loadedModelName: String?
        get() = resolveModelPath().takeIf { it.isNotBlank() }?.let { File(it).nameWithoutExtension }

    override suspend fun contextWindow(): Int {
        val path = resolveModelPath()
        if (path.isBlank()) return 4_096
        val file = File(path)
        val cached = cachedMetadata
        if (cached?.path == file.absolutePath && cached.sizeBytes == file.length()) {
            return cached.contextWindow.coerceAtLeast(1_024)
        }
        val metadata = localAiEngineGateway.inspectModel(path).getOrNull()
        if (metadata != null) cachedMetadata = metadata
        return (metadata?.contextWindow ?: 4_096).coerceAtLeast(1_024)
    }

    override suspend fun translate(
        text: String,
        targetLanguage: String,
        context: AiTranslationChunkContext,
        dictionary: List<DictPair>,
        configuredPrompt: String,
        retryInstruction: String,
        params: AiGenerationParams?,
        onToken: ((String) -> Unit)?,
    ): LocalAiTranslationResult = withContext(Dispatchers.IO) {
        val modelPath = resolveModelPath()
        require(modelPath.isNotBlank()) {
            "Chưa chọn tệp model Local AI GGUF. Vui lòng cấu hình trong Cài đặt Dịch thuật."
        }

        val systemPrompt = LocalAiTranslationPrompt.buildSystemPrompt(targetLanguage, configuredPrompt)
        val userPrompt = LocalAiTranslationPrompt.buildUserPrompt(
            text = text,
            targetLanguage = targetLanguage,
            context = context,
            dictionary = dictionary,
            retryInstruction = retryInstruction,
        )

        val estimatedOutputTokens = (text.length * 1.8 + 96).toInt().coerceIn(256, 1024)
        val generationParams = params ?: AiGenerationParams(
            temperature = TranslationConfig.localAiTemperature,
            topP = TranslationConfig.localAiTopP,
            topK = TranslationConfig.localAiTopK,
            repetitionPenalty = TranslationConfig.localAiRepetitionPenalty,
            maxOutputTokens = estimatedOutputTokens,
        )

        val modelFile = File(modelPath)
        val dummyProvider = AiProviderConfig(
            id = "local_ai",
            name = "Local AI",
            protocol = "local_gguf",
            baseUrl = "file://$modelPath",
            apiKey = "",
        )
        val dummyModel = AiModelConfig(
            id = "local_ai_model",
            provider = dummyProvider,
            displayName = modelFile.name,
            modelId = modelFile.name,
            contextWindow = contextWindow(),
            maxOutputTokens = generationParams.maxOutputTokens ?: 4_096,
            defaultParams = generationParams,
        )

        val request = AiGenerateRequest(
            model = dummyModel,
            messages = listOf(
                AiMessage(
                    role = AiMessageRole.SYSTEM,
                    content = systemPrompt,
                ),
                AiMessage(
                    role = AiMessageRole.USER,
                    content = userPrompt,
                )
            ),
            params = generationParams,
            taskType = AiTaskType.TRANSLATE_CHAPTER,
        )

        val output = StringBuilder()
        var tokenCount = 0
        localAiEngineGateway.generateStream(modelPath, request).collect { event ->
            if (event is AiStreamEvent.Content) {
                output.append(event.text)
                tokenCount++
                onToken?.invoke(event.text)
            }
        }

        val resultText = sanitizeLocalAiOutput(output.toString())
        if (resultText.isBlank()) {
            throw LocalAiEmptyOutputException()
        }
        LocalAiTranslationResult(
            text = resultText,
            generatedTokens = tokenCount,
            promptTokens = (systemPrompt.length + userPrompt.length) / 3,
            modelName = modelFile.nameWithoutExtension,
        )
    }

    override suspend fun rewrite(
        text: String,
        instruction: String,
        params: AiGenerationParams?,
        onToken: ((String) -> Unit)?,
    ): LocalAiTranslationResult = withContext(Dispatchers.IO) {
        val modelPath = resolveModelPath()
        require(modelPath.isNotBlank()) {
            "Chưa chọn tệp model Local AI GGUF. Vui lòng cấu hình trong Cài đặt Dịch thuật."
        }

        val systemPrompt = "You are a professional literary assistant. Rewrite and polish the text according to the instructions. Only output the rewritten content without explanations."
        val userPrompt = buildString {
            if (instruction.isNotBlank()) {
                append("INSTRUCTION:\n").append(instruction.trim()).append("\n\n")
            }
            append("SOURCE TEXT:\n").append(text).append("\n\n")
            append("REWRITTEN TEXT:\n")
        }

        val generationParams = params ?: AiGenerationParams(
            temperature = TranslationConfig.localAiTemperature,
            topP = TranslationConfig.localAiTopP,
            topK = TranslationConfig.localAiTopK,
            repetitionPenalty = TranslationConfig.localAiRepetitionPenalty,
            maxOutputTokens = 4_096,
        )

        val modelFile = File(modelPath)
        val dummyProvider = AiProviderConfig(
            id = "local_ai",
            name = "Local AI",
            protocol = "local_gguf",
            baseUrl = "file://$modelPath",
            apiKey = "",
        )
        val dummyModel = AiModelConfig(
            id = "local_ai_model",
            provider = dummyProvider,
            displayName = modelFile.name,
            modelId = modelFile.name,
            contextWindow = contextWindow(),
            maxOutputTokens = generationParams.maxOutputTokens ?: 4_096,
            defaultParams = generationParams,
        )

        val request = AiGenerateRequest(
            model = dummyModel,
            messages = listOf(
                AiMessage(
                    role = AiMessageRole.SYSTEM,
                    content = systemPrompt,
                ),
                AiMessage(
                    role = AiMessageRole.USER,
                    content = userPrompt,
                )
            ),
            params = generationParams,
            taskType = AiTaskType.REWRITE_TEXT,
        )

        val output = StringBuilder()
        var tokenCount = 0
        localAiEngineGateway.generateStream(modelPath, request).collect { event ->
            if (event is AiStreamEvent.Content) {
                output.append(event.text)
                tokenCount++
                onToken?.invoke(event.text)
            }
        }

        val resultText = sanitizeLocalAiOutput(output.toString())
        LocalAiTranslationResult(
            text = resultText,
            generatedTokens = tokenCount,
            promptTokens = (systemPrompt.length + userPrompt.length) / 3,
            modelName = modelFile.nameWithoutExtension,
        )
    }

    override suspend fun unload() {
        localAiEngineGateway.unload()
    }

    private fun sanitizeLocalAiOutput(raw: String): String {
        var text = raw.trim()
        val stopMarkers = listOf(
            "<|im_end|>",
            "<|endoftext|>",
            "<|end|>",
            "</s>",
            "<eos>",
            "[DONE]",
            "[END]",
        )
        for (marker in stopMarkers) {
            if (text.endsWith(marker)) {
                text = text.removeSuffix(marker).trimEnd()
            }
            if (text.contains(marker)) {
                text = text.substringBefore(marker).trimEnd()
            }
        }
        return text
    }

    private fun resolveModelPath(): String {
        val configured = TranslationConfig.localAiModelPath.trim()
        if (configured.isNotBlank() && File(configured).exists()) {
            return configured
        }
        val candidateDirs = listOfNotNull(
            context.getExternalFilesDir(null)?.let { File(it, "local-ai/models") },
            File(context.filesDir, "local-ai/models"),
            context.getExternalFilesDir(null)?.let { File(it, "asset_delivery/local_ai") },
            File(context.filesDir, "asset_delivery/local_ai"),
        )
        for (dir in candidateDirs) {
            if (dir.exists()) {
                val candidate = dir.listFiles()?.firstOrNull { it.isFile && it.extension.equals("gguf", ignoreCase = true) }
                if (candidate != null) {
                    TranslationConfig.localAiModelPath = candidate.absolutePath
                    return candidate.absolutePath
                }
            }
        }
        return ""
    }
}
