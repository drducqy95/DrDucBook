package io.legado.app.model.tts

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/** Adds top-level ModelProto metadata without loading a large ONNX graph into memory. */
internal object OnnxMetadataEditor {

    fun addMissing(file: File, required: Map<String, String>) {
        val existing = read(file)
        required.forEach { (key, value) ->
            val current = existing[key]
            if (current != null && current != value) {
                throw IOException("Metadata ONNX $key không khớp: $current")
            }
        }
        val missing = required.filterKeys { it !in existing }
        if (missing.isEmpty()) return

        FileOutputStream(file, true).buffered().use { stream ->
            val output = CodedOutputStream.newInstance(stream)
            missing.forEach { (key, value) ->
                val entrySize = CodedOutputStream.computeStringSize(1, key) +
                    CodedOutputStream.computeStringSize(2, value)
                output.writeTag(METADATA_FIELD, WireFormat.WIRETYPE_LENGTH_DELIMITED)
                output.writeUInt32NoTag(entrySize)
                output.writeString(1, key)
                output.writeString(2, value)
            }
            output.flush()
        }

        val saved = read(file)
        if (required.any { (key, value) -> saved[key] != value }) {
            throw IOException("Không thể ghi metadata Piper vào ONNX")
        }
    }

    fun read(file: File): Map<String, String> {
        if (!file.isFile) throw IOException("Không tìm thấy ONNX: ${file.name}")
        val metadata = linkedMapOf<String, String>()
        FileInputStream(file).buffered().use { stream ->
            val input = CodedInputStream.newInstance(stream).apply {
                setSizeLimit(Int.MAX_VALUE)
            }
            while (!input.isAtEnd) {
                val tag = input.readTag()
                if (tag == 0) break
                if (WireFormat.getTagFieldNumber(tag) == METADATA_FIELD &&
                    WireFormat.getTagWireType(tag) == WireFormat.WIRETYPE_LENGTH_DELIMITED
                ) {
                    val entry = CodedInputStream.newInstance(input.readByteArray())
                    var key = ""
                    var value = ""
                    while (!entry.isAtEnd) {
                        when (val entryTag = entry.readTag()) {
                            0 -> break
                            KEY_TAG -> key = entry.readStringRequireUtf8()
                            VALUE_TAG -> value = entry.readStringRequireUtf8()
                            else -> if (!entry.skipField(entryTag)) break
                        }
                    }
                    if (key.isNotEmpty()) metadata[key] = value
                } else if (!input.skipField(tag)) {
                    break
                }
            }
        }
        return metadata
    }

    private const val METADATA_FIELD = 14
    private const val KEY_TAG = 10
    private const val VALUE_TAG = 18
}
