package io.legado.app.help.storage

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-based encryption for cloud snapshots. The password never leaves the device. */
object CloudBackupCrypto {
    private val magic = "DRDUC-SNAPSHOT".toByteArray(Charsets.US_ASCII)
    private const val VERSION = 2
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val KEY_SIZE = 256
    private const val TAG_SIZE = 128
    private const val ITERATIONS = 600_000

    fun encrypt(source: File, destination: File, password: String) {
        require(password.length >= 8) { "Backup password must contain at least 8 characters" }
        val salt = ByteArray(SALT_SIZE)
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(salt)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            deriveKey(password, salt),
            GCMParameterSpec(TAG_SIZE, iv),
        )
        destination.parentFile?.mkdirs()
        FileOutputStream(destination).use { raw ->
            DataOutputStream(raw).use { header ->
                header.write(magic)
                header.writeByte(VERSION)
                header.writeInt(ITERATIONS)
                header.writeInt(salt.size)
                header.writeInt(iv.size)
                header.write(salt)
                header.write(iv)
                header.flush()
                CipherOutputStream(raw, cipher).use { encrypted ->
                    source.inputStream().buffered().use { it.copyTo(encrypted) }
                }
            }
        }
    }

    fun decrypt(source: File, destination: File, password: String) {
        require(password.length >= 8) { "Backup password must contain at least 8 characters" }
        FileInputStream(source).use { raw ->
            val header = DataInputStream(raw)
            val actualMagic = ByteArray(magic.size)
            header.readFully(actualMagic)
            require(actualMagic.contentEquals(magic)) { "Unsupported backup format" }
            require(header.readUnsignedByte() == VERSION) { "Unsupported backup version" }
            val iterations = header.readInt()
            val salt = ByteArray(header.readInt().also { require(it == SALT_SIZE) })
            val iv = ByteArray(header.readInt().also { require(it == IV_SIZE) })
            header.readFully(salt)
            header.readFully(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                deriveKey(password, salt, iterations),
                GCMParameterSpec(TAG_SIZE, iv),
            )
            destination.parentFile?.mkdirs()
            CipherInputStream(raw, cipher).use { encrypted ->
                destination.outputStream().buffered().use { encrypted.copyTo(it) }
            }
        }
    }

    fun isEncrypted(file: File): Boolean = file.inputStream().buffered().use { input ->
        val prefix = ByteArray(magic.size)
        val read = input.read(prefix)
        read == magic.size && prefix.contentEquals(magic)
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int = ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_SIZE)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
