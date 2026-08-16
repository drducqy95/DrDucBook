package io.legado.app.data.repository.ai

import io.legado.app.help.http.okHttpClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * AI generation can legitimately spend several minutes before the next token arrives.
 * A generous inactivity and total-call timeout prevents a broken provider stream from
 * keeping translation and chat jobs alive forever.
 */
internal const val AI_READ_TIMEOUT_MILLIS = 3L * 60L * 1000L
internal const val AI_CALL_TIMEOUT_MILLIS = 20L * 60L * 1000L

internal val aiOkHttpClient: OkHttpClient by lazy {
    okHttpClient.newBuilder()
        .configureAiTimeouts()
        .build()
}

internal fun OkHttpClient.Builder.configureAiTimeouts(): OkHttpClient.Builder =
    readTimeout(AI_READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .callTimeout(AI_CALL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
