package io.legado.app.domain.webservice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebServiceModelsTest {

    @Test
    fun portsDefaultToDrDucBookPairAndSuggestFreePair() {
        assertEquals(WebServicePorts.DEFAULT_HTTP_PORT, WebServicePorts.normalizeHttpPort(80))
        assertEquals(WebServicePorts.DEFAULT_HTTP_PORT, WebServicePorts.normalizeHttpPort(65_535))
        assertEquals(WebServicePorts.DEFAULT_WEB_SOCKET_PORT, WebServicePorts.webSocketPortFor(1124))

        val blocked = setOf(1124, 1125)
        assertEquals(
            1126,
            WebServicePorts.suggestHttpPort(1124) { port -> port !in blocked },
        )
    }

    @Test
    fun pairingCodeExchangesOnceAndRedactsSecrets() {
        var timestamp = 1_000L
        val broker = WebServicePairingBroker(
            now = { timestamp },
            codeGenerator = { "123456" },
            tokenGenerator = { "session-secret" },
            challengeTtlMillis = 1_000L,
            sessionTtlMillis = 2_000L,
        )

        val challenge = broker.createChallenge()
        assertEquals("123456", challenge.code)
        assertFalse(challenge.toString().contains("123456"))

        val result = broker.exchange("123 456") as WebServicePairingExchangeResult.Success
        assertEquals("session-secret", result.session.token)
        assertFalse(result.session.toString().contains("session-secret"))
        assertEquals(
            result.session,
            broker.sessionFromAuthorization("Bearer session-secret"),
        )
        assertEquals(WebServicePairingExchangeResult.InvalidCode, broker.exchange("123456"))

        assertTrue(broker.revoke("Bearer session-secret"))
        assertNull(broker.sessionFromAuthorization("Bearer session-secret"))
    }

    @Test
    fun pairingChallengeAndSessionExpire() {
        var timestamp = 1_000L
        val broker = WebServicePairingBroker(
            now = { timestamp },
            codeGenerator = { "654321" },
            tokenGenerator = { "short-session" },
            challengeTtlMillis = 100L,
            sessionTtlMillis = 100L,
        )

        broker.createChallenge()
        timestamp = 1_101L
        assertEquals(WebServicePairingExchangeResult.Expired, broker.exchange("654321"))

        timestamp = 2_000L
        broker.createChallenge()
        val result = broker.exchange("654321") as WebServicePairingExchangeResult.Success
        assertEquals(result.session, broker.sessionFromAuthorization("Bearer short-session"))

        timestamp = 2_101L
        assertNull(broker.sessionFromAuthorization("Bearer short-session"))
    }

    @Test
    fun policyPatchRequiresMatchingEtagAndAdvancesRevision() {
        val current = WebServicePolicy(
            exportEnabled = false,
            autoTranslationEnabled = false,
            revision = 7L,
            updatedAt = 1_000L,
        )

        assertEquals(
            WebServicePolicyPatchResult.PreconditionRequired,
            WebServicePolicyRevision.applyPatch(
                current = current,
                request = WebServicePolicyPatchRequest(exportEnabled = true),
                ifMatch = null,
                now = 2_000L,
            ),
        )
        assertTrue(
            WebServicePolicyRevision.applyPatch(
                current = current,
                request = WebServicePolicyPatchRequest(exportEnabled = true),
                ifMatch = "\"web-policy-6\"",
                now = 2_000L,
            ) is WebServicePolicyPatchResult.Conflict
        )

        val result = WebServicePolicyRevision.applyPatch(
            current = current,
            request = WebServicePolicyPatchRequest(
                exportEnabled = true,
                autoTranslationEnabled = true,
            ),
            ifMatch = current.etag,
            now = 2_000L,
        ) as WebServicePolicyPatchResult.Success

        assertEquals(true, result.policy.exportEnabled)
        assertEquals(true, result.policy.autoTranslationEnabled)
        assertEquals(8L, result.policy.revision)
        assertEquals("\"web-policy-8\"", result.policy.etag)
    }

    @Test
    fun backgroundPolicySanitizesAssetIdAndDisplayOptions() {
        val assetId = "${"a".repeat(64)}.png"

        assertEquals(assetId, WebServiceBackgroundPolicy.normalizeAssetId(assetId.uppercase()))
        assertNull(WebServiceBackgroundPolicy.normalizeAssetId("../$assetId"))
        assertNull(WebServiceBackgroundPolicy.normalizeAssetId("${"b".repeat(64)}.jpg"))
        assertEquals("cover", WebServiceBackgroundPolicy.normalizeFit("tile"))
        assertEquals("contain", WebServiceBackgroundPolicy.normalizeFit(" contain "))
        assertEquals("left top", WebServiceBackgroundPolicy.normalizePosition(" LEFT   TOP "))
        assertEquals("center", WebServiceBackgroundPolicy.normalizePosition("50% 50%"))
        assertEquals(0.75f, WebServiceBackgroundPolicy.normalizeDim(9f), 0.001f)
        assertEquals(WebServiceBackgroundPolicy.DEFAULT_DIM, WebServiceBackgroundPolicy.normalizeDim(Float.NaN), 0.001f)
        assertEquals(24, WebServiceBackgroundPolicy.normalizeBlur(99))
    }

    @Test
    fun policyPatchUpdatesAndClearsBackgroundWithRevision() {
        val assetId = "${"c".repeat(64)}.png"
        val current = WebServicePolicy(
            revision = 3L,
            updatedAt = 1_000L,
        )

        val updated = WebServicePolicyRevision.applyPatch(
            current = current,
            request = WebServicePolicyPatchRequest(
                backgroundAssetId = assetId,
                backgroundFit = "contain",
                backgroundPosition = "right bottom",
                backgroundDim = 0.5f,
                backgroundBlur = 12,
            ),
            ifMatch = current.etag,
            now = 2_000L,
        ) as WebServicePolicyPatchResult.Success

        assertEquals(assetId, updated.policy.backgroundAssetId)
        assertEquals("contain", updated.policy.backgroundFit)
        assertEquals("right bottom", updated.policy.backgroundPosition)
        assertEquals(0.5f, updated.policy.backgroundDim, 0.001f)
        assertEquals(12, updated.policy.backgroundBlur)
        assertEquals(4L, updated.policy.revision)

        val cleared = WebServicePolicyRevision.applyPatch(
            current = updated.policy,
            request = WebServicePolicyPatchRequest(clearBackgroundAsset = true),
            ifMatch = updated.policy.etag,
            now = 3_000L,
        ) as WebServicePolicyPatchResult.Success

        assertNull(cleared.policy.backgroundAssetId)
        assertEquals(5L, cleared.policy.revision)
    }

    @Test
    fun exportRequestsNormalizeSourceTypeAndKeys() {
        assertEquals(
            WebServiceExportRequests.SOURCE_TYPE_BOOK,
            WebServiceExportRequests.normalizeSourceType(null),
        )
        assertEquals(
            WebServiceExportRequests.SOURCE_TYPE_BOOK,
            WebServiceExportRequests.normalizeSourceType("BookSource"),
        )
        assertEquals(
            WebServiceExportRequests.SOURCE_TYPE_RSS,
            WebServiceExportRequests.normalizeSourceType(" rss_source "),
        )
        assertEquals(
            setOf("a", "b"),
            WebServiceExportRequests.normalizedKeys(listOf(" a ", "", "b", "a")),
        )
        assertEquals(
            setOf(0, 2),
            WebServiceExportRequests.normalizedChapterIndices(listOf(-1, 0, 2, 2)),
        )
    }

    @Test
    fun translationJobProgressIsBoundedAndOptionalTextIsTrimmed() {
        assertEquals(0f, WebServiceTranslationJobs.progress(1, 0), 0.001f)
        assertEquals(0f, WebServiceTranslationJobs.progress(-1, 3), 0.001f)
        assertEquals(1f, WebServiceTranslationJobs.progress(9, 3), 0.001f)
        assertEquals(0.5f, WebServiceTranslationJobs.progress(1, 2), 0.001f)
        assertEquals("google", WebServiceTranslationJobs.normalizedOptionalText(" google "))
        assertNull(WebServiceTranslationJobs.normalizedOptionalText(" "))
        assertEquals(12, WebServiceTranslationJobs.normalizedChapterIndex(" 12 "))
        assertNull(WebServiceTranslationJobs.normalizedChapterIndex("-1"))
        assertNull(WebServiceTranslationJobs.normalizedChapterIndex("chapter"))
    }

    @Test
    fun webTranslationProviderListKeepsEveryBackendProviderAndLanguage() {
        val response = buildWebServiceTranslationProviderList(
            providerValues = listOf("google", "ml_kit", "quick_translator", "nmt", "app_ai"),
            providerDisplayNames = listOf("Google", "ML Kit", "Quick", "NMT", "AI"),
            defaultProvider = "app_ai",
            defaultTargetLanguage = "vi",
            targetLanguagesForProvider = { provider ->
                if (provider == "nmt" || provider == "quick_translator") listOf("vi")
                else listOf("vi", "en")
            },
        )

        assertEquals(5, response.providers.size)
        assertEquals("app_ai", response.defaultProvider)
        assertEquals(listOf("vi"), response.providers.first { it.id == "nmt" }.targetLanguages)
        assertTrue(response.providers.any { it.id == "google" })
    }

    @Test
    fun legacyWebServiceContractKeepsRouteAndReturnDataShape() {
        assertEquals(14, WebServiceLegacyContract.postRoutes.size)
        assertEquals(12, WebServiceLegacyContract.getRoutes.size)
        assertEquals(3, WebServiceLegacyContract.webSocketRoutes.size)
        assertEquals(
            listOf("isSuccess", "errorMsg", "data"),
            WebServiceLegacyContract.returnDataKeys,
        )

        val requiredHttpRoutes = setOf(
            WebServiceLegacyContract.Http.GET_BOOKSHELF,
            WebServiceLegacyContract.Http.GET_CHAPTER_LIST,
            WebServiceLegacyContract.Http.GET_BOOK_CONTENT,
            WebServiceLegacyContract.Http.GET_BOOK_SOURCES,
            WebServiceLegacyContract.Http.GET_RSS_SOURCES,
            WebServiceLegacyContract.Http.GET_REPLACE_RULES,
            WebServiceLegacyContract.Http.SAVE_BOOK_PROGRESS,
            WebServiceLegacyContract.Http.SAVE_BOOK_SOURCE,
            WebServiceLegacyContract.Http.SAVE_RSS_SOURCE,
            WebServiceLegacyContract.Http.TEST_REPLACE_RULE,
        )
        val allHttpRoutes = WebServiceLegacyContract.httpRoutes.map { it.path }.toSet()
        assertTrue(allHttpRoutes.containsAll(requiredHttpRoutes))
        assertEquals(
            setOf(
                WebServiceLegacyContract.WebSocket.BOOK_SOURCE_DEBUG,
                WebServiceLegacyContract.WebSocket.RSS_SOURCE_DEBUG,
                WebServiceLegacyContract.WebSocket.SEARCH_BOOK,
            ),
            WebServiceLegacyContract.webSocketRoutes.toSet(),
        )
        assertTrue(
            WebServiceLegacyContract.hasNoV2Collision(
                listOf(
                    "/api/v2/instance",
                    "/api/v2/policy",
                    "/api/v2/export/sources",
                    "/api/v2/translation/jobs",
                )
            )
        )
    }

    @Test
    fun originPolicyAllowsSameHttpAndHttpsOrigins() {
        assertTrue(WebServiceOriginPolicy.isSameOrigin(null, "192.168.1.2:1124"))
        assertTrue(
            WebServiceOriginPolicy.isSameOrigin(
                origin = "http://192.168.1.2:1124",
                hostHeader = "192.168.1.2:1124",
            )
        )
        assertFalse(
            WebServiceOriginPolicy.isSameOrigin(
                origin = "http://evil.example:1124",
                hostHeader = "192.168.1.2:1124",
            )
        )
        assertTrue(
            WebServiceOriginPolicy.isSameOrigin(
                origin = "https://reader.example.com",
                hostHeader = "reader.example.com",
            )
        )
        assertTrue(
            WebServiceOriginPolicy.isSameOrigin(
                origin = "https://reader.example.com",
                hostHeader = "127.0.0.1:1124",
                trustedExternalUrl = "https://reader.example.com",
            )
        )
        assertFalse(
            WebServiceOriginPolicy.isSameOrigin(
                origin = "https://evil.example.com",
                hostHeader = "127.0.0.1:1124",
                trustedExternalUrl = "https://reader.example.com",
            )
        )
    }

    @Test
    fun pairingIsRequiredOnlyForRequestsArrivingThroughCloudflare() {
        val publicUrl = "https://reader.example.com"

        assertFalse(
            WebServiceRequestPolicy.requiresPairing(
                tunnelRequiresPairing = true,
                origin = "http://127.0.0.1:1124",
                hostHeader = "127.0.0.1:1124",
                cloudflareRay = null,
                cloudflareConnectingIp = null,
                publicUrl = publicUrl,
            )
        )
        assertTrue(
            WebServiceRequestPolicy.requiresPairing(
                tunnelRequiresPairing = true,
                origin = null,
                hostHeader = "127.0.0.1:1124",
                cloudflareRay = "test-ray-SIN",
                cloudflareConnectingIp = "203.0.113.10",
                publicUrl = publicUrl,
            )
        )
        assertTrue(
            WebServiceRequestPolicy.requiresPairing(
                tunnelRequiresPairing = true,
                origin = publicUrl,
                hostHeader = "127.0.0.1:1124",
                cloudflareRay = null,
                cloudflareConnectingIp = null,
                publicUrl = publicUrl,
            )
        )
        assertFalse(
            WebServiceRequestPolicy.requiresPairing(
                tunnelRequiresPairing = false,
                origin = publicUrl,
                hostHeader = "reader.example.com",
                cloudflareRay = "test-ray-SIN",
                cloudflareConnectingIp = "203.0.113.10",
                publicUrl = publicUrl,
            )
        )
    }

    @Test
    fun addressPolicyPrefersLanAndDoesNotExposeMobilePublicAddress() {
        val ordered = WebServiceAddressPolicy.orderedHosts(
            listOf(
                WebServiceAddressCandidate("rmnet0", "214.59.102.248", isSiteLocal = false),
                WebServiceAddressCandidate("wlan0", "192.168.1.12", isSiteLocal = true),
                WebServiceAddressCandidate("tun0", "100.64.0.2", isSiteLocal = false),
            )
        )

        assertEquals(listOf("192.168.1.12", "100.64.0.2"), ordered)
    }
}
