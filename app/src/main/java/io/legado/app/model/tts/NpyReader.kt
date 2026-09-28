package io.legado.app.model.tts

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lightweight parser for NumPy `.npy` binary serialization format.
 * Supports little-endian float32 (<f4), int32 (<i4), int64 (<i8), and bool (|b1).
 */
internal object NpyReader {

    private val NPY_MAGIC = byteArrayOf(0x93.toByte(), 'N'.code.toByte(), 'U'.code.toByte(), 'M'.code.toByte(), 'P'.code.toByte(), 'Y'.code.toByte())

    data class NpyHeader(
        val descr: String,
        val fortranOrder: Boolean,
        val shape: LongArray,
        val dataOffset: Long,
        val dataBytes: Long,
    )

    fun readHeader(file: File): NpyHeader {
        FileInputStream(file).use { input ->
            val magic = ByteArray(6)
            if (input.read(magic) != 6 || !magic.contentEquals(NPY_MAGIC)) {
                throw IOException("Tệp không phải định dạng .npy hợp lệ: ${file.name}")
            }
            val major = input.read()
            val minor = input.read()
            if (major != 1 && major != 2) {
                throw IOException("Phiên bản NPY không được hỗ trợ: $major.$minor")
            }
            val headerLen = if (major == 1) {
                val b0 = input.read()
                val b1 = input.read()
                (b0 and 0xFF) or ((b1 and 0xFF) shl 8)
            } else {
                val b0 = input.read()
                val b1 = input.read()
                val b2 = input.read()
                val b3 = input.read()
                (b0 and 0xFF) or ((b1 and 0xFF) shl 8) or ((b2 and 0xFF) shl 16) or ((b3 and 0xFF) shl 24)
            }
            val headerBytes = ByteArray(headerLen)
            if (input.read(headerBytes) != headerLen) {
                throw IOException("Không thể đọc tiêu đề tệp .npy: ${file.name}")
            }
            val headerStr = String(headerBytes, Charsets.US_ASCII)
            val descr = Regex("'descr'\\s*:\\s*'([^']+)'").find(headerStr)?.groupValues?.get(1)
                ?: throw IOException("Tiêu đề NPY thiếu thuộc tính 'descr'")
            val fortran = Regex("'fortran_order'\\s*:\\s*(True|False)").find(headerStr)?.groupValues?.get(1)?.equals("True", ignoreCase = true) ?: false
            val shapeMatch = Regex("'shape'\\s*:\\s*\\(([^)]*)\\)").find(headerStr)?.groupValues?.get(1)
                ?: throw IOException("Tiêu đề NPY thiếu thuộc tính 'shape'")

            val shapeList = shapeMatch.split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map { it.toLongOrNull() ?: throw IOException("Kích thước shape NPY không hợp lệ: $it") }
            val shape = shapeList.toLongArray()
            val dataOffset = (if (major == 1) 10 else 12) + headerLen.toLong()
            val dataBytes = file.length() - dataOffset
            return NpyHeader(descr, fortran, shape, dataOffset, dataBytes)
        }
    }

    fun readFloatArray(file: File): FloatArray {
        val header = readHeader(file)
        val elementCount = header.shape.fold(1L) { acc, dim -> acc * dim }.toInt()
        val buffer = ByteBuffer.allocateDirect(elementCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        file.inputStream().use { input ->
            input.skip(header.dataOffset)
            val channel = input.channel
            channel.read(buffer)
        }
        buffer.flip()
        val result = FloatArray(elementCount)
        buffer.asFloatBuffer().get(result)
        return result
    }

    fun readLongArray(file: File): LongArray {
        val header = readHeader(file)
        val elementCount = header.shape.fold(1L) { acc, dim -> acc * dim }.toInt()
        val buffer = ByteBuffer.allocateDirect(elementCount * 8).order(ByteOrder.LITTLE_ENDIAN)
        file.inputStream().use { input ->
            input.skip(header.dataOffset)
            val channel = input.channel
            channel.read(buffer)
        }
        buffer.flip()
        val result = LongArray(elementCount)
        buffer.asLongBuffer().get(result)
        return result
    }
}
