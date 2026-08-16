package io.legado.app.service

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ResultReceiver
import android.os.IBinder
import io.legado.app.data.repository.HachimiDecodePolicy
import io.legado.app.data.repository.HachimiLexicalConstraint
import io.legado.app.data.repository.HachimiOnnxTranslator
import io.legado.app.domain.gateway.NmtDecodeConfig
import io.legado.app.domain.gateway.NmtTranslationResult
import io.legado.app.domain.model.DictPair
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hosts the ONNX NMT runtime outside the UI/application process.
 *
 * ONNX Runtime can reserve a large native arena while importing or decoding a model. Keeping it
 * in a private process makes an allocation failure recoverable: Android can kill this process
 * without taking the reader, cache, or translation editor down with it.
 */
class NmtOnnxService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val messenger by lazy { Messenger(Handler(Looper.getMainLooper(), ::handleMessage)) }
    private val translator by lazy { HachimiOnnxTranslator(applicationContext) }
    private var activeRequestId: Long = 0L
    private var activeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        activeJob?.cancel()
        serviceScope.cancel()
        runCatching { kotlinx.coroutines.runBlocking { translator.close() } }
        super.onDestroy()
    }

    private fun handleMessage(message: Message): Boolean {
        val data = message.data
        // ResultReceiver is written to the Bundle with the concrete receiver class name. A
        // Bundle delivered through Messenger otherwise uses the boot class loader in the remote
        // process and throws BadParcelableException before we can report an NMT error. Always set
        // the application class loader before reading the receiver.
        data.classLoader = NmtOnnxService::class.java.classLoader
        @Suppress("DEPRECATION")
        val receiver = runCatching {
            data.getParcelable(NmtOnnxIpc.KEY_RESULT_RECEIVER) as? ResultReceiver
        }.getOrElse { error ->
            android.util.Log.e("NmtOnnxService", "Cannot decode NMT result receiver", error)
            null
        }
        return when (message.what) {
            NmtOnnxIpc.WHAT_TRANSLATE -> {
                val requestId = data.getLong(NmtOnnxIpc.KEY_REQUEST_ID)
                val text = data.getString(NmtOnnxIpc.KEY_TEXT).orEmpty()
                if (receiver == null) return true
                activeJob?.cancel()
                activeRequestId = requestId
                activeJob = serviceScope.launch {
                    runTranslation(requestId, text, data, receiver)
                }
                true
            }

            NmtOnnxIpc.WHAT_CANCEL -> {
                if (data.getLong(NmtOnnxIpc.KEY_REQUEST_ID) == activeRequestId) {
                    activeJob?.cancel()
                    activeJob = null
                }
                true
            }

            NmtOnnxIpc.WHAT_CLOSE -> {
                activeJob?.cancel()
                activeJob = serviceScope.launch {
                    runCatching { translator.close() }
                    stopSelf()
                }
                true
            }

            else -> false
        }
    }

    private suspend fun runTranslation(
        requestId: Long,
        text: String,
        data: Bundle,
        receiver: ResultReceiver,
    ) {
        try {
            val dictionary = GSON.fromJsonArray<DictPair>(
                data.getString(NmtOnnxIpc.KEY_DICTIONARY_JSON)
            ).getOrElse { emptyList() }
            val config = data.getString(NmtOnnxIpc.KEY_CONFIG_JSON)
                ?.let { GSON.fromJson(it, NmtDecodeConfig::class.java) }
                ?: NmtDecodeConfig()
            val constraints = dictionary.asSequence()
                .filter { it.original.isNotBlank() && it.translation.isNotBlank() }
                .distinctBy { it.original.trim() }
                .take(MAX_CONSTRAINTS)
                .map { pair ->
                    HachimiLexicalConstraint(
                        sourceKeys = listOf(pair.original.trim()),
                        target = pair.translation.trim(),
                        required = true,
                        matchEverySourceOccurrence = true,
                        canonicalizeSourceName = false,
                    )
                }
                .toList()
            val result = translator.translate(
                text = text,
                policy = HachimiDecodePolicy(
                    lexicalConstraints = constraints,
                    maxNewTokens = config.maxNewTokens.coerceIn(32, 384),
                    repetitionPenalty = config.repetitionPenalty.coerceIn(1f, 2f),
                    noRepeatNgramSize = config.noRepeatNgramSize,
                    retryMissingRequiredTerms = config.retryMissingRequiredTerms,
                    maxSourceTokens = config.maxSourceTokens.coerceIn(32, 480),
                    maxSourceChars = config.maxSourceChars.coerceIn(10, 10_000),
                    sourcePrompt = config.sourcePrompt.trim(),
                ),
                onProgress = { completed, total, mixed ->
                    receiver.send(
                        NmtOnnxIpc.RESULT_PROGRESS,
                        Bundle().apply {
                            putLong(NmtOnnxIpc.KEY_REQUEST_ID, requestId)
                            putInt(NmtOnnxIpc.KEY_COMPLETED_SEGMENTS, completed)
                            putInt(NmtOnnxIpc.KEY_TOTAL_SEGMENTS, total)
                            putString(NmtOnnxIpc.KEY_MIXED_TEXT, mixed)
                        }
                    )
                },
            )
            receiver.send(
                NmtOnnxIpc.RESULT_SUCCESS,
                Bundle().apply {
                    putLong(NmtOnnxIpc.KEY_REQUEST_ID, requestId)
                    putString(
                        NmtOnnxIpc.KEY_RESULT_JSON,
                        GSON.toJson(
                            NmtTranslationResult(
                                text = result.text,
                                sourceSegments = result.sourceSegments,
                                generatedTokens = result.generatedTokens,
                                missingRequiredTerms = result.missingRequiredTerms,
                                attribution = result.attribution,
                            )
                        )
                    )
                }
            )
        } catch (cancelled: CancellationException) {
            receiver.send(
                NmtOnnxIpc.RESULT_CANCELLED,
                Bundle().apply { putLong(NmtOnnxIpc.KEY_REQUEST_ID, requestId) }
            )
        } catch (error: Throwable) {
            receiver.send(
                NmtOnnxIpc.RESULT_ERROR,
                Bundle().apply {
                    putLong(NmtOnnxIpc.KEY_REQUEST_ID, requestId)
                    putString(NmtOnnxIpc.KEY_ERROR_CLASS, error::class.java.name)
                    putString(
                        NmtOnnxIpc.KEY_ERROR_MESSAGE,
                        error.localizedMessage ?: "NMT ONNX process failed",
                    )
                }
            )
        } finally {
            if (activeRequestId == requestId) activeJob = null
        }
    }

    private companion object {
        const val MAX_CONSTRAINTS = 64
    }
}
