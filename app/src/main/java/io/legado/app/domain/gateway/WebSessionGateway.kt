package io.legado.app.domain.gateway

import io.legado.app.domain.model.WebSessionCredential
import kotlinx.coroutines.flow.Flow

interface WebSessionGateway {
    fun observeSession(sessionType: String): Flow<WebSessionCredential?>
    suspend fun getSession(sessionType: String): WebSessionCredential?
    suspend fun saveSession(credential: WebSessionCredential)
    suspend fun clearSession(sessionType: String)
    suspend fun refreshSession(sessionType: String): Result<WebSessionCredential>
}
