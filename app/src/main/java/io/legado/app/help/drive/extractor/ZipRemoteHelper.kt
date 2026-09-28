package io.legado.app.help.drive.extractor

import io.legado.app.lib.webdav.Authorization
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

data class ZipRemoteEntry(
    val name: String,
    val compressionMethod: Int, // 0 = stored, 8 = deflated
    val compressedSize: Long,
    val uncompressedSize: Long,
    val localHeaderOffset: Long
)

object ZipRemoteHelper {

    suspend fun readCentralDirectory(
        url: String,
        auth: Authorization?,
        fileSize: Long
    ): List<ZipRemoteEntry>? {
        if (fileSize < 22) return null
        val tailLen = minOf(fileSize, 65536L + 22L)
        val tailBytes = RemoteRangeExtractor.readTail(url, auth, fileSize, tailLen) ?: return null

        val eocdIndex = findEocd(tailBytes) ?: return null
        val buf = ByteBuffer.wrap(tailBytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(eocdIndex)
        val sig = buf.int
        val diskNumber = buf.short.toInt() and 0xFFFF
        val startDisk = buf.short.toInt() and 0xFFFF
        val totalEntriesDisk = buf.short.toInt() and 0xFFFF
        val totalEntries = buf.short.toInt() and 0xFFFF
        val cdSize = buf.int.toLong() and 0xFFFFFFFFL
        val cdOffset = buf.int.toLong() and 0xFFFFFFFFL

        val tailStartOffset = fileSize - tailBytes.size
        val cdBytes: ByteArray = if (cdOffset >= tailStartOffset && (cdOffset + cdSize) <= fileSize) {
            val relOffset = (cdOffset - tailStartOffset).toInt()
            tailBytes.copyOfRange(relOffset, (relOffset + cdSize).toInt())
        } else {
            RemoteRangeExtractor.readRange(url, auth, cdOffset, cdOffset + cdSize - 1) ?: return null
        }

        return parseCentralDirectoryEntries(cdBytes, totalEntries)
    }

    private fun findEocd(bytes: ByteArray): Int? {
        val sig = byteArrayOf(0x50, 0x4b, 0x05, 0x06)
        for (i in (bytes.size - 22) downTo 0) {
            if (bytes[i] == sig[0] && bytes[i + 1] == sig[1] && bytes[i + 2] == sig[2] && bytes[i + 3] == sig[3]) {
                return i
            }
        }
        return null
    }

    private fun parseCentralDirectoryEntries(bytes: ByteArray, maxEntries: Int): List<ZipRemoteEntry> {
        val entries = mutableListOf<ZipRemoteEntry>()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var count = 0
        while (buf.remaining() >= 46 && count < maxEntries) {
            val sig = buf.int
            if (sig != 0x02014b50) break
            val versionMadeBy = buf.short
            val versionNeeded = buf.short
            val flags = buf.short
            val method = buf.short.toInt() and 0xFFFF
            val time = buf.short
            val date = buf.short
            val crc = buf.int
            val compSize = buf.int.toLong() and 0xFFFFFFFFL
            val uncompSize = buf.int.toLong() and 0xFFFFFFFFL
            val nameLen = buf.short.toInt() and 0xFFFF
            val extraLen = buf.short.toInt() and 0xFFFF
            val commentLen = buf.short.toInt() and 0xFFFF
            val diskStart = buf.short
            val internalAttr = buf.short
            val externalAttr = buf.int
            val localOffset = buf.int.toLong() and 0xFFFFFFFFL

            if (buf.remaining() < nameLen) break
            val nameBytes = ByteArray(nameLen)
            buf.get(nameBytes)
            val name = String(nameBytes, Charsets.UTF_8)

            if (extraLen > 0 && buf.remaining() >= extraLen) {
                buf.position(buf.position() + extraLen)
            }
            if (commentLen > 0 && buf.remaining() >= commentLen) {
                buf.position(buf.position() + commentLen)
            }

            entries.add(
                ZipRemoteEntry(
                    name = name,
                    compressionMethod = method,
                    compressedSize = compSize,
                    uncompressedSize = uncompSize,
                    localHeaderOffset = localOffset
                )
            )
            count++
        }
        return entries
    }

    suspend fun extractEntryBytes(
        url: String,
        auth: Authorization?,
        entry: ZipRemoteEntry
    ): ByteArray? {
        val headerBytes = RemoteRangeExtractor.readRange(
            url,
            auth,
            entry.localHeaderOffset,
            entry.localHeaderOffset + 30 + 512
        ) ?: return null

        val buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buf.remaining() < 30) return null
        val sig = buf.int
        if (sig != 0x04034b50) return null
        buf.position(26)
        val nameLen = buf.short.toInt() and 0xFFFF
        val extraLen = buf.short.toInt() and 0xFFFF
        val dataStart = entry.localHeaderOffset + 30 + nameLen + extraLen
        val dataEnd = dataStart + entry.compressedSize - 1

        val rawData = RemoteRangeExtractor.readRange(url, auth, dataStart, dataEnd) ?: return null

        return if (entry.compressionMethod == 0) {
            rawData
        } else if (entry.compressionMethod == 8) {
            decompressDeflate(rawData, entry.uncompressedSize.toInt())
        } else {
            null
        }
    }

    private fun decompressDeflate(compressed: ByteArray, expectedSize: Int): ByteArray? {
        return try {
            val inflater = Inflater(true)
            inflater.setInput(compressed)
            val output = ByteArrayOutputStream(if (expectedSize > 0) expectedSize else compressed.size * 2)
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && inflater.needsInput()) break
                output.write(buffer, 0, count)
            }
            inflater.end()
            output.toByteArray()
        } catch (e: Exception) {
            null
        }
    }
}
