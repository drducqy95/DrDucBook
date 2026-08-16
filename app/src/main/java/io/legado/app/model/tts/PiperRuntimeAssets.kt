package io.legado.app.model.tts

import android.content.Context
import java.io.File
import java.io.IOException

internal class PiperRuntimeAssets(context: Context) {
    private val appContext = context.applicationContext

    fun ensureInstalled(): File = synchronized(lock) {
        val root = File(appContext.filesDir, RUNTIME_ROOT).apply { mkdirs() }
        val target = File(root, DATA_DIRECTORY)
        if (target.isReady()) return@synchronized target

        val staging = File(root, "${DATA_DIRECTORY}_installing")
        staging.deleteRecursively()
        check(staging.mkdirs()) { "Không thể tạo thư mục eSpeak-ng" }
        try {
            copyAssetTree(ASSET_PATH, staging)
            if (!staging.isReady()) throw IOException("Gói eSpeak-ng thiếu dữ liệu tiếng Việt")
            target.deleteRecursively()
            if (!staging.renameTo(target)) {
                staging.copyRecursively(target, overwrite = true)
            }
            if (!target.isReady()) throw IOException("Không thể cài dữ liệu eSpeak-ng")
            target
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun copyAssetTree(assetPath: String, destination: File) {
        val children = appContext.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            appContext.assets.open(assetPath).use { input ->
                destination.outputStream().buffered().use(input::copyTo)
            }
            return
        }
        destination.mkdirs()
        children.forEach { name ->
            copyAssetTree("$assetPath/$name", File(destination, name))
        }
    }

    private fun File.isReady(): Boolean =
        File(this, "phontab").isFile &&
            File(this, "phondata").isFile &&
            File(this, "lang/aav/vi").isFile

    companion object {
        private const val ASSET_PATH = "espeak-ng-data"
        private const val RUNTIME_ROOT = "tts_runtime/sherpa-onnx-1.13.4"
        private const val DATA_DIRECTORY = "espeak-ng-data"
        private val lock = Any()
    }
}
