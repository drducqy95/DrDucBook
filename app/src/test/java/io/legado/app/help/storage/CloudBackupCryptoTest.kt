package io.legado.app.help.storage

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CloudBackupCryptoTest {

    @Test
    fun encryptAndDecryptRoundTrip() {
        val root = Files.createTempDirectory("backup-crypto-test").toFile()
        try {
            val source = File(root, "source.zip").apply {
                writeBytes(ByteArray(32_768) { index -> (index * 31).toByte() })
            }
            val encrypted = File(root, "snapshot.drducsnapshot")
            val restored = File(root, "restored.zip")

            CloudBackupCrypto.encrypt(source, encrypted, "correct horse battery")

            assertTrue(CloudBackupCrypto.isEncrypted(encrypted))
            assertFalse(source.readBytes().contentEquals(encrypted.readBytes()))
            CloudBackupCrypto.decrypt(encrypted, restored, "correct horse battery")
            assertArrayEquals(source.readBytes(), restored.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun wrongPasswordFailsAuthentication() {
        val root = Files.createTempDirectory("backup-crypto-test").toFile()
        try {
            val source = File(root, "source.zip").apply { writeText("private snapshot") }
            val encrypted = File(root, "snapshot.drducsnapshot")
            val restored = File(root, "restored.zip")
            CloudBackupCrypto.encrypt(source, encrypted, "correct horse battery")

            try {
                CloudBackupCrypto.decrypt(encrypted, restored, "wrong password")
                fail("Wrong password should fail authentication")
            } catch (_: Exception) {
                // Expected GCM authentication failure.
            }
            assertFalse(restored.exists() && restored.length() > 0L)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun shortPasswordIsRejected() {
        val root = Files.createTempDirectory("backup-crypto-test").toFile()
        try {
            val source = File(root, "source.zip").apply { writeText("snapshot") }
            try {
                CloudBackupCrypto.encrypt(source, File(root, "snapshot"), "short")
                fail("Short password should be rejected")
            } catch (_: IllegalArgumentException) {
                // Expected validation failure.
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
