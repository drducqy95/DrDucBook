package io.legado.app.data.repository

import android.content.Context
import io.legado.app.domain.gateway.AnonymousAccountQuotaGateway
import io.legado.app.domain.model.AccountQuotaKind
import io.legado.app.domain.model.AccountQuotaUsage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneOffset

class AnonymousAccountQuotaRepository(
    context: Context,
) : AnonymousAccountQuotaGateway {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun consumeDailyQuota(
        kind: AccountQuotaKind,
        operationKeys: Set<String>,
    ): AccountQuotaUsage = mutex.withLock {
        val key = "${LocalDate.now(ZoneOffset.UTC)}:${kind.storageValue}"
        val usedKeys = preferences.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        val resultingKeys = usedKeys + operationKeys
        require(resultingKeys.size <= kind.freeDailyLimit) {
            "daily_quota_exceeded:${kind.storageValue}:${usedKeys.size}:${kind.freeDailyLimit}"
        }
        preferences.edit().putStringSet(key, resultingKeys).apply()
        AccountQuotaUsage(
            kind = kind,
            used = resultingKeys.size,
            limit = kind.freeDailyLimit,
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "anonymous_account_quota"
    }
}
