package io.legado.app.domain.gateway

import io.legado.app.domain.model.DownloadAttention
import io.legado.app.domain.model.DownloadCenterAction
import io.legado.app.domain.model.DownloadCenterSnapshot
import kotlinx.coroutines.flow.Flow

interface DownloadCenterGateway {
    fun observeSnapshot(): Flow<DownloadCenterSnapshot>
    fun observeAttention(): Flow<DownloadAttention>
    suspend fun perform(action: DownloadCenterAction)
}
