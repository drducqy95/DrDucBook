package io.legado.app.data.repository

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object ApiKeySyncEncryption {

    private const val PAYLOAD_VERSION: Byte = 1
    private const val SALT_LENGTH = 32
    private const val IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val DERIVED_KEY_LENGTH = 32
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private val HKDF_INFO = "drducbook-api-key-sync-v1".toByteArray(Charsets.UTF_8)

    private val secureRandom = SecureRandom()

    fun encrypt(plaintext: ByteArray, masterSecret: String): ByteArray {
        require(masterSecret.isNotBlank()) { "Master secret must not be blank" }
        val salt = ByteArray(SALT_LENGTH).also(secureRandom::nextBytes)
        val iv = ByteArray(IV_LENGTH).also(secureRandom::nextBytes)
        val derivedKey = deriveKey(masterSecret, salt)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, derivedKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val ciphertext = cipher.doFinal(plaintext)

        return ByteBuffer.allocate(1 + SALT_LENGTH + IV_LENGTH + ciphertext.size)
            .put(PAYLOAD_VERSION)
            .put(salt)
            .put(iv)
            .put(ciphertext)
            .array()
    }

    fun decrypt(payload: ByteArray, masterSecret: String): ByteArray {
        require(masterSecret.isNotBlank()) { "Master secret must not be blank" }
        require(payload.size >= 1 + SALT_LENGTH + IV_LENGTH + 16) { "Invalid payload size" }

        val buffer = ByteBuffer.wrap(payload)
        val version = buffer.get()
        require(version == PAYLOAD_VERSION) { "Unsupported payload version: $version" }

        val salt = ByteArray(SALT_LENGTH).also(buffer::get)
        val iv = ByteArray(IV_LENGTH).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)

        val derivedKey = deriveKey(masterSecret, salt)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, derivedKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(secret: String, salt: ByteArray): SecretKeySpec {
        val ikm = secret.toByteArray(Charsets.UTF_8)
        val prk = hkdfExtract(salt, ikm)
        val okm = hkdfExpand(prk, HKDF_INFO, DERIVED_KEY_LENGTH)
        return SecretKeySpec(okm, "AES")
    }

    private fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val saltKey = if (salt.isNotEmpty()) salt else ByteArray(32)
        mac.init(SecretKeySpec(saltKey, HMAC_ALGORITHM))
        return mac.doFinal(ikm)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(prk, HMAC_ALGORITHM))
        val result = ByteArray(length)
        var t = ByteArray(0)
        var generated = 0
        var round = 1.toByte()

        while (generated < length) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(round)
            t = mac.doFinal()
            val toCopy = minOf(t.size, length - generated)
            System.arraycopy(t, 0, result, generated, toCopy)
            generated += toCopy
            round++
        }
        return result
    }
}
