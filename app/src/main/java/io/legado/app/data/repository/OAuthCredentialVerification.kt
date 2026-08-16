package io.legado.app.data.repository

import io.legado.app.domain.model.AiCredentialStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal suspend fun <T> verifyOAuthCredentialModels(
    models: List<T>,
    updateStatus: suspend (String) -> Unit,
    probe: suspend (T) -> Unit,
    isCredentialRejected: (Throwable) -> Boolean = ::isOAuthCredentialRejected,
): T {
    require(models.isNotEmpty()) { "OAuth provider has no model to test" }
    updateStatus(AiCredentialStatus.VERIFYING)
    var lastError: Throwable? = null
    for (model in models) {
        try {
            probe(model)
            updateStatus(AiCredentialStatus.ACTIVE)
            return model
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                // Tokens are already persisted; cancellation must not strand a valid account.
                updateStatus(AiCredentialStatus.ACTIVE)
            }
            throw error
        } catch (error: Throwable) {
            if (isCredentialRejected(error)) {
                updateStatus(AiCredentialStatus.RELOGIN_REQUIRED)
                throw error
            }
            lastError = error
        }
    }
    check(lastError != null) { "OAuth provider has no model to test" }
    // Capacity or a model-specific protocol failure does not invalidate the freshly issued token.
    // The runtime route can try the complete fallback list with its normal retry policy.
    updateStatus(AiCredentialStatus.ACTIVE)
    return models.first()
}

internal fun isOAuthCredentialRejected(error: Throwable): Boolean {
    val messages = generateSequence(error as Throwable?) { it.cause }
        .take(8)
        .mapNotNull(Throwable::message)
        .joinToString(" | ")
        .lowercase()
    val status = Regex("(?:http\\s+|^)([1-5]\\d{2})(?=\\D|$)", RegexOption.IGNORE_CASE)
        .find(messages)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    return status == 401 || listOf(
        "invalid_grant",
        "token_invalid",
        "invalid access token",
        "access token expired",
        "refresh token expired",
        "refresh token reused",
    ).any(messages::contains)
}
