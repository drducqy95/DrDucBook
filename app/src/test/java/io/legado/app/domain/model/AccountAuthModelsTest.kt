package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountAuthModelsTest {

    @Test
    fun emailCredentialsNormalizeEmailAndRequireStrongEnoughPassword() {
        val credentials = AccountEmailCredentials("  USER@Example.COM  ", "password-1")

        credentials.requireValid()

        assertEquals("user@example.com", credentials.normalizedEmail)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emailCredentialsRejectShortPassword() {
        AccountEmailCredentials("user@example.com", "short").requireValid()
    }

    @Test
    fun googleCredentialDoesNotExposeTokenInToString() {
        val credential = AccountGoogleIdCredential("id-token-secret", "nonce-secret")

        credential.requireValid()

        assertFalse(credential.toString().contains("id-token-secret"))
        assertFalse(credential.toString().contains("nonce-secret"))
        assertTrue(credential.toString().contains("<redacted>"))
    }
}
