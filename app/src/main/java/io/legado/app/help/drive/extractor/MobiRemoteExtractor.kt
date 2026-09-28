package io.legado.app.help.drive.extractor

import io.legado.app.lib.webdav.Authorization
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MobiRemoteExtractor {

    suspend fun extract(
        url: String,
        auth: Authorization?,
        fileSize: Long
    ): RemoteExtractedMetadata? {
        if (fileSize < 128) return null
        // Read first 16KB which includes PalmDB header, record list, and Record 0 (MOBI header)
        val headLen = minOf(fileSize, 16384L)
        val headBytes = RemoteRangeExtractor.readHead(url, auth, headLen) ?: return null
        if (headBytes.size < 78) return null

        val buf = ByteBuffer.wrap(headBytes).order(ByteOrder.BIG_ENDIAN)

        // 1. Palm Database Header
        val numRecords = buf.getShort(76).toInt() and 0xFFFF
        if (numRecords < 1) return null

        // Parse record offsets
        val recordOffsets = LongArray(minOf(numRecords, 200))
        for (i in recordOffsets.indices) {
            val entryPos = 78 + i * 8
            if (entryPos + 4 <= headBytes.size) {
                recordOffsets[i] = buf.getInt(entryPos).toLong() and 0xFFFFFFFFL
            }
        }

        val rec0Offset = recordOffsets[0].toInt()
        if (rec0Offset + 32 > headBytes.size) return null

        // 2. Check MOBI header signature at rec0Offset + 16
        val mobiSigOffset = rec0Offset + 16
        if (mobiSigOffset + 4 > headBytes.size) return null
        val mobiSig = String(headBytes, mobiSigOffset, 4, Charsets.US_ASCII)
        if (mobiSig != "MOBI") return null

        val mobiHeaderLength = buf.getInt(mobiSigOffset + 4)
        val textEncoding = if (mobiSigOffset + 12 <= headBytes.size) buf.getInt(mobiSigOffset + 12) else 1252
        val charset = if (textEncoding == 65001) Charsets.UTF_8 else Charsets.ISO_8859_1

        // Full name offset & length (relative to rec0Offset)
        var title: String? = null
        if (mobiSigOffset + 72 <= headBytes.size) {
            val fullNameOffset = rec0Offset + buf.getInt(mobiSigOffset + 68)
            val fullNameLen = buf.getInt(mobiSigOffset + 72)
            if (fullNameOffset >= 0 && fullNameOffset + fullNameLen <= headBytes.size && fullNameLen > 0) {
                title = String(headBytes, fullNameOffset, fullNameLen, charset)
            }
        }

        // First image index
        var firstImageIndex = -1
        if (mobiSigOffset + 92 <= headBytes.size) {
            firstImageIndex = buf.getInt(mobiSigOffset + 88)
        }

        // 3. Parse EXTH Header
        val exthOffset = mobiSigOffset + mobiHeaderLength
        var author: String? = null
        var intro: String? = null
        var coverRecordOffset = -1

        if (exthOffset + 12 <= headBytes.size) {
            val exthSig = String(headBytes, exthOffset, 4, Charsets.US_ASCII)
            if (exthSig == "EXTH") {
                val exthLength = buf.getInt(exthOffset + 4)
                val exthCount = buf.getInt(exthOffset + 8)
                var currPos = exthOffset + 12
                for (i in 0 until exthCount) {
                    if (currPos + 8 > headBytes.size || currPos >= exthOffset + exthLength) break
                    val tag = buf.getInt(currPos)
                    val len = buf.getInt(currPos + 4)
                    if (len < 8 || currPos + len > headBytes.size) break
                    val dataLen = len - 8
                    val dataOffset = currPos + 8

                    when (tag) {
                        100 -> author = String(headBytes, dataOffset, dataLen, charset)
                        103 -> intro = String(headBytes, dataOffset, dataLen, charset)
                        503 -> {
                            val fullTitle = String(headBytes, dataOffset, dataLen, charset)
                            if (fullTitle.isNotBlank()) title = fullTitle
                        }
                        201 -> {
                            if (dataLen >= 4) {
                                coverRecordOffset = buf.getInt(dataOffset)
                            }
                        }
                    }
                    currPos += len
                }
            }
        }

        // 4. Extract Cover Image if available
        var coverUri: String? = null
        if (coverRecordOffset >= 0 && firstImageIndex >= 0) {
            val coverIndex = firstImageIndex + coverRecordOffset
            if (coverIndex < recordOffsets.size) {
                val coverStart = recordOffsets[coverIndex]
                val coverEnd = if (coverIndex + 1 < recordOffsets.size) recordOffsets[coverIndex + 1] else coverStart + 150000L
                val coverLen = (coverEnd - coverStart).coerceIn(1L, 500000L)

                val coverBytes = RemoteRangeExtractor.readRange(url, auth, coverStart, coverStart + coverLen - 1)
                if (coverBytes != null && coverBytes.size > 10) {
                    val coverKey = "$url#mobicover"
                    val savedPath = RemoteCoverCache.saveCoverBytes(coverKey, coverBytes)
                    if (savedPath != null) {
                        coverUri = "file://$savedPath"
                    }
                }
            }
        }

        return RemoteExtractedMetadata(
            title = title,
            author = author,
            intro = intro,
            coverUrl = coverUri,
            format = "mobi"
        )
    }
}
