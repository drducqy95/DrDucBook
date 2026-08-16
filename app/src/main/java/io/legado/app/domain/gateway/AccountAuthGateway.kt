package io.legado.app.domain.gateway

import io.legado.app.domain.model.AccountAuthResult
import io.legado.app.domain.model.AccountEmailCredentials
import io.legado.app.domain.model.AccountGoogleIdCredential
import io.legado.app.domain.model.AccountSession
import io.legado.app.domain.model.AccountSignOutMode
import kotlinx.coroutines.flow.Flow

interface AccountAuthGateway {

    fun observeSession(): Flow<AccountSession?>

    suspend fun currentSession(): AccountSession?

    suspend fun currentAccessToken(): String?

    suspend fun signUpWithEmail(credentials: AccountEmailCredentials): AccountAuthResult

    suspend fun signInWithEmail(credentials: AccountEmailCredentials): AccountSession

    suspend fun signInOrLinkGoogle(credential: AccountGoogleIdCredential): AccountSession

    suspend fun sendPasswordReset(email: String)

    suspend fun reauthenticate()

    suspend fun changePassword(currentPassword: String, newPassword: String)

    suspend fun refreshSession()

    suspend fun signOut(mode: AccountSignOutMode = AccountSignOutMode.LOCAL): AccountAuthResult.SignedOut
}
