package io.legado.app.model.tts

import android.content.Context
import com.drducbook.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class LocalTtsModelRegistry(private val context: Context) {

    fun list(): List<LocalTtsModel> {
        ensureBundledDebugModel()
        return modelRoot().listFiles().orEmpty()
        .asSequence()
        .filter(File::isDirectory)
        .mapNotNull { directory -> runCatching { read(directory) }.getOrNull() }
        .sortedBy { it.name.lowercase() }
        .toList()
    }

    fun get(id: String): LocalTtsModel? {
        ensureBundledDebugModel()
        if (!SAFE_ID.matches(id)) return null
        val directory = File(modelRoot(), id)
        return runCatching { read(directory) }.getOrNull()
    }

    fun delete(id: String): Boolean {
        if (!SAFE_ID.matches(id)) return false
        val directory = File(modelRoot(), id)
        return !directory.exists() || directory.deleteRecursively()
    }

    internal fun modelRoot(): File = File(context.filesDir, MODEL_ROOT).apply { mkdirs() }

    private fun ensureBundledDebugModel() {
        if (!BuildConfig.DEBUG) return
        val debugAssetAvailable = runCatching {
            context.assets.open("$BUNDLED_DEBUG_ASSET_PATH/tts_config.json").use { }
            true
        }.getOrDefault(false)
        if (!debugAssetAvailable) return
        val root = modelRoot()
        val target = File(root, BUNDLED_DEBUG_ID)
        if (File(target, MANIFEST_FILE).isFile && REQUIRED_FILES.all { File(target, it).isFile }) {
            return
        }
        val staging = File(root, "${BUNDLED_DEBUG_ID}_bundled_installing")
        staging.deleteRecursively()
        check(staging.mkdirs()) { "Không thể tạo thư mục cài model TTS debug" }
        try {
            REQUIRED_FILES.forEach { fileName ->
                context.assets.open("$BUNDLED_DEBUG_ASSET_PATH/$fileName").use { input ->
                    File(staging, fileName).outputStream().buffered().use(input::copyTo)
                }
            }
            runCatching {
                context.assets.open("$BUNDLED_DEBUG_ASSET_PATH/LICENSE").use { input ->
                    File(staging, "LICENSE").outputStream().buffered().use(input::copyTo)
                }
            }
            val config = JSONObject(File(staging, "tts_config.json").readText())
            val speakers = config.getJSONObject("speakers")
            val voices = speakers.keys().asSequence()
                .map { name -> LocalTtsVoice(speakers.getInt(name), name) }
                .sortedBy(LocalTtsVoice::id)
                .toList()
            val model = LocalTtsModel(
                id = BUNDLED_DEBUG_ID,
                name = "Valtec Vietnamese TTS (Debug)",
                engine = ENGINE_VALTEC_VITS,
                language = "vi",
                sampleRate = config.optInt("sample_rate", 24_000),
                voices = voices,
                defaultVoiceId = voices.firstOrNull()?.id ?: 0,
                attribution = "Valtec Team; bundled in debug builds for testing",
                license = "CC BY-NC 4.0",
                directoryPath = staging.absolutePath,
                checksum = calculateChecksum(staging, ENGINE_VALTEC_VITS),
                sizeBytes = staging.directorySize(),
            )
            write(model, staging)
            target.deleteRecursively()
            if (!staging.renameTo(target)) {
                staging.copyRecursively(target, overwrite = true)
            }
        } catch (error: Throwable) {
            target.deleteRecursively()
            throw IOException("Không thể cài model Valtec nhúng trong bản debug", error)
        } finally {
            staging.deleteRecursively()
        }
    }

    internal fun write(model: LocalTtsModel, directory: File) {
        val json = JSONObject()
            .put("schemaVersion", 2)
            .put("id", model.id)
            .put("name", model.name)
            .put("engine", model.engine)
            .put("language", model.language)
            .put("sampleRate", model.sampleRate)
            .put("defaultVoiceId", model.defaultVoiceId)
            .put("attribution", model.attribution)
            .put("license", model.license)
            .put("checksum", model.checksum)
            .put("sizeBytes", model.sizeBytes)
            .put("voices", JSONArray().apply {
                model.voices.forEach { voice ->
                    put(JSONObject().put("id", voice.id).put("name", voice.name))
                }
            })
        File(directory, MANIFEST_FILE).writeText(json.toString(2))
    }

    private fun read(directory: File): LocalTtsModel {
        val manifest = File(directory, MANIFEST_FILE)
        if (!manifest.isFile) throw IOException("Thiếu $MANIFEST_FILE")
        val json = JSONObject(manifest.readText())
        val id = json.getString("id")
        if (!SAFE_ID.matches(id) || directory.name != id) throw IOException("ID model TTS không hợp lệ")
        val engine = json.getString("engine")
        requiredFiles(engine).forEach { fileName ->
            if (!File(directory, fileName).isFile) throw IOException("Thiếu $fileName")
        }
        val voicesJson = json.optJSONArray("voices") ?: JSONArray()
        val voices = buildList {
            for (index in 0 until voicesJson.length()) {
                val voice = voicesJson.getJSONObject(index)
                add(LocalTtsVoice(voice.getInt("id"), voice.getString("name")))
            }
        }
        if (voices.isEmpty()) throw IOException("Model TTS không có giọng đọc")
        return LocalTtsModel(
            id = id,
            name = json.getString("name"),
            engine = engine,
            language = json.optString("language", "vi"),
            sampleRate = json.optInt("sampleRate", 24_000),
            voices = voices,
            defaultVoiceId = json.optInt("defaultVoiceId", voices.first().id),
            attribution = json.optString("attribution"),
            license = json.optString("license"),
            directoryPath = directory.absolutePath,
            checksum = json.optString("checksum").ifBlank { calculateChecksum(directory, engine) },
            sizeBytes = json.optLong("sizeBytes", directory.directorySize()),
        )
    }

    internal fun calculateChecksum(directory: File, engine: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        requiredFiles(engine).sorted().forEach { fileName ->
            digest.update(fileName.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            File(directory, fileName).inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun File.directorySize(): Long =
        walkTopDown().filter(File::isFile).sumOf(File::length)

    companion object {
        const val ENGINE_VALTEC_VITS = "valtec-vits-onnx-v1"
        const val ENGINE_PIPER_VITS = "piper-vits-onnx-v1"
        const val MANIFEST_FILE = "tts-model.json"
        const val MODEL_ROOT = "tts_models"
        const val BUNDLED_DEBUG_ID = "76616c7465632d766e2d7631"
        const val PIPER_MODEL_FILE = "model.onnx"
        const val PIPER_CONFIG_FILE = "model.onnx.json"
        const val PIPER_TOKENS_FILE = "tokens.txt"
        private const val BUNDLED_DEBUG_ASSET_PATH = "tts_models/valtec_vi"
        val REQUIRED_FILES = setOf(
            "text_encoder.onnx",
            "duration_predictor.onnx",
            "flow.onnx",
            "decoder.onnx",
            "tts_config.json",
        )
        val PIPER_REQUIRED_FILES = setOf(
            PIPER_MODEL_FILE,
            PIPER_CONFIG_FILE,
            PIPER_TOKENS_FILE,
        )

        internal fun requiredFiles(engine: String): Set<String> = when (engine) {
            ENGINE_VALTEC_VITS -> REQUIRED_FILES
            ENGINE_PIPER_VITS -> PIPER_REQUIRED_FILES
            else -> throw IOException("Engine model TTS chưa được hỗ trợ: $engine")
        }

        private val SAFE_ID = Regex("[a-f0-9]{24}")
    }
}
