package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.AccountAuthGateway
import io.legado.app.domain.model.AccountAuthResult
import io.legado.app.domain.model.AccountEmailCredentials
import io.legado.app.domain.model.AccountGoogleIdCredential
import io.legado.app.domain.model.AccountSession
import io.legado.app.domain.model.AccountSignOutMode
import kotlinx.coroutines.flow.Flow

class AccountAuthUseCase(
    private val accountAuthGateway: AccountAuthGateway,
) {

    fun observeSession(): Flow<AccountSession?> = accountAuthGateway.observeSession()

    suspend fun currentSession(): AccountSession? = accountAuthGateway.currentSession()

    suspend fun currentAccessToken(): String? = accountAuthGateway.currentAccessToken()

    suspend fun signUpWithEmail(email: String, password: String): AccountAuthResult =
        accountAuthGateway.signUpWithEmail(AccountEmailCredentials(email, password))

    suspend fun signInWithEmail(email: String, password: String): AccountSession =
        accountAuthGateway.signInWithEmail(AccountEmailCredentials(email, password))

    suspend fun signInOrLinkGoogle(idToken: String, nonce: String): AccountSession =
        accountAuthGateway.signInOrLinkGoogle(AccountGoogleIdCredential(idToken, nonce))

    suspend fun sendPasswordReset(email: String) =
        accountAuthGateway.sendPasswordReset(email)

    suspend fun reauthenticate() =
        accountAuthGateway.reauthenticate()

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        require(currentPassword.isNotBlank()) { "Mật khẩu hiện tại không được để trống" }
        require(newPassword.length >= 8) { "Mật khẩu mới phải có ít nhất 8 ký tự" }
        require(newPassword != currentPassword) { "Mật khẩu mới phải khác mật khẩu hiện tại" }
        accountAuthGateway.changePassword(currentPassword, newPassword)
    }

    suspend fun refreshSession() =
        accountAuthGateway.refreshSession()

    suspend fun signOut(mode: AccountSignOutMode = AccountSignOutMode.LOCAL): AccountAuthResult.SignedOut =
        accountAuthGateway.signOut(mode)
}
