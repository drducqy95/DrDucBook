package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException

class AiProviderFailureTest {

    @Test
    fun classifiesEveryRequiredProviderFailureLane() {
        val cases = listOf(
            Exception("OpenAI-compatible configuration incomplete") to AiFailureKind.CONFIGURATION,
            Exception("No healthy AI route target for translate_chapter") to AiFailureKind.UNKNOWN,
            Exception("HTTP 401: Unauthorized") to AiFailureKind.AUTHENTICATION,
            Exception("API key not valid. Please pass a valid API key.") to AiFailureKind.AUTHENTICATION,
            Exception("HTTP 429: too many requests") to AiFailureKind.RATE_LIMIT,
            Exception("Selected model is at capacity. Please try a different model.") to AiFailureKind.RATE_LIMIT,
            Exception("HTTP 429: RESOURCE_EXHAUSTED quota") to AiFailureKind.QUOTA,
            SocketTimeoutException("read timed out") to AiFailureKind.TIMEOUT,
            IOException("Unable to resolve host") to AiFailureKind.NETWORK,
            Exception("HTTP 400: invalid request") to AiFailureKind.PROTOCOL,
            Exception("Model not found or is not supported for generateContent") to AiFailureKind.PROTOCOL,
            Exception("models/gemini-x is not found for API version v1beta, or is not supported for generateContent. Call ListModels to see available models and their supported methods.") to AiFailureKind.PROTOCOL,
            Exception("Invalid JSON payload received. Unknown name \"thinking_config\" at 'generation_config'") to AiFailureKind.PROTOCOL,
            Exception("Empty Gemini response") to AiFailureKind.EMPTY_OUTPUT,
            Exception("Translation changed paragraph count") to AiFailureKind.PARSE_ERROR,
            CancellationException("cancelled") to AiFailureKind.CANCELLED,
            Exception("HTTP 503: unavailable") to AiFailureKind.SERVER,
            Exception("server_is_overloaded") to AiFailureKind.SERVER,
            Exception("service_unavailable_error") to AiFailureKind.SERVER,
            Exception("unexpected failure") to AiFailureKind.UNKNOWN,
        )

        cases.forEach { (error, expected) ->
            assertEquals(
                error.message,
                expected,
                AiProviderFailureClassifier.classify(
                    error = error,
                    provider = "Gemini",
                    model = "gemini-3.1-flash-lite",
                ).failure.kind,
            )
        }
    }

    @Test
    fun preservesRealAttemptCountAndBuildsActionableUiMessage() {
        val error = AiRequestAttemptsException(
            attempts = 3,
            lastFailure = Exception("HTTP 429: too many requests"),
        )

        val failure = AiProviderFailureClassifier.classify(
            error = error,
            provider = "Gemini",
            model = "gemini-3.1-flash-lite",
            attemptOffset = 2,
        ).failure

        assertEquals(5, failure.attempt)
        assertEquals(429, failure.statusCode)
        assertTrue(failure.retryable)
        assertTrue(failure.userMessage.contains("Gemini"))
        assertTrue(failure.userMessage.contains("gemini-3.1-flash-lite"))
        assertTrue(failure.userMessage.contains("lần thử 5"))
        assertTrue(failure.userMessage.contains("HTTP 429"))
        assertFalse(failure.userMessage.contains("Translation failed"))
    }

    @Test
    fun routeUnavailablePreservesComboNameAndRetryDelay() {
        val failure = AiProviderFailureClassifier.classify(
            error = AiRouteUnavailableException(
                taskType = AiTaskType.TRANSLATE_CHAPTER,
                routeName = "Dịch AI · Free fallback",
                retryAfterMillis = 90_000L,
                targetSummary = "OpenCode · DeepSeek=EMPTY_OUTPUT",
            ),
            provider = "OpenCode",
            model = "deepseek-v4-flash-free",
        ).failure

        assertEquals(AiFailureKind.ROUTE_UNAVAILABLE, failure.kind)
        assertEquals(90_000L, failure.retryAfterMillis)
        assertEquals("Dịch AI · Free fallback", failure.routeName)
        assertTrue(failure.userMessage.contains("Dịch AI · Free fallback"))
        assertTrue(failure.userMessage.contains("2 phút"))
        assertFalse(failure.userMessage.contains("Cấu hình AI chưa hợp lệ"))
    }

    @Test
    fun permanentConfigurationAndQuotaFailuresAreNotRetried() {
        listOf(
            Exception("AI model configuration is incomplete"),
            Exception("HTTP 429: insufficient_quota"),
            Exception("HTTP 404: endpoint not found"),
        ).forEach { error ->
            val failure = AiProviderFailureClassifier.classify(error, "P", "M").failure
            assertFalse("${failure.kind} must stop immediately", failure.retryable)
        }
        assertEquals(
            AiFailureKind.CONFIGURATION,
            AiProviderFailureClassifier.classify(
                Exception("AI model configuration is incomplete"),
                "P",
                "M",
            ).failure.kind,
        )
    }

    @Test
    fun classifiesNestedProviderDetailInsteadOfOnlyWrapper() {
        val error = IllegalStateException(
            "Provider request failed",
            Exception("Model is not available for generateContent"),
        )

        val failure = AiProviderFailureClassifier.classify(error, "Gemini", "gemini-x").failure

        assertEquals(AiFailureKind.PROTOCOL, failure.kind)
        assertTrue(failure.technicalDetail.contains("Provider request failed"))
        assertTrue(failure.technicalDetail.contains("Model is not available"))
    }
}
