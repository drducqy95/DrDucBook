package io.legado.app.data.repository

import com.drducbook.app.auth.DrDucBookDeepLinks
import com.drducbook.app.cloud.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.legado.app.domain.gateway.AccountAuthGateway
import io.legado.app.domain.model.AccountAuthResult
import io.legado.app.domain.model.AccountEmailCredentials
import io.legado.app.domain.model.AccountGoogleIdCredential
import io.legado.app.domain.model.AccountSession
import io.legado.app.domain.model.AccountSignOutMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class SupabaseAccountAuthRepository(
    private val clientProvider: () -> SupabaseClient? = { SupabaseClientProvider.client },
) : AccountAuthGateway {

    override fun observeSession(): Flow<AccountSession?> {
        val auth = clientProvider()?.auth ?: return flowOf(null)
        return flow {
            auth.awaitInitialization()
            auth.currentSessionOrNull()
                .toAccountSession()
                ?.let { session -> emit(session) }
            emitAll(
                auth.sessionStatus
                    .filter { status ->
                        status is SessionStatus.Authenticated ||
                            status is SessionStatus.NotAuthenticated
                    }
                    .map { status -> status.toAccountSession() }
            )
        }
            .distinctUntilChanged()
    }

    override suspend fun currentSession(): AccountSession? {
        val auth = clientProvider()?.auth ?: return null
        auth.awaitInitialization()
        return auth.currentSessionOrNull().toAccountSession()
    }

    override suspend fun currentAccessToken(): String? {
        val auth = clientProvider()?.auth ?: return null
        auth.awaitInitialization()
        return auth.currentAccessTokenOrNull()
    }

    override suspend fun signUpWithEmail(credentials: AccountEmailCredentials): AccountAuthResult {
        credentials.requireValid()
        val auth = requireAuth()
        auth.signUpWith(Email, redirectUrl = DrDucBookDeepLinks.AUTH_CALLBACK) {
            email = credentials.normalizedEmail
            password = credentials.password
        }
        return auth.currentSessionOrNull()
            ?.toAccountSession()
            ?.let(AccountAuthResult::SignedIn)
            ?: AccountAuthResult.EmailVerificationRequired(credentials.normalizedEmail)
    }

    override suspend fun signInWithEmail(credentials: AccountEmailCredentials): AccountSession {
        credentials.requireValid()
        val auth = requireAuth()
        auth.signInWith(Email, redirectUrl = DrDucBookDeepLinks.AUTH_CALLBACK) {
            email = credentials.normalizedEmail
            password = credentials.password
        }
        return requireNotNull(auth.currentSessionOrNull().toAccountSession()) {
            "Supabase did not return a session"
        }
    }

    override suspend fun signInOrLinkGoogle(credential: AccountGoogleIdCredential): AccountSession {
        credential.requireValid()
        val auth = requireAuth()
        if (auth.currentSessionOrNull() != null) {
            auth.linkIdentityWithIdToken(
                provider = Google,
                idToken = credential.idToken,
            ) {
                nonce = credential.nonce
            }
            auth.refreshCurrentSession()
        } else {
            auth.signInWith(IDToken, redirectUrl = DrDucBookDeepLinks.AUTH_CALLBACK) {
                idToken = credential.idToken
                provider = Google
                nonce = credential.nonce
            }
        }
        return requireNotNull(auth.currentSessionOrNull().toAccountSession()) {
            "Supabase did not return a Google session"
        }
    }

    override suspend fun sendPasswordReset(email: String) {
        val normalized = email.trim().lowercase()
        require(normalized.contains('@')) { "Email is invalid" }
        requireAuth().resetPasswordForEmail(
            email = normalized,
            redirectUrl = DrDucBookDeepLinks.AUTH_CALLBACK,
        )
    }

    override suspend fun reauthenticate() {
        requireAuth().reauthenticate()
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        requireAuth().updateUser {
            password = newPassword
            this.currentPassword = currentPassword
        }
    }

    override suspend fun refreshSession() {
        requireAuth().refreshCurrentSession()
    }

    override suspend fun signOut(mode: AccountSignOutMode): AccountAuthResult.SignedOut {
        val scope = when (mode) {
            AccountSignOutMode.LOCAL -> SignOutScope.LOCAL
            AccountSignOutMode.GLOBAL -> SignOutScope.GLOBAL
        }
        requireAuth().signOut(scope)
        return AccountAuthResult.SignedOut
    }

    private fun requireAuth() = requireNotNull(clientProvider()?.auth) {
        "Supabase is not configured"
    }
}

internal fun SessionStatus.toAccountSession(): AccountSession? = when (this) {
    is SessionStatus.Authenticated -> session.toAccountSession()
    else -> null
}

internal fun UserSession?.toAccountSession(): AccountSession? {
    val user = this?.user ?: return null
    return AccountSession(
        userId = user.id,
        email = user.email,
        emailVerified = user.emailConfirmedAt != null || user.confirmedAt != null,
        providerIds = user.identities
            .orEmpty()
            .mapNotNull { it.provider }
            .toSet(),
        expiresAtEpochMillis = expiresAt.toEpochMilliseconds(),
    )
}
