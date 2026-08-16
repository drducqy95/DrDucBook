package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.AnonymousAccountQuotaGateway
import io.legado.app.domain.model.AccountAccess
import io.legado.app.domain.model.AccountQuotaKind
import io.legado.app.domain.model.AccountQuotaUsage
import java.security.MessageDigest

class AccountEntitlementUseCase(
    private val accountAuthUseCase: AccountAuthUseCase,
    private val accountAccessUseCase: AccountAccessUseCase,
    private val anonymousAccountQuotaGateway: AnonymousAccountQuotaGateway,
) {
    suspend fun currentAccess(): AccountAccess {
        val session = accountAuthUseCase.currentSession() ?: return AccountAccess.anonymous()
        return accountAccessUseCase.getAccess(session.userId)
    }

    suspend fun consume(
        kind: AccountQuotaKind,
        operationIds: Collection<String>,
    ): AccountQuotaUsage {
        val session = accountAuthUseCase.currentSession()
        val keys = operationIds
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .map(::sha256)
            .toSet()
        require(keys.isNotEmpty()) { "Không có tác vụ cần tính hạn mức" }
        require(keys.size <= 100) { "Mỗi lần chỉ hỗ trợ tối đa 100 tác vụ" }
        return if (session == null) {
            anonymousAccountQuotaGateway.consumeDailyQuota(kind, keys)
        } else {
            accountAccessUseCase.consumeDailyQuota(kind, keys)
        }
    }

    suspend fun requireLocalTtsImportAllowed(installedModelCount: Int) {
        val limit = currentAccess().featureLimits.maxInstalledLocalTtsModels ?: return
        require(installedModelCount < limit) {
            "Tài khoản Free chỉ được lưu $limit model TTS cục bộ. Hãy xóa model hiện có hoặc nâng cấp Premium."
        }
    }

    suspend fun requireTtsModelCountAllowed(resultingModelCount: Int) {
        val limit = currentAccess().featureLimits.maxActiveTtsModels ?: return
        require(resultingModelCount <= limit) {
            "Tài khoản Free chỉ được sử dụng tối đa $limit model TTS"
        }
    }

    private fun sha256(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
