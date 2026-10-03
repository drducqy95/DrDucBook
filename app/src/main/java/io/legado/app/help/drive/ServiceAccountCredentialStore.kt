package io.legado.app.help.drive

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object ServiceAccountCredentialStore {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "drducbook.drive.service_account"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    fun validate(rawJson: String): Result<String> = runCatching {
        val trimmed = rawJson.trim()
        require(trimmed.isNotBlank()) { "JSON key không được để trống" }
        require(trimmed.startsWith("{") && trimmed.endsWith("}")) { "Định dạng JSON không hợp lệ" }

        val map = GSON.fromJsonObject<Map<String, Any>>(trimmed).getOrNull()
            ?: error("Không thể đọc cấu trúc JSON")

        val type = map["type"]?.toString()
        require(type == "service_account") { "File JSON không phải là Service Account (type=$type)" }

        val clientEmail = map["client_email"]?.toString().orEmpty().trim()
        require(clientEmail.isNotBlank() && clientEmail.contains("@")) {
            "Không tìm thấy client_email hợp lệ trong JSON"
        }

        val privateKey = map["private_key"]?.toString().orEmpty().trim()
        require(privateKey.isNotBlank() && privateKey.contains("BEGIN PRIVATE KEY")) {
            "Không tìm thấy private_key hợp lệ trong JSON"
        }

        clientEmail
    }

    fun extractEmail(rawJson: String): String? {
        return runCatching {
            val map = GSON.fromJsonObject<Map<String, Any>>(rawJson.trim()).getOrNull()
            map?.get("client_email")?.toString()?.trim()
        }.getOrNull()
    }

    fun encrypt(rawJson: String): String {
        val trimmed = rawJson.trim()
        if (trimmed.isBlank()) return ""

        return try {
            val key = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
            "enc_v1:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Throwable) {
            // Fallback for JVM tests or environments without Keystore
            "b64:" + Base64.encodeToString(trimmed.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    fun decrypt(encrypted: String): String {
        val trimmed = encrypted.trim()
        if (trimmed.isBlank()) return ""

        if (trimmed.startsWith("enc_v1:")) {
            val payload = Base64.decode(trimmed.removePrefix("enc_v1:"), Base64.NO_WRAP)
            val key = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_BITS, payload, 0, 12)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
            val decryptedBytes = cipher.doFinal(payload, 12, payload.size - 12)
            return String(decryptedBytes, Charsets.UTF_8)
        }

        if (trimmed.startsWith("b64:")) {
            val bytes = Base64.decode(trimmed.removePrefix("b64:"), Base64.NO_WRAP)
            return String(bytes, Charsets.UTF_8)
        }

        // Raw JSON backwards compatibility
        return trimmed
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }
}
