package io.legado.app.domain.model

import androidx.annotation.Keep
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException

@Keep
enum class AiFailureKind(
    val retryable: Boolean,
    val vietnameseLabel: String,
    val vietnameseAction: String,
) {
    ROUTE_UNAVAILABLE(
        true,
        "Combo AI đang tạm nghỉ",
        "Chờ hết thời gian tạm nghỉ hoặc chọn model/combo khác.",
    ),
    CONFIGURATION(
        false,
        "Cấu hình AI chưa hợp lệ",
        "Kiểm tra protocol, URL, model và API key trong cấu hình AI.",
    ),
    AUTHENTICATION(
        false,
        "Xác thực hoặc quyền truy cập bị từ chối",
        "Kiểm tra API key, token đăng nhập và quyền dùng model.",
    ),
    RATE_LIMIT(
        true,
        "Provider đang giới hạn tần suất",
        "Chờ rồi thử lại, giảm số request đồng thời hoặc đổi key.",
    ),
    QUOTA(
        false,
        "Đã hết hạn mức hoặc gói dịch vụ",
        "Kiểm tra quota, billing hoặc đổi tài khoản/provider.",
    ),
    TIMEOUT(
        true,
        "Provider phản hồi quá thời gian",
        "Giảm chunk, kiểm tra mạng hoặc thử lại.",
    ),
    NETWORK(
        true,
        "Không kết nối được tới provider",
        "Kiểm tra mạng, DNS, proxy và URL provider.",
    ),
    PROTOCOL(
        false,
        "Endpoint hoặc định dạng phản hồi không tương thích",
        "Kiểm tra protocol, endpoint và model đã chọn.",
    ),
    EMPTY_OUTPUT(
        true,
        "Provider trả nội dung rỗng hoặc bị chặn",
        "Kiểm tra prompt, bộ lọc an toàn và ngân sách output token.",
    ),
    PARSE_ERROR(
        true,
        "Không ghép được phản hồi theo định dạng yêu cầu",
        "Thử lại với prompt chuẩn hoặc giảm chunk.",
    ),
    CANCELLED(
        false,
        "Tác vụ đã bị hủy",
        "Khởi chạy lại tác vụ nếu vẫn muốn dịch.",
    ),
    SERVER(
        true,
        "Máy chủ provider đang lỗi",
        "Chờ rồi thử lại hoặc chuyển provider/model dự phòng.",
    ),
    UNKNOWN(
        false,
        "Lỗi provider chưa được phân loại",
        "Mở nhật ký tác vụ để kiểm tra chi tiết kỹ thuật.",
    ),
}

@Keep
data class AiProviderFailure(
    val kind: AiFailureKind,
    val provider: String,
    val model: String,
    val attempt: Int,
    val statusCode: Int? = null,
    val retryable: Boolean = kind.retryable,
    val technicalDetail: String = "",
    val retryAfterMillis: Long? = null,
    val routeName: String = "",
    val targetSummary: String = "",
) {
    val userMessage: String
        get() = buildString {
            if (kind == AiFailureKind.ROUTE_UNAVAILABLE) {
                append(routeName.ifBlank { "AI Router" })
                append(": ").append(kind.vietnameseLabel)
                retryAfterMillis
                    ?.takeIf { it > 0 }
                    ?.let { append(". Thử lại sau ").append(formatRetryAfter(it)) }
                targetSummary.takeIf(String::isNotBlank)?.let {
                    append(". Trạng thái: ").append(it)
                }
                append(". ").append(kind.vietnameseAction)
                return@buildString
            }
            append(provider.ifBlank { "AI Provider" })
            append(" · ").append(model.ifBlank { "model chưa cấu hình" })
            append(" · lần thử ").append(attempt.coerceAtLeast(1))
            append(": ").append(kind.vietnameseLabel)
            statusCode?.let { append(" (HTTP ").append(it).append(')') }
            append(". ").append(kind.vietnameseAction)
        }
}

