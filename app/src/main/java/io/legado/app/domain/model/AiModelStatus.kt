package io.legado.app.domain.model

import androidx.annotation.Keep

@Keep
object AiModelStatus {
    const val ACTIVE = "active"
    const val STALE = "stale"
    const val DEPRECATED = "deprecated"

    /**
     * Time (in milliseconds) a model remains in STALE status before being automatically
     * transitioned to DEPRECATED (7 days).
     */
    const val STALE_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
}
