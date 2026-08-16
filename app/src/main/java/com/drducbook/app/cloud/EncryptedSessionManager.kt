package com.drducbook.app.cloud

import android.content.Context
import android.util.Base64
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/**
 * Persists the Supabase session encrypted with an Android Keystore key.
 * The preference file contains only ciphertext and the GCM nonce.
 */
internal class EncryptedSessionManager(
    context: Context,
    configFingerprint: String,
) : SessionManager {

    private val preferences = context.applicationContext.getSharedPreferences(
        "supabase_session_$configFingerprint",
        Context.MODE_PRIVATE,
    )

    private val keyAlias = "drducbook.supabase.session.$configFingerprint"
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) {
        val plaintext = json.encodeToString(session).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext)
        preferences.edit()
            .putString(KEY_NONCE, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
    }

    override suspend fun loadSession(): UserSession {
        val nonce = preferences.getString(KEY_NONCE, null)
            ?: error("No session stored")
        val ciphertext = preferences.getString(KEY_CIPHERTEXT, null)
            ?: error("No session stored")
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(nonce, Base64.NO_WRAP)),
            )
            val plaintext = cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP))
            json.decodeFromString<UserSession>(plaintext.toString(Charsets.UTF_8))
        }.getOrElse {
            deleteSession()
            error("Stored session is invalid")
        }
    }

    override suspend fun deleteSession() {
        preferences.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KEY_ALGORITHM, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val KEY_NONCE = "nonce"
        const val KEY_CIPHERTEXT = "ciphertext"
    }
}
