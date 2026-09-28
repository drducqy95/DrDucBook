package io.legado.app.model.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class NpyReaderTest {

    @Test
    fun readsFloat32NpyCorrectly() {
        val tempDir = Files.createTempDirectory("npy-test").toFile()
        try {
            val file = File(tempDir, "test.npy")
            val headerDict = "{'descr': '<f4', 'fortran_order': False, 'shape': (2, 3), }\n"
            val headerBytes = headerDict.toByteArray(Charsets.US_ASCII)
            val headerLen = headerBytes.size

            val buffer = ByteBuffer.allocate(10 + headerLen + 24).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put(byteArrayOf(0x93.toByte(), 'N'.code.toByte(), 'U'.code.toByte(), 'M'.code.toByte(), 'P'.code.toByte(), 'Y'.code.toByte()))
            buffer.put(1.toByte()) // major
            buffer.put(0.toByte()) // minor
            buffer.putShort(headerLen.toShort())
            buffer.put(headerBytes)

            // Data: 6 float32 values [1.0, 2.0, 3.0, 4.0, 5.0, 6.0]
            val expected = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f)
            expected.forEach { buffer.putFloat(it) }

            file.writeBytes(buffer.array())

            val header = NpyReader.readHeader(file)
            assertEquals("<f4", header.descr)
            assertEquals(false, header.fortranOrder)
            assertEquals(2, header.shape.size)
            assertEquals(2L, header.shape[0])
            assertEquals(3L, header.shape[1])

            val floats = NpyReader.readFloatArray(file)
            assertEquals(6, floats.size)
            for (i in expected.indices) {
                assertEquals(expected[i], floats[i], 1e-5f)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
