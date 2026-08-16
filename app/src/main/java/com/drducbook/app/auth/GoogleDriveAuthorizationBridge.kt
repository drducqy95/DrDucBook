package com.drducbook.app.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.drducbook.app.cloud.CloudConsentScopes
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class GoogleDriveAuthorization(
    val accessToken: String?,
    val resolution: PendingIntent?,
) {
    init {
        require(!accessToken.isNullOrBlank() || resolution != null) {
            "Google Drive authorization returned no token or consent flow"
        }
    }

    override fun toString(): String =
        "GoogleDriveAuthorization(accessToken=<redacted>, resolution=${resolution != null})"
}

object GoogleDriveAuthorizationBridge {

    suspend fun authorize(context: Context): Result<GoogleDriveAuthorization> = runCatching {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(CloudConsentScopes.googleDriveAppData)))
            .build()
        val result = Identity.getAuthorizationClient(context)
            .authorize(request)
            .awaitResult()
        val pendingIntent = result.pendingIntent
        GoogleDriveAuthorization(
            accessToken = result.accessToken?.takeIf(String::isNotBlank),
            resolution = pendingIntent,
        )
    }.recoverCatching(::normalizeError)

    fun completeAuthorization(context: Context, data: Intent): Result<String> = runCatching {
        Identity.getAuthorizationClient(context)
            .getAuthorizationResultFromIntent(data)
            .accessToken
            ?.takeIf(String::isNotBlank)
            ?: error("Google Drive did not return an access token")
    }.recoverCatching(::normalizeError)

    private fun normalizeError(error: Throwable): Nothing = when (error) {
        is CancellationException -> throw error
        is ApiException -> throw IllegalStateException(
            when (error.statusCode) {
                403 -> "Google Drive từ chối quyền truy cập. Hãy bật Drive API và thêm tài khoản vào Test users trong màn hình OAuth consent của Google Cloud."
                401 -> "Phiên Google Drive đã hết hạn. Hãy cấp quyền lại."
                10 -> "Cấu hình Google Drive của DrDucBook chưa khớp. Hãy kiểm tra package com.drducbook.app và SHA-1 của bản đang cài trong OAuth client Android."
                else -> error.localizedMessage ?: "Google Drive authorization failed (${error.statusCode})"
            },
            error,
        )
        else -> throw error
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value ->
            if (continuation.isActive) continuation.resume(value)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        addOnCanceledListener {
            if (continuation.isActive) {
                continuation.resumeWithException(CancellationException("Google Drive authorization cancelled"))
            }
        }
    }
}
