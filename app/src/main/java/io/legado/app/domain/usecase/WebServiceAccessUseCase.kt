package io.legado.app.domain.usecase

import io.legado.app.domain.model.AccountPermission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi

class WebServiceAccessUseCase(
    private val accountAuthUseCase: AccountAuthUseCase,
    private val accountAccessUseCase: AccountAccessUseCase,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAllowed(): Flow<Boolean> = accountAuthUseCase.observeSession()
        .transformLatest { session ->
            if (session == null) {
                emit(false)
                return@transformLatest
            }
            val access = runCatching {
                accountAccessUseCase.getAccess(session.userId)
            }.getOrNull() ?: return@transformLatest
            val allowed = access.allows(AccountPermission.WEB_SERVICE)
            emit(allowed)
            val expiresAt = access.roleExpiresAtEpochMillis
            if (allowed && expiresAt != null) {
                delay((expiresAt - System.currentTimeMillis()).coerceAtLeast(0L))
                emit(false)
            }
        }
        .catch { }
        .distinctUntilChanged()

    suspend fun isAllowed(): Boolean {
        val session = accountAuthUseCase.currentSession() ?: return false
        return accountAccessUseCase
            .getAccess(session.userId)
            .allows(AccountPermission.WEB_SERVICE)
    }

    suspend fun requireAllowed() {
        require(isAllowed()) {
            "Web service requires a Premium or Admin account"
        }
    }
}