class AiProviderException(
    val failure: AiProviderFailure,
    cause: Throwable? = null,
) : Exception(failure.userMessage, cause)

/** Preserves the number of real HTTP/model attempts made inside a protocol handler. */
class AiRequestAttemptsException(
    val attempts: Int,
    val lastFailure: Throwable,
) : Exception(lastFailure.message, lastFailure)

class AiRouteUnavailableException(
    val taskType: String,
    val routeName: String,
    val retryAfterMillis: Long? = null,
    val targetSummary: String = "",
) : IllegalStateException(
    buildString {
        append("No healthy AI route target for ").append(taskType)
        routeName.takeIf(String::isNotBlank)?.let { append(" in ").append(it) }
        retryAfterMillis?.let { append("; retry after ").append(it).append(" ms") }
        targetSummary.takeIf(String::isNotBlank)?.let { append("; ").append(it) }
    }
)

object AiProviderFailureClassifier {

    private val httpStatusPattern = Regex("(?:HTTP\\s+|^)([1-5]\\d{2})(?=\\D|$)", RegexOption.IGNORE_CASE)
    private val quotaTerms = listOf(
        "quota",
        "insufficient_quota",
        "resource_exhausted",
        "billing",
        "credit balance",
        "usage limit",
    )
    private val rateLimitTerms = listOf(
        "model_at_capacity",
        "selected model is at capacity",
        "model is at capacity",
        "rate limit",
        "rate_limit",
        "too many requests",
    )

