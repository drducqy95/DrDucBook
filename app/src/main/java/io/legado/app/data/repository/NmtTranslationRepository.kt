package io.legado.app.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.os.ResultReceiver
import io.legado.app.domain.gateway.NmtDecodeConfig
import io.legado.app.domain.gateway.NmtTranslationGateway
import io.legado.app.domain.gateway.NmtTranslationResult
import io.legado.app.domain.model.DictPair
import io.legado.app.service.NmtOnnxIpc
import io.legado.app.service.NmtOnnxService
import io.legado.app.utils.GSON
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * Client-side NMT gateway. The ONNX model is never loaded in the application process; all calls
 * cross the private Messenger boundary to [NmtOnnxService].
 */
class NmtTranslationRepository(
    context: Context,
) : NmtTranslationGateway {
    private val appContext = context.applicationContext
    private val requestMutex = Mutex()
    private val requestIds = AtomicLong(0L)
    private val callbackScope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var serviceMessenger: Messenger? = null
    private var connectionDeferred: CompletableDeferred<Messenger>? = null
    private var bound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: android.os.IBinder?) {
            val messenger = service?.let(::Messenger)
            if (messenger == null) {
                connectionDeferred?.completeExceptionally(IOException("NMT service returned no binder"))
                connectionDeferred = null
                return
            }
            serviceMessenger = messenger
            connectionDeferred?.complete(messenger)
            connectionDeferred = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceMessenger = null
            connectionDeferred?.completeExceptionally(IOException("NMT service disconnected"))
            connectionDeferred = null
            bound = false
        }

        override fun onBindingDied(name: ComponentName?) {
            onServiceDisconnected(name)
        }

        override fun onNullBinding(name: ComponentName?) {
            onServiceDisconnected(name)
        }
    }

    override suspend fun translate(
        text: String,
        dictionary: List<DictPair>,
        config: NmtDecodeConfig,
        onProgress: suspend (completedSegments: Int, totalSegments: Int, mixedText: String) -> Unit,
    ): NmtTranslationResult = requestMutex.withLock {
        withTimeout(TRANSLATION_TIMEOUT_MS) {
            val messenger = awaitService()
            val requestId = requestIds.incrementAndGet()
            suspendCancellableCoroutine { continuation ->
                val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        val data = resultData ?: Bundle()
                        if (data.getLong(NmtOnnxIpc.KEY_REQUEST_ID, requestId) != requestId) return
                        when (resultCode) {
                            NmtOnnxIpc.RESULT_PROGRESS -> {
                                val completed = data.getInt(NmtOnnxIpc.KEY_COMPLETED_SEGMENTS)
                                val total = data.getInt(NmtOnnxIpc.KEY_TOTAL_SEGMENTS)
                                val mixed = data.getString(NmtOnnxIpc.KEY_MIXED_TEXT).orEmpty()
                                callbackScope.launch {
                                    if (continuation.isActive) onProgress(completed, total, mixed)
                                }
                            }

                            NmtOnnxIpc.RESULT_SUCCESS -> {
                                val json = data.getString(NmtOnnxIpc.KEY_RESULT_JSON)
                                val result = runCatching {
                                    GSON.fromJson(json, NmtTranslationResult::class.java)
                                }.getOrNull()
                                if (result == null) {
                                    continuation.resumeWithException(IOException("NMT returned an invalid result"))
                                } else if (continuation.isActive) {
                                    continuation.resume(result)
                                }
                            }

                            NmtOnnxIpc.RESULT_CANCELLED -> {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(CancellationException("NMT request cancelled"))
                                }
                            }

                            NmtOnnxIpc.RESULT_ERROR -> {
                                val message = data.getString(NmtOnnxIpc.KEY_ERROR_MESSAGE)
                                    ?: "NMT ONNX process failed"
                                if (continuation.isActive) continuation.resumeWithException(IOException(message))
                            }
                        }
                    }
                }
                val message = Message.obtain(null, NmtOnnxIpc.WHAT_TRANSLATE).apply {
                    data = Bundle().apply {
                        putLong(NmtOnnxIpc.KEY_REQUEST_ID, requestId)
                        putString(NmtOnnxIpc.KEY_TEXT, text)
                        putString(NmtOnnxIpc.KEY_DICTIONARY_JSON, GSON.toJson(dictionary))
                        putString(NmtOnnxIpc.KEY_CONFIG_JSON, GSON.toJson(config))
                        putParcelable(NmtOnnxIpc.KEY_RESULT_RECEIVER, receiver)
                    }
                }
                try {
                    messenger.send(message)
                } catch (error: RemoteException) {
                    continuation.resumeWithException(IOException("Không thể kết nối tiến trình NMT", error))
                }
                continuation.invokeOnCancellation {
                    sendCancel(messenger, requestId)
                }
            }
        }
    }

    override suspend fun close() = requestMutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            serviceMessenger?.let { messenger ->
                runCatching { messenger.send(Message.obtain(null, NmtOnnxIpc.WHAT_CLOSE)) }
            }
            serviceMessenger = null
            connectionDeferred?.cancel()
            connectionDeferred = null
            if (bound) {
                runCatching { appContext.unbindService(serviceConnection) }
                bound = false
            }
        }
    }

    private suspend fun awaitService(): Messenger {
        serviceMessenger?.let { return it }
        return withContext(Dispatchers.Main.immediate) {
            serviceMessenger?.let { return@withContext it }
            connectionDeferred?.let { return@withContext it.await() }
            val deferred = CompletableDeferred<Messenger>()
            connectionDeferred = deferred
            val intent = Intent(appContext, NmtOnnxService::class.java)
            bound = appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                connectionDeferred = null
                deferred.completeExceptionally(IOException("Không thể khởi động tiến trình NMT"))
            }
            try {
                withTimeout(SERVICE_BIND_TIMEOUT_MS) { deferred.await() }
            } catch (error: TimeoutCancellationException) {
                connectionDeferred = null
                if (bound) {
                    runCatching { appContext.unbindService(serviceConnection) }
                    bound = false
                }
                throw IOException("Tiến trình NMT không phản hồi", error)
            }
        }
    }

    private fun sendCancel(messenger: Messenger, requestId: Long) {
        runCatching {
            messenger.send(Message.obtain(null, NmtOnnxIpc.WHAT_CANCEL).apply {
                data = Bundle().apply { putLong(NmtOnnxIpc.KEY_REQUEST_ID, requestId) }
            })
        }
    }

    private companion object {
        const val MAX_CONSTRAINTS = 64
        const val SERVICE_BIND_TIMEOUT_MS = 10_000L
        const val TRANSLATION_TIMEOUT_MS = 180_000L
    }
}
