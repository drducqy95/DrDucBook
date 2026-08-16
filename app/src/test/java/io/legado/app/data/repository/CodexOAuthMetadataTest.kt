package io.legado.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class CodexOAuthMetadataTest {

    @Test
    fun refreshUsesTheFormEncodingAndScopeExpectedByCodexOAuth() {
        assertEquals("openid profile email offline_access", CodexOAuthMetadata.OAUTH_SCOPE)
        assertEquals(false, CodexOAuthMetadata.REFRESH_USES_JSON)
    }

    @Test
    fun extractAccountIdPrefersChatGptClaim() {
        val token = unsignedJwt(
            """{"sub":"user-sub","email":"user@example.com","https://api.openai.com/auth.chatgpt_account_id":"acct-chatgpt"}"""
        )

        assertEquals("acct-chatgpt", CodexOAuthMetadata.extractAccountId(token))
        assertEquals("user-sub", CodexOAuthMetadata.extractSubject(token))
        assertEquals("user@example.com", CodexOAuthMetadata.extractEmail(token))
    }

    @Test
    fun emailIsNotAccountIdFallback() {
        val token = unsignedJwt("""{"email":"user@example.com"}""")

        assertNull(CodexOAuthMetadata.extractAccountId(token))
        assertNull(CodexOAuthMetadata.extractSubject(token))
        assertEquals("user@example.com", CodexOAuthMetadata.extractEmail(token))
    }

    private fun unsignedJwt(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
        val body = encoder.encodeToString(payload.toByteArray())
        return "$header.$body."
    }
}
