package com.drducbook.app.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.drducbook.app.cloud.SupabaseClientProvider
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Base64

class GoogleCredentialToken(
    val idToken: String,
    val nonce: String,
) {
    override fun toString(): String = "GoogleCredentialToken(idToken=<redacted>, nonce=<redacted>)"
}

object GoogleCredentialBridge {

    /** Uses Supabase PKCE in the external browser when Credential Manager is blocked on a device. */
    fun openBrowserFallback(context: Context): Result<Unit> = runCatching {
        val client = SupabaseClientProvider.client ?: error("Supabase is not configured")
        val url = client.auth.getOAuthUrl(
            Google,
            redirectUrl = DrDucBookDeepLinks.AUTH_CALLBACK,
        )
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    /** Converts provider errors into an actionable message without exposing token details. */
    fun userMessage(error: Throwable): String {
        val raw = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .firstOrNull(String::isNotBlank)
            .orEmpty()
        return when {
            raw.contains("access_denied", ignoreCase = true) ||
                raw.contains("not completed verification", ignoreCase = true) ->
                "Google đang chặn DrDucBook vì ứng dụng đang ở chế độ kiểm thử. Hãy thêm tài khoản vào Test users trong Google Cloud hoặc hoàn tất xác minh OAuth."
            raw.contains("DEVELOPER_ERROR", ignoreCase = true) || raw.contains("status code: 10") ->
                "Cấu hình Google của DrDucBook chưa khớp. Hãy kiểm tra OAuth client, package com.drducbook.app và SHA-1 của bản đang cài."
            raw.isNotBlank() -> raw
            else -> "Không thể đăng nhập bằng Google"
        }
    }

    suspend fun requestIdToken(
        context: Context,
        serverClientId: String,
        nonce: String = GoogleAuthNonce.generate(),
    ): Result<GoogleCredentialToken> {
        if (serverClientId.isBlank()) {
            return Result.failure(IllegalStateException("Google Auth client ID is not configured"))
        }
        return runCatching {
            val credentialManager = CredentialManager.create(context)
            val hashedNonce = GoogleAuthNonce.sha256Hex(nonce)
            val response = try {
                credentialManager.getCredential(
                    context,
                    request(
                        serverClientId = serverClientId,
                        hashedNonce = hashedNonce,
                        filterByAuthorizedAccounts = true,
                    ),
                )
            } catch (_: NoCredentialException) {
                credentialManager.getCredential(
                    context,
                    request(
                        serverClientId = serverClientId,
                        hashedNonce = hashedNonce,
                        filterByAuthorizedAccounts = false,
                    ),
                )
            }
            val credential = response.credential
            if (
                credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleCredentialToken(
                    idToken = googleIdToken.idToken,
                    nonce = nonce,
                )
            } else {
                error("Google Credential Manager returned an unsupported credential")
            }
        }.recoverCatching { error ->
            when (error) {
                is GoogleIdTokenParsingException -> throw IllegalStateException("Google ID token is invalid", error)
                is GetCredentialCancellationException -> throw CancellationException("Google sign-in was cancelled", error)
                is GetCredentialException -> throw IllegalStateException(error.message ?: "Google sign-in failed", error)
                else -> throw error
            }
        }
    }

    private fun request(
        serverClientId: String,
        hashedNonce: String,
        filterByAuthorizedAccounts: Boolean,
    ): GetCredentialRequest = GetCredentialRequest.Builder()
        .addCredentialOption(
            GetGoogleIdOption.Builder()
                .setServerClientId(serverClientId)
                .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
                .setNonce(hashedNonce)
                .build()
        )
        .build()
}

object GoogleAuthNonce {

    private val secureRandom = SecureRandom()

    fun generate(byteCount: Int = NONCE_BYTES): String {
        require(byteCount in 16..64) { "Nonce byte count must be between 16 and 64" }
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }

    fun sha256Hex(nonce: String): String {
        require(nonce.isNotBlank()) { "Nonce must not be blank" }
        return MessageDigest.getInstance("SHA-256")
            .digest(nonce.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private const val NONCE_BYTES = 32
}
