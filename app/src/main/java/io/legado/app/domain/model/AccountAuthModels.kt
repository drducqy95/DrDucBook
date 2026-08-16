package io.legado.app.domain.model

data class AccountSession(
    val userId: String,
    val email: String?,
    val emailVerified: Boolean,
    val providerIds: Set<String>,
    val expiresAtEpochMillis: Long?,
) {
    val signedIn: Boolean
        get() = userId.isNotBlank()
}

data class AccountEmailCredentials(
    val email: String,
    val password: String,
) {
    val normalizedEmail: String
        get() = email.trim().lowercase()

    fun requireValid() {
        require(normalizedEmail.contains('@')) { "Email is invalid" }
        require(password.length >= MIN_PASSWORD_LENGTH) {
            "Password must be at least $MIN_PASSWORD_LENGTH characters"
        }
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
    }
}

class AccountGoogleIdCredential(
    val idToken: String,
    val nonce: String,
) {
    fun requireValid() {
        require(idToken.isNotBlank()) { "Google ID token is required" }
        require(nonce.isNotBlank()) { "Google nonce is required" }
    }

    override fun toString(): String = "AccountGoogleIdCredential(idToken=<redacted>, nonce=<redacted>)"
}

sealed interface AccountAuthResult {
    data class SignedIn(val session: AccountSession) : AccountAuthResult
    data class EmailVerificationRequired(val email: String) : AccountAuthResult
    data object SignedOut : AccountAuthResult
}

enum class AccountSignOutMode {
    LOCAL,
    GLOBAL,
}
