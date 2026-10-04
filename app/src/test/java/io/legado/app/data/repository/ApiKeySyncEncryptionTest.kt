package io.legado.app.data.repository

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiKeySyncEncryptionTest {

    @Test
    fun roundTripEncryptDecrypt() {
        val masterSecret = "supabase-user-uuid-12345-access-token-hash"
        val originalText = "{\"version\":1,\"providers\":[{\"providerId\":\"gemini\",\"apiKey\":\"AIzaSy123\"}]}"
        val plaintext = originalText.toByteArray(Charsets.UTF_8)

        val encrypted = ApiKeySyncEncryption.encrypt(plaintext, masterSecret)
        val decrypted = ApiKeySyncEncryption.decrypt(encrypted, masterSecret)

        assertArrayEquals(plaintext, decrypted)
        assertEquals(originalText, decrypted.toString(Charsets.UTF_8))
    }

    @Test
    fun decryptWithWrongSecretFails() {
        val masterSecret = "secret-a"
        val wrongSecret = "secret-b"
        val plaintext = "test secret payload".toByteArray(Charsets.UTF_8)

        val encrypted = ApiKeySyncEncryption.encrypt(plaintext, masterSecret)
        assertThrows(Exception::class.java) {
            ApiKeySyncEncryption.decrypt(encrypted, wrongSecret)
        }
    }
}
