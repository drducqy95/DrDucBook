package io.legado.app.data.repository

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.LocalAiEngineGateway
import io.legado.app.domain.gateway.LocalAiModelMetadata
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.LocalAiDeviceInfo
import io.legado.app.domain.model.LocalAiModelCatalog
import io.legado.app.domain.model.LocalAiRuntimePlanner
import io.legado.app.domain.model.LocalAiRuntimeProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
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
                val file = requireGguf(modelPath)
                metadata(file, "")
            }
        }

    override suspend fun importModel(sourceUri: String): Result<LocalAiModelMetadata> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(sourceUri)
                val document = DocumentFile.fromSingleUri(context, uri)
                val sourceName = document?.name.orEmpty()
                    .ifBlank { uri.lastPathSegment.orEmpty() }
                    .replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
                    .takeLast(160)
                require(sourceName.endsWith(".gguf", ignoreCase = true)) {
                    "Only GGUF models are supported"
                }
                val declaredSize = document?.length()?.takeIf { it > 0 } ?: 0L
                val modelDir = File(
                    context.getExternalFilesDir(null) ?: context.filesDir,
                    "local-ai/models",
                ).apply { mkdirs() }
                if (declaredSize > 0) {
                    require(modelDir.usableSpace > declaredSize + MIN_FREE_SPACE_AFTER_IMPORT) {
                        "Not enough free storage to import this model"
                    }
                }
                val target = File(modelDir, sourceName)
                val temporary = File(modelDir, ".$sourceName.importing")
                runCatching {
                    modelDir.listFiles()?.forEach { file ->
                        if (file.name.endsWith(".importing")) file.delete()
                    }
                }
                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                        temporary.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                require(modelDir.usableSpace > MIN_FREE_SPACE_AFTER_IMPORT + read) {
                                    "Not enough free storage to finish importing this model"
                                }
                                digest.update(buffer, 0, read)
                                output.write(buffer, 0, read)
                            }
                        }
                    } ?: error("Không thể mở model đã chọn")
                    val hash = digest.digest().joinToString("") { "%02x".format(it) }
                    verifyKnownHyMt2Hash(sourceName, hash)
                    requireGguf(temporary)
                    if (target.exists() && !target.delete()) error("Không thể thay thế model")
                    if (!temporary.renameTo(target)) {
                        temporary.copyTo(target, overwrite = true)
                        temporary.delete()
                    }
                    metadata(target, hash)
                } finally {
                    temporary.delete()
                }
            }
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
                        val file = requireGguf(modelPath)
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
    }.flowOn(Dispatchers.IO)

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
        check(nativeHandle != 0L) { "Native engine failed to load ${file.name}" }
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

    private fun requireGguf(modelPath: String): File = requireGguf(File(modelPath))

    private fun requireGguf(file: File): File {
        require(file.isFile && file.canRead()) {
            "Local GGUF model is not readable: ${file.absolutePath}"
        }
        require(file.extension.equals("gguf", ignoreCase = true)) { "Only GGUF models are supported" }
        RandomAccessFile(file, "r").use { input ->
            val magic = ByteArray(4)
            require(input.read(magic) == magic.size && magic.contentEquals(GGUF_MAGIC)) {
                "The selected file is not a valid GGUF model"
            }
        }
        return file
    }

    private fun metadata(file: File, hash: String): LocalAiModelMetadata {
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
        require(hash.equals(expected, ignoreCase = true)) {
            "Hy-MT2 model checksum does not match the pinned Legado release"
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
        val result = runCatching { System.loadLibrary("legado_local_ai") }
        isAvailable = result.isSuccess
        loadErrorMessage = result.exceptionOrNull()?.message
            ?: "The local AI native runtime is not packaged for this device ABI"
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
