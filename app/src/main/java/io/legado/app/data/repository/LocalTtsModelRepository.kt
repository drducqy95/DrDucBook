package io.legado.app.data.repository

import android.content.Context
import android.net.Uri
import io.legado.app.domain.gateway.LocalTtsModelGateway
import io.legado.app.domain.model.LocalTtsImportProgress
import io.legado.app.domain.model.LocalTtsModelInfo
import io.legado.app.domain.model.LocalTtsModelTestResult
import io.legado.app.domain.model.LocalTtsVoiceInfo
import io.legado.app.model.ReadAloud
import io.legado.app.model.tts.LocalTtsModel
import io.legado.app.model.tts.LocalTtsModelImporter
import io.legado.app.model.tts.LocalTtsModelRegistry
import io.legado.app.model.tts.LocalTtsSynthesisEngine
import io.legado.app.model.tts.PiperOnnxTtsEngine
import io.legado.app.model.tts.ValtecOnnxTtsEngine
import io.legado.app.model.tts.parseLocalTtsEngine
import io.legado.app.ui.config.readConfig.ReadConfig
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LocalTtsModelRepository(
    private val context: Context,
) : LocalTtsModelGateway {

    private val registry = LocalTtsModelRegistry(context)
    private val models = MutableStateFlow<ImmutableList<LocalTtsModelInfo>>(persistentListOf())

    override fun observeModels(): Flow<ImmutableList<LocalTtsModelInfo>> = models.asStateFlow()

    override suspend fun refresh() = withContext(Dispatchers.IO) {
        models.value = registry.list().map { it.toInfo() }.toImmutableList()
    }

    override suspend fun importModel(
        uri: Uri,
        onProgress: (LocalTtsImportProgress) -> Unit,
    ): LocalTtsModelInfo = withContext(Dispatchers.IO) {
        LocalTtsModelImporter.import(context, uri, onProgress).toInfo().also { refresh() }
    }

    override suspend fun deleteModel(modelId: String) = withContext(Dispatchers.IO) {
        check(registry.delete(modelId)) { "Không thể xóa model TTS local" }
        if (parseLocalTtsEngine(ReadConfig.ttsEngine)?.modelId == modelId) {
            ReadConfig.ttsEngine = null
            ReadAloud.upReadAloudClass()
        }
        refresh()
    }

    override suspend fun testModel(
        modelId: String,
        voiceId: Int,
        testPhrase: String,
    ): LocalTtsModelTestResult = withContext(Dispatchers.Default) {
        val model = registry.get(modelId)
            ?: return@withContext LocalTtsModelTestResult(false, message = "Không tìm thấy model TTS")
        if (model.engine !in setOf(
                LocalTtsModelRegistry.ENGINE_VALTEC_VITS,
                LocalTtsModelRegistry.ENGINE_PIPER_VITS,
            )
        ) {
            return@withContext LocalTtsModelTestResult(
                false,
                message = "Runtime chưa hỗ trợ engine ${model.engine}",
            )
        }
        if (model.voices.none { it.id == voiceId }) {
            return@withContext LocalTtsModelTestResult(false, message = "Voice không tồn tại trong model")
        }
        runCatching {
            createEngine(model).use { engine ->
                engine.synthesize(testPhrase.trim().take(160), voiceId)
            }
        }.fold(
            onSuccess = { samples ->
                val valid = samples.isNotEmpty() && samples.all(Float::isFinite)
                LocalTtsModelTestResult(
                    success = valid,
                    sampleRate = model.sampleRate,
                    frameCount = samples.size,
                    message = if (valid) "OK" else "PCM rỗng hoặc chứa giá trị không hợp lệ",
                )
            },
            onFailure = { error ->
                LocalTtsModelTestResult(
                    success = false,
                    message = error.localizedMessage ?: error.javaClass.simpleName,
                )
            },
        )
    }

    override suspend fun selectDefaultModel(modelId: String, voiceId: Int) {
        val model = withContext(Dispatchers.IO) { registry.get(modelId) }
            ?: error("Không tìm thấy model TTS")
        require(model.voices.any { it.id == voiceId }) { "Voice không tồn tại trong model" }
        ReadConfig.ttsEngine = model.engineValue(voiceId)
        ReadAloud.upReadAloudClass()
        refresh()
    }

    override fun getModelInfo(modelId: String): LocalTtsModelInfo? =
        models.value.firstOrNull { it.id == modelId }

    private fun LocalTtsModel.toInfo(): LocalTtsModelInfo {
        val selected = parseLocalTtsEngine(ReadConfig.ttsEngine)
            ?.takeIf { it.modelId == id }
            ?.voiceId
        return LocalTtsModelInfo(
            id = id,
            name = name,
            engine = engine,
            language = language,
            sampleRate = sampleRate,
            voices = voices.map { LocalTtsVoiceInfo(it.id, it.name) }.toImmutableList(),
            defaultVoiceId = defaultVoiceId,
            selectedVoiceId = selected,
            attribution = attribution,
            license = license,
            checksum = checksum,
            sizeBytes = sizeBytes,
            runtimeReady = engine == LocalTtsModelRegistry.ENGINE_VALTEC_VITS ||
                engine == LocalTtsModelRegistry.ENGINE_PIPER_VITS,
        )
    }

    private fun createEngine(model: LocalTtsModel): LocalTtsSynthesisEngine = when (model.engine) {
        LocalTtsModelRegistry.ENGINE_VALTEC_VITS -> ValtecOnnxTtsEngine(model)
        LocalTtsModelRegistry.ENGINE_PIPER_VITS -> PiperOnnxTtsEngine(context, model)
        else -> error("Runtime chưa hỗ trợ engine ${model.engine}")
    }
}
