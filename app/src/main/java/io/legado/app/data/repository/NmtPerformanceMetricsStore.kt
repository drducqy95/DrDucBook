package io.legado.app.data.repository

import android.content.Context
import io.legado.app.domain.gateway.NmtPerformanceMetrics
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray

/**
 * Small local rolling store for aggregate NMT diagnostics. It intentionally contains no source,
 * prompt, token text, dictionary entries or translated content.
 */
internal class NmtPerformanceMetricsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun append(metrics: NmtPerformanceMetrics) {
        val current = preferences.getString(KEY_METRICS, null)
            ?.let { GSON.fromJsonArray<NmtPerformanceMetrics>(it).getOrNull() }
            .orEmpty()
        preferences.edit()
            .putString(KEY_METRICS, GSON.toJson((current + metrics).takeLast(MAX_ENTRIES)))
            .apply()
    }

    @Synchronized
    fun read(): List<NmtPerformanceMetrics> = preferences.getString(KEY_METRICS, null)
        ?.let { GSON.fromJsonArray<NmtPerformanceMetrics>(it).getOrNull() }
        .orEmpty()

    private companion object {
        const val PREFERENCES_NAME = "nmt_performance_metrics"
        const val KEY_METRICS = "rolling_metrics"
        const val MAX_ENTRIES = 20
    }
}
