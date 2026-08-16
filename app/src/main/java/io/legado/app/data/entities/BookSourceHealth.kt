package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.legado.app.domain.model.BookSourceHealthStatus

@Entity(
    tableName = "book_source_health",
    indices = [Index("status"), Index("lastChecked")],
)
data class BookSourceHealth(
    @PrimaryKey val sourceUrl: String,
    val status: String = BookSourceHealthStatus.UNKNOWN_OFFLINE.name,
    val lastChecked: Long = 0L,
    val latencyMs: Long? = null,
    val httpStatus: Int? = null,
    val failureStep: String? = null,
    val messageRedacted: String? = null,
    val consecutiveFailures: Int = 0,
) {
    val statusValue: BookSourceHealthStatus
        get() = runCatching { BookSourceHealthStatus.valueOf(status) }
            .getOrDefault(BookSourceHealthStatus.UNKNOWN_OFFLINE)
}
