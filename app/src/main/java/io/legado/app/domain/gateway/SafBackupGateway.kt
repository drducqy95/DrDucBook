package io.legado.app.domain.gateway

import android.net.Uri

interface SafBackupGateway {
    val configured: Boolean
    fun selectedTreeUri(): Uri?
    val scheduleIntervalHours: Long?
    suspend fun setTreeUri(uri: Uri)
    suspend fun clearTreeUri()
    suspend fun setSchedule(intervalHours: Long, password: String)
    suspend fun clearSchedule()
    suspend fun uploadLatest(password: String): Long
    suspend fun restoreLatest(password: String): Boolean
    suspend fun runScheduled(): Boolean
}
