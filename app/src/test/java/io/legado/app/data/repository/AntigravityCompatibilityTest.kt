package io.legado.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AntigravityCompatibilityTest {

    @Test
    fun codeAssistHeadersMatchOfficialAntigravityClient() {
        val headers = antigravityCodeAssistHeaders("access-token")

        assertEquals("Bearer access-token", headers["Authorization"])
        assertEquals(ANTIGRAVITY_IDE_USER_AGENT, headers["User-Agent"])
        assertEquals("local", headers["x-request-source"])
        assertFalse(headers.containsKey("X-Goog-Api-Client"))
        assertFalse(headers.containsKey("Client-Metadata"))
    }

    @Test
    fun oauthFlowMatches9RouterWithoutPkce() {
        assertFalse(ANTIGRAVITY_OAUTH_USES_PKCE)
        assertEquals(4, antigravityClientMetadata(isArm64 = true)["platform"])
        assertEquals(3, antigravityClientMetadata(isArm64 = false)["platform"])
    }

    @Test
    fun fallbackProjectIdUsesCloudProjectShape() {
        val projectId = generateAntigravityProjectId("abc12")

        assertEquals("useful-fuze-abc12", projectId)
        assertTrue(projectId.length <= 30)
    }
}
