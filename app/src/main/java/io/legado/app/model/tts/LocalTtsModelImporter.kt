package io.legado.app.model.tts

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.legado.app.domain.model.LocalTtsImportProgress
import io.legado.app.domain.model.LocalTtsImportStage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

object LocalTtsModelImporter {

    suspend fun import(
        context: Context,
        uri: Uri,
        onProgress: (LocalTtsImportProgress) -> Unit = {},
    ): LocalTtsModel = withContext(Dispatchers.IO) {
        val registry = LocalTtsModelRegistry(context)
        val root = registry.modelRoot()
        cleanOrphanedStaging(root)
        val sourceBytes = querySourceSize(context, uri)
        if (sourceBytes > MAX_SOURCE_PACKAGE_BYTES) {
            throw IOException("TTS model package exceeds safe size limit")
        }
        onProgress(LocalTtsImportProgress(LocalTtsImportStage.PREPARING, totalBytes = sourceBytes))
        val staging = File(root, "import_${UUID.randomUUID()}")
        check(staging.mkdirs()) { "Không thể tạo thư mục tạm để nhập model TTS" }
        try {
            val packageFiles = context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                extractRecognizedFiles(input, staging) { processedBytes ->
                    onProgress(
                        LocalTtsImportProgress(
                            stage = LocalTtsImportStage.EXTRACTING,
                            processedBytes = processedBytes,
                            totalBytes = sourceBytes,
                        )
                    )
                }
            } ?: throw IOException("Không thể đọc gói model TTS")
            currentCoroutineContext().ensureActive()
            onProgress(LocalTtsImportProgress(LocalTtsImportStage.VALIDATING))
            val model = if (packageFiles.looksLikePiperVoicePack()) {
                val descriptor = preparePiperModel(staging, packageFiles)
                PiperRuntimeAssets(context).ensureInstalled()
                val checksum = registry.calculateChecksum(
                    staging,
                    LocalTtsModelRegistry.ENGINE_PIPER_VITS,
                )
                descriptor.toModel(staging, checksum.take(24), checksum)
            } else {
                LocalTtsModelRegistry.REQUIRED_FILES.forEach { fileName ->
                    if (!File(staging, fileName).isFile) {
                        throw IOException("Gói model TTS thiếu $fileName")
                    }
                }
                val checksum = registry.calculateChecksum(
                    staging,
                    LocalTtsModelRegistry.ENGINE_VALTEC_VITS,
                )
                createModel(staging, checksum.take(24), checksum)
            }
            currentCoroutineContext().ensureActive()
            onProgress(LocalTtsImportProgress(LocalTtsImportStage.PROBING))
            probeRuntime(context, model)
            currentCoroutineContext().ensureActive()
            onProgress(LocalTtsImportProgress(LocalTtsImportStage.INSTALLING))
            registry.write(model, staging)
            installAtomically(root, staging, model.id)
            registry.get(model.id) ?: throw IOException("Không thể kiểm tra model TTS sau khi cài")
        } finally {
            staging.deleteRecursively()
        }
    }

    internal suspend fun extractRecognizedFiles(
        input: java.io.InputStream,
        staging: File,
        onBytesExtracted: (Long) -> Unit = {},
    ): Set<String> {
        val extracted = hashSetOf<String>()
        var totalBytes = 0L
        var entryCount = 0
        var sawEntry = false
        try {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    sawEntry = true
                    entryCount++
                    if (entryCount > MAX_ENTRIES) throw IOException("Gói model có quá nhiều tệp")
                    val normalized = entry.name.replace('\\', '/')
                    if (normalized.startsWith('/') || normalized.split('/').any { it == ".." }) {
                        throw IOException("Đường dẫn không an toàn trong gói model: ${entry.name}")
                    }
                    val fileName = normalized.substringAfterLast('/')
                    if (!entry.isDirectory) {
                        if (!fileName.isRecognizedTtsFile()) {
                            throw IOException("Gói model chứa tệp ngoài whitelist: $fileName")
                        }
                        if (!extracted.add(fileName)) {
                            throw IOException("Gói model chứa tệp trùng: $fileName")
                        }
                        val destination = File(staging, fileName)
                        destination.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var entryBytes = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = zip.read(buffer)
                                if (read < 0) break
                                entryBytes += read
                                totalBytes += read
                                if (entryBytes > MAX_ENTRY_BYTES || totalBytes > MAX_TOTAL_BYTES) {
                                    throw IOException("Gói model vượt giới hạn dung lượng giải nén")
                                }
                                output.write(buffer, 0, read)
                                onBytesExtracted(totalBytes)
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (error: ZipException) {
            throw IOException("Gói model TTS không phải ZIP hợp lệ", error)
        }
        if (!sawEntry) throw IOException("Gói model TTS không phải ZIP hợp lệ hoặc đang trống")
        if (extracted.isEmpty()) throw IOException("Gói model TTS không chứa tệp được hỗ trợ")
        return extracted
    }

    private fun querySourceSize(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0).coerceAtLeast(0L) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    private suspend fun probeRuntime(context: Context, model: LocalTtsModel) {
        val samples = try {
            when (model.engine) {
            LocalTtsModelRegistry.ENGINE_VALTEC_VITS ->
                ValtecOnnxTtsEngine(model).use { it.synthesize(PROBE_TEXT, model.defaultVoiceId) }
            LocalTtsModelRegistry.ENGINE_PIPER_VITS ->
                PiperOnnxTtsEngine(context, model).use { it.synthesize(PROBE_TEXT, model.defaultVoiceId) }
            else -> throw IOException("Runtime chưa hỗ trợ engine ${model.engine}")
            }
        } catch (error: OutOfMemoryError) {
            throw IOException("TTS model is too large for available memory", error)
        }
        validateProbeSamples(samples)
    }

    internal fun validateProbeSamples(samples: FloatArray) {
        if (samples.isEmpty() || samples.any { !it.isFinite() }) {
            throw IOException("Model TTS không vượt qua kiểm tra tổng hợp thử")
        }
    }

    private fun Set<String>.looksLikePiperVoicePack(): Boolean =
        any { it.endsWith(".onnx.json", ignoreCase = true) } ||
            (count { it.endsWith(".onnx", ignoreCase = true) } == 1 &&
                none { it == "tts_config.json" })

    internal fun preparePiperModel(directory: File, packageFiles: Set<String>): PiperDescriptor {
        val configs = packageFiles.filter { it.endsWith(".onnx.json", ignoreCase = true) }
        if (configs.size != 1) {
            throw IOException("Gói Piper phải có đúng một tệp .onnx.json")
        }
        val models = packageFiles.filter { it.endsWith(".onnx", ignoreCase = true) }
        if (models.size != 1) {
            throw IOException("Gói Piper phải có đúng một tệp .onnx")
        }
        val configName = configs.single()
        val modelName = models.single()
        val expectedModelName = configName.removeSuffix(".json")
        if (!modelName.equals(expectedModelName, ignoreCase = true)) {
            throw IOException("Tên cặp Piper không khớp: $modelName và $configName")
        }

        val displayName = modelName.removeSuffix(".onnx")
        val modelFile = File(directory, modelName)
        val configFile = File(directory, configName)
        moveToCanonical(modelFile, File(directory, LocalTtsModelRegistry.PIPER_MODEL_FILE))
        moveToCanonical(configFile, File(directory, LocalTtsModelRegistry.PIPER_CONFIG_FILE))

        val canonicalConfig = File(directory, LocalTtsModelRegistry.PIPER_CONFIG_FILE)
        val config: JsonObject = try {
            Json.parseToJsonElement(canonicalConfig.readText()).jsonObject
        } catch (error: Throwable) {
            throw IOException("Cấu hình Piper .onnx.json không hợp lệ", error)
        }
        if (!config.stringValue("phoneme_type").equals("espeak", ignoreCase = true)) {
            throw IOException("Piper hiện chỉ hỗ trợ phoneme_type espeak")
        }
        val sampleRate = config.objectValue("audio")?.intValue("sample_rate") ?: 0
        if (sampleRate !in 8_000..192_000) {
            throw IOException("sample_rate trong cấu hình Piper không hợp lệ")
        }
        val espeakVoice = config.objectValue("espeak")?.stringValue("voice").orEmpty().trim()
        if (espeakVoice.isEmpty()) throw IOException("Cấu hình Piper thiếu espeak.voice")
        val speakerCount = config.intValue("num_speakers") ?: 0
        if (speakerCount !in 1..1024) throw IOException("num_speakers của Piper không hợp lệ")
        val voices = parsePiperVoices(config, speakerCount, displayName)
        writePiperTokens(config, File(directory, LocalTtsModelRegistry.PIPER_TOKENS_FILE))

        val language = config.objectValue("language")
            ?.stringValue("name_english")
            ?.takeIf(String::isNotBlank)
            ?: espeakVoice
        OnnxMetadataEditor.addMissing(
            File(directory, LocalTtsModelRegistry.PIPER_MODEL_FILE),
            linkedMapOf(
                "model_type" to "vits",
                "comment" to "piper",
                "language" to language,
                "voice" to espeakVoice,
                "has_espeak" to "1",
                "n_speakers" to speakerCount.toString(),
                "sample_rate" to sampleRate.toString(),
            ),
        )
        return PiperDescriptor(
            displayName = displayName,
            language = espeakVoice,
            sampleRate = sampleRate,
            voices = voices,
        )
    }

    private fun parsePiperVoices(
        config: JsonObject,
        speakerCount: Int,
        displayName: String,
    ): List<LocalTtsVoice> {
        val speakerMap = config.objectValue("speaker_id_map")
        if (speakerMap == null || speakerMap.isEmpty()) {
            return List(speakerCount) { id ->
                LocalTtsVoice(id, if (speakerCount == 1) displayName else "$displayName ${id + 1}")
            }
        }
        val voices = speakerMap.entries
            .map { (name, value) ->
                LocalTtsVoice(value.jsonPrimitive.intOrNull ?: -1, name)
            }
            .sortedBy(LocalTtsVoice::id)
            .toList()
        if (voices.size != speakerCount || voices.map(LocalTtsVoice::id).toSet() != (0 until speakerCount).toSet()) {
            throw IOException("speaker_id_map của Piper không khớp num_speakers")
        }
        return voices
    }

    private fun writePiperTokens(config: JsonObject, target: File) {
        val idMap = config.objectValue("phoneme_id_map")
            ?: throw IOException("Cấu hình Piper thiếu phoneme_id_map")
        val tokens = idMap.entries.map { (symbol, value) ->
            val ids: JsonArray = value as? JsonArray
                ?: throw IOException("phoneme_id_map không hợp lệ tại ký hiệu $symbol")
            val id = ids.firstOrNull()?.jsonPrimitive?.intOrNull ?: -1
            if (id < 0) {
                throw IOException("phoneme_id_map không hợp lệ tại ký hiệu $symbol")
            }
            symbol to id
        }.toList()
        if (tokens.isEmpty()) throw IOException("phoneme_id_map của Piper đang trống")
        if (tokens.map { it.second }.distinct().size != tokens.size) {
            throw IOException("phoneme_id_map của Piper chứa ID trùng")
        }
        target.bufferedWriter(Charsets.UTF_8).use { writer ->
            tokens.sortedBy { it.second }.forEach { (symbol, id) ->
                writer.append(symbol).append(' ').append(id.toString()).append('\n')
            }
        }
    }

    private fun JsonObject.stringValue(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.intValue(name: String): Int? =
        this[name]?.jsonPrimitive?.intOrNull

    private fun JsonObject.objectValue(name: String): JsonObject? =
        this[name]?.jsonObject

    private fun moveToCanonical(source: File, target: File) {
        if (source.absolutePath == target.absolutePath) return
        if (target.exists()) throw IOException("Gói Piper chứa tên tệp xung đột: ${target.name}")
        if (!source.renameTo(target)) {
            source.copyTo(target)
            if (!source.delete()) throw IOException("Không thể chuẩn hóa tên tệp Piper")
        }
    }

    internal data class PiperDescriptor(
        val displayName: String,
        val language: String,
        val sampleRate: Int,
        val voices: List<LocalTtsVoice>,
    ) {
        fun toModel(directory: File, id: String, checksum: String): LocalTtsModel = LocalTtsModel(
            id = id,
            name = "Piper - $displayName",
            engine = LocalTtsModelRegistry.ENGINE_PIPER_VITS,
            language = language,
            sampleRate = sampleRate,
            voices = voices,
            defaultVoiceId = voices.first().id,
            attribution = "Piper voice imported by user; Android runtime by sherpa-onnx",
            license = "See the voice package license",
            directoryPath = directory.absolutePath,
            checksum = checksum,
            sizeBytes = directory.walkTopDown().filter(File::isFile).sumOf(File::length),
        )
    }

    private fun createModel(directory: File, id: String, checksum: String): LocalTtsModel {
        val config = try {
            JSONObject(File(directory, "tts_config.json").readText())
        } catch (error: Throwable) {
            throw IOException("tts_config.json không hợp lệ", error)
        }
        val speakers = config.optJSONObject("speakers")
            ?: throw IOException("tts_config.json thiếu danh sách speakers")
        val voices = speakers.keys().asSequence()
            .map { name -> LocalTtsVoice(speakers.getInt(name), name) }
            .sortedBy(LocalTtsVoice::id)
            .toList()
        if (voices.isEmpty()) throw IOException("Model không có giọng đọc")
        if (voices.map(LocalTtsVoice::id).distinct().size != voices.size) {
            throw IOException("tts_config.json chứa voice ID trùng")
        }
        val sampleRate = config.optInt("sample_rate", 24_000)
        if (sampleRate !in 8_000..192_000) {
            throw IOException("sample_rate trong tts_config.json không hợp lệ")
        }
        return LocalTtsModel(
            id = id,
            name = "Valtec Vietnamese TTS",
            engine = LocalTtsModelRegistry.ENGINE_VALTEC_VITS,
            language = "vi",
            sampleRate = sampleRate,
            voices = voices,
            defaultVoiceId = voices.first().id,
            attribution = "Valtec Team (runtime adapted for Legado)",
            license = "CC BY-NC 4.0",
            directoryPath = directory.absolutePath,
            checksum = checksum,
            sizeBytes = directory.walkTopDown().filter(File::isFile).sumOf(File::length),
        )
    }

    private fun String.isRecognizedTtsFile(): Boolean =
        this in LocalTtsModelRegistry.REQUIRED_FILES ||
            this == "LICENSE" ||
            this == "LICENSE.txt" ||
            endsWith(".onnx", ignoreCase = true) ||
            endsWith(".onnx.json", ignoreCase = true)

    private fun installAtomically(root: File, staging: File, id: String) {
        val target = File(root, id)
        val installing = File(root, "${id}_installing")
        val backup = File(root, "${id}_backup")
        installing.deleteRecursively()
        backup.deleteRecursively()
        staging.copyRecursively(installing, overwrite = true)
        var previousMoved = false
        try {
            if (target.exists()) {
                if (!target.renameTo(backup)) throw IOException("Không thể chuẩn bị thay model TTS cũ")
                previousMoved = true
            }
            if (!installing.renameTo(target)) {
                installing.copyRecursively(target, overwrite = true)
                installing.deleteRecursively()
            }
            backup.deleteRecursively()
        } catch (error: Throwable) {
            target.deleteRecursively()
            if (previousMoved && backup.exists()) backup.renameTo(target)
            throw error
        } finally {
            installing.deleteRecursively()
            if (backup.exists() && !target.exists()) backup.renameTo(target)
        }
    }

    internal fun cleanOrphanedStaging(root: File) {
        runCatching {
            root.listFiles()?.forEach { file ->
                if (file.isDirectory && (
                    file.name.startsWith("import_") ||
                    file.name.endsWith("_installing") ||
                    file.name.endsWith("_backup")
                )) {
                    file.deleteRecursively()
                }
            }
        }
    }

    internal const val MAX_ENTRIES = 20
    internal const val MAX_ENTRY_BYTES = 2L * 1024L * 1024L * 1024L
    internal const val MAX_TOTAL_BYTES = 4L * 1024L * 1024L * 1024L
    private const val MAX_SOURCE_PACKAGE_BYTES = 4L * 1024L * 1024L * 1024L
    private const val PROBE_TEXT = "Xin chao."
}