    fun classify(
        error: Throwable,
        provider: String,
        model: String,
        attemptOffset: Int = 0,
    ): AiProviderException {
        if (error is AiProviderException && attemptOffset == 0) return error
        val attempts = when (error) {
            is AiProviderException -> error.failure.attempt
            is AiRequestAttemptsException -> error.attempts
            else -> 1
        }
        val root = unwrap(error)
        val chain = generateSequence(root as Throwable?) { it.cause }
            .take(8)
            .toList()
        val detailMessages = chain.asSequence()
            .mapNotNull(Throwable::message)
            .filter(String::isNotBlank)
            .distinct()
            .toList()
        val detail = detailMessages.joinToString(" | ")
        val normalized = detail.lowercase()
        val routeUnavailable = chain.filterIsInstance<AiRouteUnavailableException>().firstOrNull()
        val statusCode = chain.asSequence()
            .mapNotNull { throwable -> httpStatusPattern.find(throwable.message.orEmpty()) }
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .firstOrNull()
        val kind = when {
            chain.any { it is CancellationException } -> AiFailureKind.CANCELLED
            routeUnavailable != null -> AiFailureKind.ROUTE_UNAVAILABLE
            normalized.containsAny(quotaTerms) || statusCode == 402 -> AiFailureKind.QUOTA
            statusCode == 401 || statusCode == 403 || normalized.containsAny(
                listOf(
                    "unauthorized",
                    "authentication",
                    "invalid api key",
                    "api key not valid",
                    "api_key_invalid",
                    "invalid api_key",
                    "permission denied",
                    "invalid_grant",
                    "access token",
                    "refresh token",
                    "đăng nhập lại",
                )
            ) -> AiFailureKind.AUTHENTICATION
            statusCode == 429 || normalized.containsAny(rateLimitTerms) -> AiFailureKind.RATE_LIMIT
            chain.any { it is SocketTimeoutException } ||
                statusCode == 408 || normalized.containsAny(listOf("timeout", "timed out")) -> {
                AiFailureKind.TIMEOUT
            }
            normalized.containsAny(
                listOf(
                    "configuration incomplete",
                    "configuration is incomplete",
                    "config incomplete",
                    "no ai translation preset",
                    "unsupported ai protocol",
                    "baseurl",
                    "api key and model are required",
                    "api key, and model are required",
                    "model is required",
                    "model required",
                    "ai route not found",
                    "ai route is disabled",
                    "ai route does not match task",
                    "no resolvable model or credential",
                    "ai route has no resolvable model or credential",
                )
            ) -> AiFailureKind.CONFIGURATION
            normalized.containsAny(
                listOf("empty ai response", "empty gemini response", "empty translation result", "blocked")
            ) -> AiFailureKind.EMPTY_OUTPUT
            normalized.containsAny(
                listOf(
                    "changed paragraph count",
                    "changed source language",
                    "paragraph markers",
                    "invalid format",
                    "parse error",
                    "malformed json",
                    "json syntax",
                )
            ) -> AiFailureKind.PARSE_ERROR
            statusCode in setOf(400, 404, 405, 406, 409, 415, 422) || normalized.containsAny(
                listOf(
                    "invalid response",
                    "protocol error",
                    "unsupported response",
                    "unexpected response schema",
                    "model not found",
                    "model is not found",
                    "model was not found",
                    "model_not_found",
                    "unknown model",
                    "invalid model",
                    "invalid model id",
                    "model id is invalid",
                    "model does not exist",
                    "model is unavailable",
                    "model unavailable",
                    "model is not available",
                    "model not available",
                    "model does not support",
                    "model doesn't support",
                    "not supported by this model",
                    "is not supported by this model",
                    "not supported in this model",
                    "not found for api version",
                    "not found or is not supported",
                    "generatecontent is not supported",
                    "not supported for generatecontent",
                    "call listmodels",
                    "listmodels to see available models",
                    "supportedgenerationmethods",
                    "thinking_config",
                    "thinking config",
                    "thinking is not supported",
                    "thinking level is not supported",
                    "unsupported thinking",
                )
            ) -> AiFailureKind.PROTOCOL
            statusCode != null && statusCode in 500..599 -> AiFailureKind.SERVER
            normalized.containsAny(
                listOf(
                    "server_is_overloaded",
                    "service_unavailable_error",
                    "server is overloaded",
                    "service unavailable",
                )
            ) -> AiFailureKind.SERVER
            chain.any { it is IOException } -> AiFailureKind.NETWORK
            else -> AiFailureKind.UNKNOWN
        }
        val sanitizedDetail = detail.redactSecrets()
        val failure = AiProviderFailure(
            kind = kind,
            provider = provider,
            model = model,
            attempt = attemptOffset + attempts,
            statusCode = statusCode,
            technicalDetail = sanitizedDetail.take(MAX_TECHNICAL_DETAIL_LENGTH),
            retryAfterMillis = routeUnavailable?.retryAfterMillis,
            routeName = routeUnavailable?.routeName.orEmpty(),
            targetSummary = routeUnavailable?.targetSummary.orEmpty(),
        )
        return AiProviderException(failure, root)
    }

    private fun unwrap(error: Throwable): Throwable {
        var current = error
        repeat(8) {
            current = when (current) {
                is AiRequestAttemptsException -> current.lastFailure
                is AiProviderException -> current.cause ?: return current
                else -> return current
            }
        }
        return current
    }

    private fun String.containsAny(values: List<String>): Boolean = values.any(::contains)

    private fun String.redactSecrets(): String {
        return this
            .replace(Regex("(key|api_key|token|access_token)=([^&\\s]+)", RegexOption.IGNORE_CASE), "$1=[REDACTED]")
            .replace(Regex("(Bearer|Token)\\s+[A-Za-z0-9._~+/-]+=*", RegexOption.IGNORE_CASE), "$1 [REDACTED]")
            .replace(Regex("sk-[A-Za-z0-9]{20,}"), "[REDACTED_KEY]")
    }

    private const val MAX_TECHNICAL_DETAIL_LENGTH = 500
}

private fun formatRetryAfter(durationMillis: Long): String {
    val seconds = ((durationMillis + 999L) / 1_000L).coerceAtLeast(1L)
    return if (seconds < 60L) {
        "$seconds giây"
    } else {
        "${(seconds + 59L) / 60L} phút"
    }
}
