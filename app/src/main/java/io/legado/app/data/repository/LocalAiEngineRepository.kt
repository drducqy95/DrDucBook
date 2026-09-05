package io.legado.app.data.repository

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.LocalAiCorruptedModelException
import io.legado.app.domain.gateway.LocalAiEngineGateway
import io.legado.app.domain.gateway.LocalAiModelMetadata
import io.legado.app.domain.gateway.LocalAiOutOfMemoryException
import io.legado.app.domain.gateway.LocalAiUnsupportedAbiException
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.LocalAiDeviceInfo
import io.legado.app.domain.model.LocalAiModelCatalog
import io.legado.app.domain.model.LocalAiRuntimePlanner
import io.legado.app.domain.model.LocalAiRuntimeProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** Process-scoped owner of the native model. Requests are serialized because one context/KV cache
 * is shared; the loaded weights stay memory-mapped between adjacent chunks. */
class LocalAiEngineRepository(
    private val context: Context,
) : LocalAiEngineGateway {

    private val modelMutex = Mutex()
    private var loadedPath: String? = null
    private var nativeHandle: Long = 0L
    private var idleUnloadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private fun scheduleIdleUnload() {
        idleUnloadJob?.cancel()
        idleUnloadJob = scope.launch {
            delay(10 * 60 * 1000L) // 10 minutes
            unload()
        }
    }

    override val nativeRuntimeAvailable: Boolean
        get() = LocalAiNativeBridge.isAvailable

    override suspend fun inspectModel(modelPath: String): Result<LocalAiModelMetadata> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (file, version) = requireGguf(modelPath)
                metadata(file, "", version)
            }
        }

    override suspend fun validateModel(modelPath: String): Result<LocalAiModelMetadata> =
        inspectModel(modelPath)

    override suspend fun importModel(
        sourceUri: String,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)?,
    ): Result<LocalAiModelMetadata> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(sourceUri)
                if (uri.scheme == "file" || sourceUri.startsWith("/")) {
                    val directPath = if (sourceUri.startsWith("/")) sourceUri else uri.path.orEmpty()
                    val directFile = File(directPath)
                    if (directFile.exists() && directFile.isFile) {
                        val (_, version) = requireGguf(directFile)
                        val digest = MessageDigest.getInstance("SHA-256")
                        directFile.inputStream().buffered().use { input ->
                            val buf = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                            while (true) {
                                val r = input.read(buf)
                                if (r < 0) break
                                digest.update(buf, 0, r)
                            }
                        }
                        val hash = digest.digest().joinToString("") { "%02x".format(it) }
                        return@runCatching metadata(directFile, hash, version)
                    }
                }

                val document = DocumentFile.fromSingleUri(context, uri)
                val rawName = resolveDisplayName(uri, document)
                val cleanName = rawName.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").takeLast(160)
                val sourceName = if (cleanName.endsWith(".gguf", ignoreCase = true)) cleanName else "$cleanName.gguf"
                val declaredSize = document?.length()?.takeIf { it > 0 } ?: 0L
                val modelDir = File(
                    context.getExternalFilesDir(null) ?: context.filesDir,
                    "local-ai/models",
                ).apply { mkdirs() }
                if (declaredSize > 0) {
                    require(modelDir.usableSpace > declaredSize + MIN_FREE_SPACE_AFTER_IMPORT) {
                        "Không đủ dung lượng bộ nhớ trống để nhập model này"
                    }
                }
                val target = File(modelDir, sourceName)
                val temporary = File(modelDir, ".$sourceName.tmp")
                runCatching {
                    modelDir.listFiles()?.forEach { file ->
                        if (file.name.endsWith(".tmp") || file.name.endsWith(".importing")) file.delete()
                    }
                }
                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    var totalRead = 0L
                    context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                        temporary.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                digest.update(buffer, 0, read)
                                output.write(buffer, 0, read)
                                totalRead += read
                                onProgress?.invoke(totalRead, declaredSize)
                            }
                        }
                    } ?: error("Không thể mở tệp model đã chọn")
                    val hash = digest.digest().joinToString("") { "%02x".format(it) }
                    verifyKnownHyMt2Hash(sourceName, hash)
                    val (_, version) = requireGguf(temporary)
                    if (target.exists() && !target.delete()) error("Không thể thay thế model cũ")
                    if (!temporary.renameTo(target)) {
                        temporary.copyTo(target, overwrite = true)
                        temporary.delete()
                    }
                    metadata(target, hash, version)
                } finally {
                    if (temporary.exists()) temporary.delete()
                }
            }
        }

    private fun resolveDisplayName(uri: Uri, document: DocumentFile?): String {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            val name = cursor.getString(index)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
        val docName = document?.name
        if (!docName.isNullOrBlank()) return docName
        val lastSegment = uri.lastPathSegment.orEmpty().substringAfterLast('/')
        if (lastSegment.isNotBlank()) return lastSegment
        return "imported_model.gguf"
    }

    override fun generateStream(
        modelPath: String,
        request: AiGenerateRequest,
    ): Flow<AiStreamEvent> = callbackFlow {
        if (!LocalAiNativeBridge.isAvailable) {
            close(IllegalStateException(LocalAiNativeBridge.loadErrorMessage))
            return@callbackFlow
        }
        idleUnloadJob?.cancel()
        val cancellationRequested = AtomicBoolean(false)
        val worker = Thread {
            try {
                kotlinx.coroutines.runBlocking {
                    modelMutex.withLock {
                        val (file, _) = requireGguf(modelPath)
                        val profile = runtimeProfile(
                            request.model.contextWindow.takeIf { it > 0 } ?: DEFAULT_CONTEXT_WINDOW
                        )
                        ensureLoaded(file, profile)
                        val callback = object : LocalAiNativeBridge.Callback {
                            override fun onToken(text: String) {
                                trySend(AiStreamEvent.Content(text))
                            }

                            override fun isCancelled(): Boolean = cancellationRequested.get()
                        }
                        LocalAiNativeBridge.generate(
                            handle = nativeHandle,
                            roles = request.messages.map { it.role }.toTypedArray(),
                            contents = request.messages.map { it.content }.toTypedArray(),
                            maxOutputTokens = request.params.maxOutputTokens ?: 1_024,
                            temperature = request.params.temperature ?: 0.7f,
                            topP = request.params.topP ?: 0.6f,
                            topK = request.params.topK ?: 20,
                            repetitionPenalty = request.params.repetitionPenalty ?: 1.05f,
                            callback = callback,
                        )
                        if (cancellationRequested.get()) releaseLoadedModel()
                    }
                }
                scheduleIdleUnload()
                close()
            } catch (error: CancellationException) {
                releaseAfterFailedGeneration()
                close(error)
            } catch (error: Throwable) {
                releaseAfterFailedGeneration()
                close(error)
            }
        }.apply {
            name = "legado-local-ai"
            priority = Thread.NORM_PRIORITY
            start()
        }
        awaitClose {
            cancellationRequested.set(true)
            LocalAiNativeBridge.cancel()
            worker.interrupt()
        }
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    override suspend fun unload() = withContext(Dispatchers.IO) {
        modelMutex.withLock {
            idleUnloadJob?.cancel()
            idleUnloadJob = null
            if (nativeHandle != 0L) LocalAiNativeBridge.free(nativeHandle)
            nativeHandle = 0L
            loadedPath = null
        }
    }

    private fun ensureLoaded(file: File, profile: LocalAiRuntimeProfile) {
        if (loadedPath == file.absolutePath && nativeHandle != 0L) return
        if (nativeHandle != 0L) LocalAiNativeBridge.free(nativeHandle)

        // Memory pre-check before calling into C++ engine
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val availMb = memoryInfo.availMem / (1_024L * 1_024L)
        if (availMb < 300L) {
            throw LocalAiOutOfMemoryException(
                "RAM khả dụng quá thấp (${availMb}MB). Local AI cần ít nhất 350MB RAM trống để nạp model ${file.name}. Vui lòng đóng bớt ứng dụng chạy ngầm và thử lại."
            )
        }

        nativeHandle = LocalAiNativeBridge.load(
            nativeLibDir = context.applicationInfo.nativeLibraryDir,
            modelPath = file.absolutePath,
            contextWindow = profile.contextWindow,
            threads = profile.threads,
            batchThreads = profile.batchThreads,
            batchSize = profile.batchSize,
            microBatchSize = profile.microBatchSize,
            useMmap = profile.useMmap,
            useMlock = profile.useMlock,
            gpuLayers = profile.gpuLayers,
        )
        check(nativeHandle != 0L) {
            "Native engine không thể nạp tệp model ${file.name}. Vui lòng kiểm tra tính toàn vẹn của tệp GGUF."
        }
        loadedPath = file.absolutePath
    }

    private fun releaseAfterFailedGeneration() {
        runCatching {
            kotlinx.coroutines.runBlocking {
                modelMutex.withLock { releaseLoadedModel() }
            }
        }
    }

    private fun releaseLoadedModel() {
        if (nativeHandle != 0L) runCatching { LocalAiNativeBridge.free(nativeHandle) }
        nativeHandle = 0L
        loadedPath = null
    }

    private fun runtimeProfile(contextWindow: Int): LocalAiRuntimeProfile {
        return LocalAiRuntimePlanner.plan(
            device = deviceInfo(),
            requestedContextWindow = contextWindow,
        )
    }

    private fun deviceInfo(): LocalAiDeviceInfo {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        return LocalAiDeviceInfo(
            primaryAbi = Build.SUPPORTED_64_BIT_ABIS.firstOrNull()
                ?: Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            supportedAbis = Build.SUPPORTED_ABIS.toSet(),
            availableProcessors = Runtime.getRuntime().availableProcessors(),
            totalMemoryMb = memoryInfo.totalMem / (1_024L * 1_024L),
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
        )
    }

    private fun requireGguf(modelPath: String): Pair<File, Int> = requireGguf(File(modelPath))

    private fun requireGguf(file: File): Pair<File, Int> {
        if (!file.isFile || !file.canRead()) {
            throw LocalAiCorruptedModelException(
                "Tệp model GGUF không tồn tại hoặc không thể đọc: ${file.absolutePath}"
            )
        }
        if (file.length() < 1024L * 1024L) {
            throw LocalAiCorruptedModelException(
                "Tệp model GGUF bị lỗi hoặc quá nhỏ (${file.length()} bytes)"
            )
        }
        val version = RandomAccessFile(file, "r").use { input ->
            val header = ByteArray(8)
            val read = input.read(header)
            if (read < 8) {
                throw LocalAiCorruptedModelException("Không thể đọc header của tệp GGUF: ${file.name}")
            }
            val magic = header.copyOfRange(0, 4)
            if (!magic.contentEquals(GGUF_MAGIC)) {
                throw LocalAiCorruptedModelException("Tệp đã chọn không phải định dạng GGUF hợp lệ (magic header không khớp)")
            }
            val buffer = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN)
            val v = buffer.int
            if (v !in 1..3) {
                throw LocalAiCorruptedModelException("Phiên bản GGUF v$v không được hỗ trợ (chỉ hỗ trợ v2/v3)")
            }
            v
        }
        return file to version
    }

    private fun metadata(file: File, hash: String, ggufVersion: Int = 3): LocalAiModelMetadata {
        val device = deviceInfo()
        val profile = LocalAiRuntimePlanner.plan(
            device = device,
            requestedContextWindow = DEFAULT_CONTEXT_WINDOW,
        )
        return LocalAiModelMetadata(
            name = file.nameWithoutExtension,
            path = file.absolutePath,
            sizeBytes = file.length(),
            contextWindow = profile.contextWindow,
            runtimeProfile = profile,
            sha256 = hash,
            primaryAbi = device.primaryAbi,
            totalMemoryMb = device.totalMemoryMb,
            ggufVersion = ggufVersion,
        )
    }

    private fun verifyKnownHyMt2Hash(fileName: String, hash: String) {
        val normalizedFileName = fileName.lowercase()
        val expected = LocalAiModelCatalog.all.firstOrNull { catalog ->
            catalog.fileName.equals(fileName, ignoreCase = true)
        }?.sha256 ?: when (normalizedFileName) {
            "hy-mt2-1.8b-1.25bit.gguf" -> LocalAiModelCatalog.hyMt2V1.sha256
            else -> return
        }
        if (!hash.equals(expected, ignoreCase = true)) {
            io.legado.app.constant.AppLog.putDebug("Model $fileName checksum: $hash differs from catalog $expected, proceeding with custom GGUF")
        }
    }

    private companion object {
        const val DEFAULT_CONTEXT_WINDOW = 4_096
        const val MIN_FREE_SPACE_AFTER_IMPORT = 256L * 1_024L * 1_024L
        val GGUF_MAGIC = byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte())
    }
}

