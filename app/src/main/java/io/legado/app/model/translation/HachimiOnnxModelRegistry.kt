package io.legado.app.model.translation

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class NmtModelDescriptor(
    val id: String,
    val name: String,
    val headDimension: Long = 64L,
    val decoderLayers: Int = 2,
    val attentionHeads: Long = 8L,
    val defaultNoRepeatNgramSize: Int = 2,
    val attribution: String = "HachimiMT-60 by ngocdang83 · CC-BY-4.0",
    val supportsSourcePrompt: Boolean = false,
)

class HachimiOnnxModelRegistry(private val context: Context) {

    fun isInstalled(modelId: String = DEFAULT_MODEL_ID): Boolean =
        REQUIRED_FILES.all { fileName -> File(modelDirectory(modelId), fileName).isFile }

    fun missingFiles(modelId: String = DEFAULT_MODEL_ID): List<String> =
        REQUIRED_FILES.filterNot { fileName -> File(modelDirectory(modelId), fileName).isFile }

    fun installedDirectory(modelId: String = DEFAULT_MODEL_ID): File {
        val missing = missingFiles(modelId)
        if (missing.isNotEmpty()) {
            throw IOException(
                "NMT model '$modelId' is not installed. " +
                    "Open Translation settings, download the ZIP, then import it."
            )
        }
        return modelDirectory(modelId)
    }

    fun modelDescriptor(modelId: String = DEFAULT_MODEL_ID): NmtModelDescriptor {
        val dir = modelDirectory(modelId)
        val manifestFile = File(dir, MANIFEST_FILE)
        if (manifestFile.isFile) {
            val json = runCatching { JSONObject(manifestFile.readText()) }.getOrNull()
            if (json != null) {
                return NmtModelDescriptor(
                    id = json.optString("id", modelId),
                    name = json.optString("name", if (modelId == HACHIMI_QT_MODEL_ID) "HachimiMT-60-QT zh-vi" else "HachimiMT-60 zh-vi"),
                    headDimension = json.optLong("headDimension", if (modelId == HACHIMI_QT_MODEL_ID) 72L else 64L),
                    decoderLayers = json.optInt("decoderLayers", 2),
                    attentionHeads = json.optLong("attentionHeads", 8L),
                    defaultNoRepeatNgramSize = json.optInt("defaultNoRepeatNgramSize", if (modelId == HACHIMI_QT_MODEL_ID) 0 else 2),
                    attribution = json.optString("attribution", if (modelId == HACHIMI_QT_MODEL_ID) "HachimiMT-60-QT by ngocdang83 · CC-BY-4.0" else "HachimiMT-60 by ngocdang83 · CC-BY-4.0"),
                    supportsSourcePrompt = json.optBoolean("supportsSourcePrompt", false),
                )
            }
        }
        return if (modelId == HACHIMI_QT_MODEL_ID) {
            NmtModelDescriptor(
                id = HACHIMI_QT_MODEL_ID,
                name = "HachimiMT-60-QT zh-vi",
                headDimension = 72L,
                defaultNoRepeatNgramSize = 0,
                attribution = "HachimiMT-60-QT by ngocdang83 · CC-BY-4.0"
            )
        } else {
            NmtModelDescriptor(
                id = DEFAULT_MODEL_ID,
                name = "HachimiMT-60 zh-vi",
                headDimension = 64L,
                defaultNoRepeatNgramSize = 2,
                attribution = "HachimiMT-60 by ngocdang83 · CC-BY-4.0"
            )
        }
    }

    fun listInstalledModels(): List<NmtModelDescriptor> {
        val root = modelRoot()
        val dirs = root.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.filter { dir ->
            REQUIRED_FILES.all { File(dir, it).isFile }
        }.map { dir ->
            modelDescriptor(dir.name)
        }
    }

    internal fun modelRoot(): File = File(context.filesDir, MODEL_ROOT).apply {
        if (!exists() && !mkdirs()) {
            throw IOException("Could not create NMT model directory")
        }
        if (!isDirectory) {
            throw IOException("NMT model path is not a directory")
        }
    }

    internal fun modelDirectory(modelId: String = DEFAULT_MODEL_ID): File = File(modelRoot(), modelId)

    companion object {
        const val MODEL_ROOT = "nmt_models"
        const val MODEL_ID = "hachimi_onnx"
        const val DEFAULT_MODEL_ID = "hachimi_onnx"
        const val HACHIMI_QT_MODEL_ID = "hachimi_mt60_qt_zh_vi"
        const val ENCODER_FILE = "encoder_model.onnx"
        const val DECODER_FILE = "decoder_model_merged.onnx"
        const val TOKENIZER_FILE = "tokenizer.onnx"
        const val TARGET_TOKENIZER_FILE = "target_tokenizer.onnx"
        const val DETOKENIZER_FILE = "detokenizer.onnx"
        const val MANIFEST_FILE = "model_manifest.json"
        val REQUIRED_FILES = listOf(
            ENCODER_FILE,
            DECODER_FILE,
            TOKENIZER_FILE,
            TARGET_TOKENIZER_FILE,
            DETOKENIZER_FILE,
        )
        val OPTIONAL_FILES = setOf(
            MANIFEST_FILE,
            "NOTICE.txt",
            "README.md",
            "README.txt",
            "LICENSE",
            "LICENSE.txt",
        )

        fun isOptionalCompanionFile(fileName: String): Boolean =
            fileName in OPTIONAL_FILES ||
                fileName.endsWith(".onnx_data", ignoreCase = true) ||
                fileName.endsWith(".data", ignoreCase = true) ||
                fileName.endsWith(".bin", ignoreCase = true)
    }
}

