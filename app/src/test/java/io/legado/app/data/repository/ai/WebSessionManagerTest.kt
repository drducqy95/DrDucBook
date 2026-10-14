package io.legado.app.data.repository.ai

import com.google.gson.JsonObject
import io.legado.app.utils.GSON
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebSessionManagerTest {

    @Before
    fun setup() {
        GeminiWebSessionManager.invalidateSession()
        ChatGptWebSessionManager.invalidateSession()
    }

    @Test
    fun geminiWebSessionManagerParsesJsonCredential() = runBlocking {
        val jsonPayload = JsonObject().apply {
            addProperty("cookie", "__Secure-1PSID=test_psid_123; __Secure-1PSIDTS=test_ts_456")
            addProperty("snlm0e", "test_snlm0e_token_xyz")
            addProperty("bl", "boq_assistant-bard-web-server_20260904.01_p0")
        }.toString()

        val session = GeminiWebSessionManager.getSession(jsonPayload)

        assertEquals("test_snlm0e_token_xyz", session.snlm0e)
        assertEquals("boq_assistant-bard-web-server_20260904.01_p0", session.buildLabel)
        assertEquals("__Secure-1PSID=test_psid_123; __Secure-1PSIDTS=test_ts_456", session.cookieHeader)
        assertEquals("test_psid_123", session.cookies["__Secure-1PSID"])
        assertEquals("test_ts_456", session.cookies["__Secure-1PSIDTS"])
    }

    @Test
    fun geminiWebSessionManagerParsesWizGlobalDataFormat() = runBlocking {
        GeminiWebSessionManager.invalidateSession()
        // WIZ_global_data case variations: "SNlM0e", "cfb2h"
        val jsonPayload = JsonObject().apply {
            addProperty("cookie", "__Secure-1PSID=psid_999; __Secure-1PSIDTS=ts_888")
            addProperty("SNlM0e", "wiz_snlm0e_abc")
            addProperty("cfb2h", "boq_assistant-bard-web-server_20260905.02_p0")
        }.toString()

        val session = GeminiWebSessionManager.getSession(jsonPayload)

        assertEquals("wiz_snlm0e_abc", session.snlm0e)
        assertEquals("boq_assistant-bard-web-server_20260905.02_p0", session.buildLabel)
        assertEquals("psid_999", session.cookies["__Secure-1PSID"])
    }

    @Test
    fun chatGptWebSessionManagerResolvesDirectJwt() = runBlocking {
        val jwtToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

        val resolved = ChatGptWebSessionManager.resolveAccessToken(jwtToken)

        assertEquals(jwtToken, resolved)
    }

    @Test
    fun chatGptWebSessionManagerResolvesJsonAccessToken() = runBlocking {
        val jwtToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        val jsonPayload = JsonObject().apply {
            addProperty("accessToken", jwtToken)
            addProperty("email", "user@example.com")
        }.toString()

        val resolved = ChatGptWebSessionManager.resolveAccessToken(jsonPayload)

        assertEquals(jwtToken, resolved)
    }

    @Test
    fun streamingDeltaAccumulatorEliminatesDuplicateText() {
        val cumulativeChunks = listOf(
            "Xin",
            "Xin chào",
            "Xin chào bạn",
            "Xin chào bạn, tôi là",
            "Xin chào bạn, tôi là Gemini",
        )

        var lastEmittedText = ""
        val deltas = mutableListOf<String>()

        for (extractedText in cumulativeChunks) {
            if (extractedText.startsWith(lastEmittedText)) {
                val delta = extractedText.substring(lastEmittedText.length)
                if (delta.isNotEmpty()) {
                    lastEmittedText = extractedText
                    deltas.add(delta)
                }
            } else if (extractedText != lastEmittedText) {
                lastEmittedText = extractedText
                deltas.add(extractedText)
            }
        }

        val fullReconstructed = deltas.joinToString("")
        assertEquals("Xin chào bạn, tôi là Gemini", fullReconstructed)
        assertEquals(listOf("Xin", " chào", " bạn", ", tôi là", " Gemini"), deltas)
    }

    @Test
    fun chatGptWebSentinelGeneratesValidTokensAndSolvesPow() {
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
        val config = ChatGptWebSentinel.getConfig(userAgent)
        assertEquals(18, config.size)

        val reqToken = ChatGptWebSentinel.getRequirementsToken(userAgent)
        assertTrue("Requirements token must start with gAAAAAC", reqToken.startsWith("gAAAAAC"))
        assertTrue("Requirements token must be at least 50 chars", reqToken.length > 50)

        // Test solving easy PoW (same as requirements token target diff 0fffff)
        val (answer, solved) = ChatGptWebSentinel.generateAnswer("0.123456789", "0fffff", config)
        assertTrue("Easy PoW challenge must be solved", solved)
        assertTrue("Answer must not be blank", answer.isNotBlank())
    }

    @Test
    fun sha3512MatchesKnownTestVector() {
        // Known NIST vector: SHA3-512("")
        val expectedEmptyHashHex = "a69f73cca23a9ac5c8b567dc185a756e97c982164fe25859e0d1dcc1475c80a615b2123af1f5f94c11e3e9402c3ac558f500199d95b6d3e301758586281dcd26"
        val actualEmptyHash = ChatGptWebSentinel.sha3_512(ByteArray(0))
        val actualEmptyHex = actualEmptyHash.joinToString("") { "%02x".format(it) }
        assertEquals(expectedEmptyHashHex, actualEmptyHex)

        val pureKeccakEmptyHash = PureKeccak.sha3_512(ByteArray(0))
        val pureKeccakHex = pureKeccakEmptyHash.joinToString("") { "%02x".format(it) }
        assertEquals(expectedEmptyHashHex, pureKeccakHex)
    }

    @Test
    fun geminiWebHandlerExtractsTextFromRealGoogleResponseChunk() {
        val handler = GeminiWebHandler()
        val chunkLine = """[["wrb.fr",null,"[null,[\"c_66fc1629be0bc83a\",\"r_a9283965aa789ef0\"],null,null,[[\"rc_c1300196cbdf6021\",[\"Xin chào! Rất vui được hỗ trợ bạn.\"],null,null,null,null,null,null,[1],\"vi\"]]]"]]"""
        val extracted = handler.extractTextFromChunk(chunkLine)
        assertEquals("Xin chào! Rất vui được hỗ trợ bạn.", extracted)
    }

    @Test
    fun geminiWebHandlerBuilds80ElementPayload() {
        val handler = GeminiWebHandler()
        val payloadJson = handler.buildFReqPayload("Xin chào", "gemini-web-thinking")
        assertTrue("Payload must contain valid outer wrapper", payloadJson.startsWith("[null,"))
        assertTrue("Payload must contain prompt", payloadJson.contains("Xin chào"))
        assertTrue("Payload must set deep thinking mode", payloadJson.contains("[[0]]"))
    }

    @Test
    fun geminiWebHandlerGeneratesValidSapisidHash() {
        val handler = GeminiWebHandler()
        val sapisidHash = handler.generateSapisidHash("test_sapisid_123")
        assertTrue("Hash must start with SAPISIDHASH", sapisidHash.startsWith("SAPISIDHASH "))
        val parts = sapisidHash.substring("SAPISIDHASH ".length).split("_")
        assertEquals(2, parts.size)
        assertEquals(40, parts[1].length)
    }

    @Test
    fun geminiWebSessionManagerIsolatesMultipleAccountsInPool() = runBlocking {
        val credA = JsonObject().apply {
            addProperty("cookie", "__Secure-1PSID=account_A; __Secure-1PSIDTS=ts_A")
            addProperty("snlm0e", "snlm0e_A")
            addProperty("bl", "bl_A")
        }.toString()

        val credB = JsonObject().apply {
            addProperty("cookie", "__Secure-1PSID=account_B; __Secure-1PSIDTS=ts_B")
            addProperty("snlm0e", "snlm0e_B")
            addProperty("bl", "bl_B")
        }.toString()

        val sessionA = GeminiWebSessionManager.getSession(credA)
        val sessionB = GeminiWebSessionManager.getSession(credB)

        assertEquals("snlm0e_A", sessionA.snlm0e)
        assertEquals("snlm0e_B", sessionB.snlm0e)
        assertEquals("account_A", sessionA.cookies["__Secure-1PSID"])
        assertEquals("account_B", sessionB.cookies["__Secure-1PSID"])

        // Invalidate only account A
        GeminiWebSessionManager.invalidateSession(credA)

        // Account B should still be intact in cache
        val sessionBStillValid = GeminiWebSessionManager.getSession(credB)
        assertEquals("snlm0e_B", sessionBStillValid.snlm0e)
        assertEquals(sessionB.fetchedAt, sessionBStillValid.fetchedAt)

        // Full invalidate clears all
        GeminiWebSessionManager.invalidateSession(null)
    }

    @Test
    fun chatGptWebSessionManagerIsolatesMultipleAccountsInPool() = runBlocking {
        val jwtA = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhY2NvdW50X0EiLCJleHAiOjE5OTk5OTk5OTl9.signatureA"
        val jwtB = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhY2NvdW50X0IiLCJleHAiOjE5OTk5OTk5OTl9.signatureB"

        val credA = JsonObject().apply { addProperty("accessToken", jwtA) }.toString()
        val credB = JsonObject().apply { addProperty("accessToken", jwtB) }.toString()

        val tokenA = ChatGptWebSessionManager.resolveAccessToken(credA)
        val tokenB = ChatGptWebSessionManager.resolveAccessToken(credB)

        assertEquals(jwtA, tokenA)
        assertEquals(jwtB, tokenB)

        // Invalidate only account A
        ChatGptWebSessionManager.invalidateSession(credA)

        // Account B should still resolve B
        val tokenBAfter = ChatGptWebSessionManager.resolveAccessToken(credB)
        assertEquals(jwtB, tokenBAfter)
    }

    @Test
    fun geminiWebHandlerCheckRpcErrorInvalidatesTargetCredential() = runBlocking {
        val handler = GeminiWebHandler()
        val credA = JsonObject().apply {
            addProperty("cookie", "__Secure-1PSID=acc_A; __Secure-1PSIDTS=ts_A")
            addProperty("snlm0e", "snlm0e_A")
        }.toString()
        val credB = JsonObject().apply {
            addProperty("cookie", "__Secure-1PSID=acc_B; __Secure-1PSIDTS=ts_B")
            addProperty("snlm0e", "snlm0e_B")
        }.toString()

        GeminiWebSessionManager.getSession(credA)
        val sessionB = GeminiWebSessionManager.getSession(credB)

        // Trigger RPC error with credA
        try {
            handler.checkRpcError("""[["er", null, "auth error"]]""", credentialKey = credA)
        } catch (_: Exception) {}

        // Verify account B session remains cached and unchanged
        val sessionBRetrieved = GeminiWebSessionManager.getSession(credB)
        assertEquals("snlm0e_B", sessionBRetrieved.snlm0e)
        assertEquals(sessionB.fetchedAt, sessionBRetrieved.fetchedAt)
    }

    @Test
    fun geminiWebHandlerIncludes4ActualModelsAndThinkingLevels() = runBlocking {
        val handler = GeminiWebHandler()
        val dummyProvider = io.legado.app.domain.model.AiProviderConfig(
            id = "test_gemini_web",
            name = "Gemini Web",
            protocol = io.legado.app.domain.model.AiProtocol.GEMINI_WEB,
            baseUrl = "https://gemini.google.com",
            apiKey = "",
        )
        val models = handler.fetchModels(dummyProvider).getOrThrow()
        val flashLite = models.first { it.id == "gemini-web-3.5-flash-lite" }
        assertEquals(1_000_000, flashLite.contextWindow)
        assertEquals(16_384, flashLite.maxOutputTokens)

        val flash38 = models.first { it.id == "gemini-web-3.8-flash" }
        assertEquals(1_000_000, flash38.contextWindow)

        val pro31 = models.first { it.id == "gemini-web-3.1-pro" }
        assertEquals(1_000_000, pro31.contextWindow)

        val argon4 = models.first { it.id == "gemini-web-4-argon" }
        assertEquals(1_000_000, argon4.contextWindow)

        // Test model spec mapping
        assertEquals(Pair(6, 4), handler.resolveGeminiWebModelSpec("gemini-web-3.5-flash-lite"))
        assertEquals(Pair(1, 4), handler.resolveGeminiWebModelSpec("gemini-web-3.8-flash"))
        assertEquals(Pair(3, 4), handler.resolveGeminiWebModelSpec("gemini-web-3.1-pro"))
        assertEquals(Pair(2, 0), handler.resolveGeminiWebModelSpec("gemini-web-4-argon"))
        assertEquals(Pair(2, 1), handler.resolveGeminiWebModelSpec("gemini-web-4-argon-low"))
        assertEquals(Pair(2, 2), handler.resolveGeminiWebModelSpec("gemini-web-4-argon-medium"))
        assertEquals(Pair(2, 3), handler.resolveGeminiWebModelSpec("gemini-web-4-argon-high"))

        // Legacy aliases
        assertEquals(Pair(6, 4), handler.resolveGeminiWebModelSpec("gemini-web-flash-lite"))
        assertEquals(Pair(1, 4), handler.resolveGeminiWebModelSpec("gemini-web-default"))
        assertEquals(Pair(3, 4), handler.resolveGeminiWebModelSpec("gemini-web-pro"))
        assertEquals(Pair(2, 0), handler.resolveGeminiWebModelSpec("gemini-web-thinking"))

        // Test payload structure
        val payload = handler.buildFReqPayload("test prompt", "gemini-web-3.5-flash-lite")
        val parsedOuter = GSON.fromJson(payload, com.google.gson.JsonArray::class.java)
        val innerString = parsedOuter[1].asString
        val parsedInner = GSON.fromJson(innerString, com.google.gson.JsonArray::class.java)
        // Code 6 is FLASH_LITE at index 79
        assertEquals(6, parsedInner[79].asInt)
        // thinkMode is 4 at index 17
        assertEquals(4, parsedInner[17].asJsonArray[0].asJsonArray[0].asInt)

        // Test Argon High payload
        val payloadArgonHigh = handler.buildFReqPayload("test prompt", "gemini-web-4-argon-high")
        val parsedOuterArgon = GSON.fromJson(payloadArgonHigh, com.google.gson.JsonArray::class.java)
        val parsedInnerArgon = GSON.fromJson(parsedOuterArgon[1].asString, com.google.gson.JsonArray::class.java)
        assertEquals(2, parsedInnerArgon[79].asInt)
        assertEquals(3, parsedInnerArgon[17].asJsonArray[0].asJsonArray[0].asInt)
    }

    @Test
    fun geminiWebHandlerBuilds81ElementPayloadWithMultiTurnAndJspb() {
        val handler = GeminiWebHandler()

        // 1. JSPB headers verification
        val jspbHeaders = handler.buildJspbHeaders(modelNumber = 1, generationId = "GEN-UUID-TEST")
        assertEquals("[0]", jspbHeaders["x-goog-ext-73010989-jspb"])
        assertEquals("[0,0,0]", jspbHeaders["x-goog-ext-73010990-jspb"])
        assertTrue("Must contain model JSPB header", jspbHeaders.containsKey("x-goog-ext-525001261-jspb"))
        assertTrue("Must contain session JSPB header", jspbHeaders.containsKey("x-goog-ext-525005358-jspb"))
        assertTrue("Model header must contain generation ID", jspbHeaders["x-goog-ext-525001261-jspb"]!!.contains("GEN-UUID-TEST"))

        // 2. Multi-turn payload verification
        val conversationMeta = GeminiWebConversationMetadata(
            cid = "c_mock_conversation_id",
            rid = "r_mock_response_id",
            rcid = "rc_mock_candidate_id",
            model = "gemini-web-3.8-flash",
            turnCount = 3,
        )
        val payload = handler.buildFReqPayload(
            prompt = "Dịch tiếp chương 2",
            modelId = "gemini-web-3.8-flash",
            conversationMeta = conversationMeta,
        )

        val outer = GSON.fromJson(payload, com.google.gson.JsonArray::class.java)
        val inner = GSON.fromJson(outer[1].asString, com.google.gson.JsonArray::class.java)

        // Must have exactly 81 elements (index 0 to 80)
        assertEquals(81, inner.size())

        // Index 2 must contain [cid, rid, rcid]
        val contextArray = inner[2].asJsonArray
        assertEquals("c_mock_conversation_id", contextArray[0].asString)
        assertEquals("r_mock_response_id", contextArray[1].asString)
        assertEquals("rc_mock_candidate_id", contextArray[2].asString)

        // Index 45 must be 1 (isTemporary flag to avoid chat history clutter)
        assertEquals(1, inner[45].asInt)

        // Index 80 must be 1 (81st element)
        assertEquals(1, inner[80].asInt)

        // Index 6 and 41 must be [1]
        assertEquals(1, inner[6].asJsonArray[0].asInt)
        assertEquals(1, inner[41].asJsonArray[0].asInt)

        // 3. File Attachment verification
        val filePayload = handler.buildFReqPayload(
            prompt = "OCR bức ảnh này",
            modelId = "gemini-web-3.8-flash",
            fileAttachment = Pair("w_test_file_id_123", "manga_p1.jpg")
        )
        val outerFile = GSON.fromJson(filePayload, com.google.gson.JsonArray::class.java)
        val innerFile = GSON.fromJson(outerFile[1].asString, com.google.gson.JsonArray::class.java)
        val fileArray = innerFile[0].asJsonArray[3].asJsonArray
        val fileEntry = fileArray[0].asJsonArray
        assertEquals("w_test_file_id_123", fileEntry[0].asJsonArray[0].asString)
        assertEquals("manga_p1.jpg", fileEntry[1].asString)
    }

    @Test
    fun geminiWebConversationStoreTracksPerBookMetadataAndEvicts() {
        GeminiWebConversationStore.clearAll()

        val bookKey = "https://example.com/novel/123"
        val meta1 = GeminiWebConversationMetadata(
            cid = "cid_book1",
            rid = "rid_book1",
            rcid = "rcid_book1",
            model = "gemini-web-3.8-flash",
            turnCount = 1,
        )

        GeminiWebConversationStore.put(bookKey, meta1)

        // Same book, same model -> should retrieve conversation
        val retrieved = GeminiWebConversationStore.get(bookKey, "gemini-web-3.8-flash")
        assertNotNull(retrieved)
        assertEquals("cid_book1", retrieved?.cid)
        assertEquals(1, retrieved?.turnCount)

        // Same book, different model -> should reset conversation for clean context
        val modelMismatch = GeminiWebConversationStore.get(bookKey, "gemini-web-4-argon")
        org.junit.Assert.assertNull("Model mismatch must reset conversation", modelMismatch)

        // Turn limit enforcement: turnCount >= 30 should evict to prevent token overflow
        val metaAtMaxTurns = GeminiWebConversationMetadata(
            cid = "cid_max_turns",
            rid = "rid_max_turns",
            rcid = "rcid_max_turns",
            model = "gemini-web-3.8-flash",
            turnCount = 30,
        )
        GeminiWebConversationStore.put(bookKey, metaAtMaxTurns)
        val maxTurnResult = GeminiWebConversationStore.get(bookKey, "gemini-web-3.8-flash")
        org.junit.Assert.assertNull("Turns >= 30 must evict conversation", maxTurnResult)

        // Removal
        GeminiWebConversationStore.put(bookKey, meta1)
        GeminiWebConversationStore.remove(bookKey)
        org.junit.Assert.assertNull(GeminiWebConversationStore.get(bookKey, "gemini-web-3.8-flash"))
    }

    @Test
    fun geminiWebSessionManagerResolvesAuthUserAndRewritesUrls() {
        // Default authUser = ""
        assertEquals("", GeminiWebSessionManager.resolveAuthUser(null))

        // Explicit in JSON
        val jsonCred = JsonObject().apply {
            addProperty("cookie", "__Secure-1PSID=test")
            addProperty("authUser", 2)
        }.toString()
        assertEquals("2", GeminiWebSessionManager.resolveAuthUser(jsonCred))

        // From URL pattern in credential string
        val urlCred = "__Secure-1PSID=test; https://gemini.google.com/u/1/app"
        assertEquals("1", GeminiWebSessionManager.resolveAuthUser(urlCred))

        // URL slot rewriting for non-zero authUser
        val baseAppUrl = "https://gemini.google.com/app"
        assertEquals("https://gemini.google.com/app", GeminiWebSessionManager.getGeminiUrl(baseAppUrl, ""))
        assertEquals("https://gemini.google.com/app", GeminiWebSessionManager.getGeminiUrl(baseAppUrl, "0"))
        assertEquals("https://gemini.google.com/u/1/app", GeminiWebSessionManager.getGeminiUrl(baseAppUrl, "1"))
        assertEquals("https://gemini.google.com/u/2/app", GeminiWebSessionManager.getGeminiUrl(baseAppUrl, "2"))

        val rpcUrl = "https://gemini.google.com/_/BardChatUi/data/assistant.lamda.BardFrontendService/StreamGenerate"
        assertEquals(
            "https://gemini.google.com/u/2/_/BardChatUi/data/assistant.lamda.BardFrontendService/StreamGenerate",
            GeminiWebSessionManager.getGeminiUrl(rpcUrl, "2")
        )
    }
}