internal object LocalAiNativeBridge {
    val loadErrorMessage: String
    val isAvailable: Boolean

    init {
        val supported64Bit = Build.SUPPORTED_64_BIT_ABIS
        if (supported64Bit == null || supported64Bit.isEmpty()) {
            isAvailable = false
            loadErrorMessage = "Thiết bị không hỗ trợ 64-bit ABI (chỉ hỗ trợ: ${Build.SUPPORTED_ABIS.joinToString()}). Local AI yêu cầu arm64-v8a hoặc x86_64."
        } else {
            val result = runCatching { System.loadLibrary("legado_local_ai") }
            isAvailable = result.isSuccess
            loadErrorMessage = result.exceptionOrNull()?.let { err ->
                "Không thể nạp thư viện native liblegado_local_ai.so: ${err.message ?: err::class.java.simpleName}. ABI: ${supported64Bit.firstOrNull()}"
            } ?: "The local AI native runtime is not packaged for this device ABI"
        }
    }

    interface Callback {
        fun onToken(text: String)
        fun isCancelled(): Boolean
    }

    external fun load(
        nativeLibDir: String,
        modelPath: String,
        contextWindow: Int,
        threads: Int,
        batchThreads: Int,
        batchSize: Int,
        microBatchSize: Int,
        useMmap: Boolean,
        useMlock: Boolean,
        gpuLayers: Int,
    ): Long

    external fun generate(
        handle: Long,
        roles: Array<String>,
        contents: Array<String>,
        maxOutputTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repetitionPenalty: Float,
        callback: Callback,
    )

    external fun cancel()
    external fun free(handle: Long)
}
