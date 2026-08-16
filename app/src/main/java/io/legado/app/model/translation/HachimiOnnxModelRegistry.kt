package io.legado.app.model.translation

import android.content.Context
import java.io.File
import java.io.IOException

class HachimiOnnxModelRegistry(private val context: Context) {

    fun isInstalled(): Boolean =
        REQUIRED_FILES.all { fileName -> File(modelDirectory(), fileName).isFile }

    fun missingFiles(): List<String> =
        REQUIRED_FILES.filterNot { fileName -> File(modelDirectory(), fileName).isFile }

    fun installedDirectory(): File {
        val missing = missingFiles()
        if (missing.isNotEmpty()) {
            throw IOException(
                "NMT/Hachimi ONNX model is not installed. " +
                    "Open Translation settings, download the ZIP, then import it."
            )
        }
        return modelDirectory()
    }

    internal fun modelRoot(): File = File(context.filesDir, MODEL_ROOT).apply {
        if (!exists() && !mkdirs()) {
            throw IOException("Could not create NMT model directory")
        }
        if (!isDirectory) {
            throw IOException("NMT model path is not a directory")
        }
    }

    internal fun modelDirectory(): File = File(modelRoot(), MODEL_ID)

    companion object {
        const val MODEL_ROOT = "nmt_models"
        const val MODEL_ID = "hachimi_onnx"
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
