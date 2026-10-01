package io.legado.app.data.repository

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.legado.app.domain.model.QUICK_DICTIONARY_IGNORE_TARGET
import io.legado.app.domain.model.QuickDictionaryEntry
import io.legado.app.domain.model.QuickDictionaryImportPhase
import io.legado.app.domain.model.QuickDictionaryImportProgress
import io.legado.app.domain.model.QuickDictionaryImportResult
import io.legado.app.domain.model.QuickDictionaryPack
import io.legado.app.domain.model.QuickDictionaryPackState
import io.legado.app.domain.model.QuickDictionaryPackStatus
import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.utils.GSON
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import splitties.init.appCtx
import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset
import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent, bounded-memory dictionaries for user imports with millions of lines.
 *
 * The source remains line-editable on disk while translation uses the same open-addressed mmap
 * format as the bundled QT2020 index. Only terms that occur in the current chapter are projected
 * into [QuickDictionaryEntry], so Room and the translation pipeline never materialize the pack.
 */
class QuickDictionaryPackStore(
    private val root: File = File(appCtx.filesDir, DIRECTORY_NAME),
) {

    init {
        root.mkdirs()
        cleanupIncompletePacks()
    }
    private val _packs = MutableStateFlow(loadMetadata())
    val packs = _packs.asStateFlow()
    private val _originalPackState = MutableStateFlow(loadOriginalPackState())
    val originalPackState = _originalPackState.asStateFlow()
    private val mappedIndexes = ConcurrentHashMap<String, MappedDictionaryIndex>()

    fun versionToken(): String = _packs.value
        .joinToString("|") { "${it.id}:${it.updatedAt}:${it.entryCount}:${it.enabled}" }
        .ifBlank { "none" }

    fun importPack(
        sourceFile: File,
        displayName: String,
        type: QuickDictionaryType,
        scope: QuickDictionaryScope,
        scopeKey: String,
        isExistingEntry: (QuickDictionaryType, String) -> Boolean = { _, _ -> false },
        onProgress: (QuickDictionaryImportProgress) -> Unit,
    ): QuickDictionaryImportResult {
        require(sourceFile.isFile) { "Dictionary import file does not exist" }
        require(displayName.isNotBlank()) { "Dictionary name is required" }
        require(scope == QuickDictionaryScope.GLOBAL || scopeKey.isNotBlank()) {
            "Dictionary scope is required"
        }
        val id = "pack_${UUID.randomUUID().toString().replace("-", "")}"
        val indexFile = File(root, "$id.qtdict")
        val normalizedSource = File(root, "$id.source.txt")
        val metadataFile = File(root, "$id.json")
        return try {
            val build = QuickDictionaryIndexBuilder.build(
                input = sourceFile,
                indexFile = indexFile,
                normalizedSourceFile = normalizedSource,
                type = type,
                isExistingEntry = { raw -> isExistingEntry(type, raw) },
                onProgress = onProgress,
            )
            if (build.entryCount == 0) {
                indexFile.delete()
                normalizedSource.delete()
                if (build.duplicateLines > 0) {
                    return QuickDictionaryImportResult(
                        pack = null,
                        rejectedLines = build.rejectedLines,
                        duplicateLines = build.duplicateLines,
                        importedEntries = 0,
                    )
                }
                error("No valid dictionary entries were found")
            }
            require(QuickDictionaryIndexBuilder.validate(indexFile)) {
                "Dictionary index validation failed"
            }
            val now = System.currentTimeMillis()
            val pack = QuickDictionaryPack(
                id = id,
                name = displayName.trim(),
                type = type,
                scope = scope,
                scopeKey = if (scope == QuickDictionaryScope.GLOBAL) "" else scopeKey,
                entryCount = build.entryCount,
                indexBytes = indexFile.length(),
                sourceBytes = normalizedSource.length(),
                createdAt = now,
                updatedAt = now,
            )
            metadataFile.writeText(GSON.toJson(pack), Charsets.UTF_8)
            _packs.value = (_packs.value + pack)
                .sortedWith(compareBy<QuickDictionaryPack> { it.scope }.thenBy { it.name })
            QuickDictionaryImportResult(
                pack = pack,
                rejectedLines = build.rejectedLines,
                duplicateLines = build.duplicateLines,
            )
        } catch (error: Throwable) {
            indexFile.delete()
            normalizedSource.delete()
            metadataFile.delete()
            throw error
        }
    }

    fun deletePack(id: String) {
        val safeId = id.takeIf { PACK_ID.matches(it) } ?: return
        mappedIndexes.remove(safeId)
        File(root, "$safeId.qtdict").delete()
        File(root, "$safeId.source.txt").delete()
        File(root, "$safeId.json").delete()
        _packs.value = _packs.value.filterNot { it.id == safeId }
        if (_originalPackState.value.activePackId == safeId) {
            _originalPackState.value = _originalPackState.value.copy(
                status = QuickDictionaryPackStatus.NOT_INSTALLED,
                activePackId = null,
                activeRevision = null,
                entryCount = 0,
            )
            persistOriginalPackState()
        }
    }

    fun getPack(id: String): QuickDictionaryPack? = _packs.value.firstOrNull { it.id == id }

    /** True only when the downloaded original pack has a verified active copy. */
    fun hasOriginalPack(): Boolean {
        val state = _originalPackState.value
        return state.status == QuickDictionaryPackStatus.READY ||
            state.status == QuickDictionaryPackStatus.EDITED ||
            state.status == QuickDictionaryPackStatus.OUTDATED
    }

    fun markOriginalDownloading() {
        _originalPackState.value = _originalPackState.value.copy(
            status = QuickDictionaryPackStatus.DOWNLOADING,
            lastError = null,
        )
        persistOriginalPackState()
    }

    fun markOriginalVerifying() {
        _originalPackState.value = _originalPackState.value.copy(
            status = QuickDictionaryPackStatus.VERIFYING,
            lastError = null,
        )
        persistOriginalPackState()
    }

    fun markOriginalFailure(message: String) {
        _originalPackState.value = _originalPackState.value.copy(
            status = QuickDictionaryPackStatus.FAILED,
            lastError = message.take(MAX_STATE_ERROR_LENGTH),
        )
        persistOriginalPackState()
    }

    fun markOriginalEdited() {
        if (!hasOriginalPack()) return
        _originalPackState.value = _originalPackState.value.copy(
            status = QuickDictionaryPackStatus.EDITED,
            activeRevision = "${_originalPackState.value.pristineRevision}:edited:${System.currentTimeMillis()}",
            lastError = null,
        )
        persistOriginalPackState()
    }

    /**
     * Records a downloaded pack as the active original pack and keeps an immutable source copy.
     * The source is written to a temporary file first so an interrupted install cannot replace the
     * last known-good pristine dictionary.
     */
    fun registerOriginalPack(pack: QuickDictionaryPack, sourceFile: File) {
        require(sourceFile.isFile && sourceFile.length() > 0L) {
            "Original dictionary source is missing"
        }
        markOriginalVerifying()
        val pristine = File(root, ORIGINAL_PRISTINE_SOURCE)
        val temporary = File(root, "$ORIGINAL_PRISTINE_SOURCE.tmp")
        try {
            sourceFile.copyTo(temporary, overwrite = true)
            atomicReplace(temporary, pristine)
            val revision = fileRevision(pristine)
            _originalPackState.value = QuickDictionaryPackState(
                status = QuickDictionaryPackStatus.READY,
                activePackId = pack.id,
                activeRevision = revision,
                pristineRevision = revision,
                entryCount = pack.entryCount,
                lastVerifiedAt = System.currentTimeMillis(),
            )
            persistOriginalPackState()
        } catch (error: Throwable) {
            temporary.delete()
            markOriginalFailure(error.message ?: "Không thể lưu bản QT gốc")
            throw error
        }
    }

    /** Restores the active original pack from the immutable source and rebuilds its index. */
    fun restoreOriginalPack(): QuickDictionaryPack? {
        val state = _originalPackState.value
        val pristine = File(root, ORIGINAL_PRISTINE_SOURCE)
        if (!pristine.isFile || pristine.length() == 0L) {
            markOriginalFailure("Không tìm thấy bản QT gốc")
            return null
        }
        markOriginalVerifying()
        state.activePackId?.let { deletePack(it) }
        val restored = runCatching {
            importPack(
                sourceFile = pristine,
                displayName = ORIGINAL_DISPLAY_NAME,
                type = QuickDictionaryType.VIETPHRASE,
                scope = QuickDictionaryScope.GLOBAL,
                scopeKey = "",
                onProgress = {},
            ).pack
        }.getOrNull()
        if (restored == null) {
            markOriginalFailure("Không thể khôi phục bản QT gốc")
            return null
        }
        registerOriginalPack(restored, pristine)
        return restored
    }

    fun containsEntry(
        type: QuickDictionaryType,
        raw: String,
        scope: QuickDictionaryScope,
        scopeKey: String,
    ): Boolean {
        val normalizedRaw = raw.trim()
        if (normalizedRaw.isEmpty()) return false
        return _packs.value.asSequence()
            .filter { pack ->
                sameDictionaryLane(pack.type, type) &&
                    (pack.scope == QuickDictionaryScope.GLOBAL ||
                        pack.scope == scope && pack.scopeKey == scopeKey)
            }
            .any { pack -> openIndex(pack)?.containsExact(normalizedRaw) == true }
    }

    fun matchEntries(
        context: String,
        projectKey: String,
        activeUniverseKey: String,
    ): List<QuickDictionaryEntry> {
        if (context.isEmpty()) return emptyList()
        val applicable = _packs.value.filter { pack ->
            pack.enabled && when (pack.scope) {
                QuickDictionaryScope.GLOBAL -> true
                QuickDictionaryScope.UNIVERSE -> pack.scopeKey == activeUniverseKey
                QuickDictionaryScope.PROJECT -> pack.scopeKey == projectKey
            }
        }
        if (applicable.isEmpty()) return emptyList()
        val matches = LinkedHashMap<String, QuickDictionaryEntry>()
        applicable.forEach { pack ->
            val index = openIndex(pack) ?: return@forEach
            var offset = 0
            while (offset < context.length) {
                index.longestAt(context, offset)?.let { match ->
                    val key = "${pack.type}\u0000${match.source.lowercase()}"
                    matches[key] = QuickDictionaryEntry(
                        raw = match.source,
                        hanViet = if (pack.type == QuickDictionaryType.PHONETIC) {
                            match.target
                        } else {
                            ""
                        },
                        target = when (pack.type) {
                            QuickDictionaryType.PHONETIC,
                            QuickDictionaryType.IGNORE -> ""
                            else -> match.target
                        },
                        type = pack.type,
                        scope = pack.scope,
                        scopeKey = pack.scopeKey,
                        enabled = pack.enabled,
                        updatedAt = pack.updatedAt,
                    )
                }
                val codePoint = context.codePointAt(offset)
                offset += Character.charCount(codePoint)
            }
        }
        return matches.values.toList()
    }

    private fun openIndex(pack: QuickDictionaryPack): MappedDictionaryIndex? {
        mappedIndexes[pack.id]?.let { return it }
        val index = MappedDictionaryIndex.openOrNull(File(root, "${pack.id}.qtdict"))
            ?: return null
        return mappedIndexes.putIfAbsent(pack.id, index) ?: index
    }

    private fun loadMetadata(): List<QuickDictionaryPack> {
        return root.listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .mapNotNull(::readMetadataPack)
            .filter { pack ->
                File(root, "${pack.id}.qtdict").isFile &&
                    File(root, "${pack.id}.source.txt").isFile
            }
            .sortedWith(compareBy<QuickDictionaryPack> { it.scope }.thenBy { it.name })
    }

    /**
     * Gson can instantiate Kotlin data classes without calling their constructor. A malformed or
     * old metadata file can therefore contain null in a property declared non-null in Kotlin.
     * Validate the JSON object before deserializing so startup never reaches Regex.matches (or
     * sorting) with an invalid pack.
     */
    private fun readMetadataPack(file: File): QuickDictionaryPack? {
        return runCatching {
            val json = JsonParser.parseString(file.readText(Charsets.UTF_8))
                .takeIf(JsonElement::isJsonObject)
                ?.asJsonObject
                ?: return@runCatching null
            val id = json.stringValue("id")?.takeIf(PACK_ID::matches) ?: return@runCatching null
            val name = json.stringValue("name")?.takeIf(String::isNotBlank)
                ?: return@runCatching null
            val type = json.stringValue("type")
                ?.let { value -> QuickDictionaryType.entries.firstOrNull { it.name == value } }
                ?: return@runCatching null
            val scope = json.stringValue("scope")
                ?.let { value -> QuickDictionaryScope.entries.firstOrNull { it.name == value } }
                ?: return@runCatching null
            if (json.stringValue("scopeKey") == null) return@runCatching null

            GSON.fromJson(json, QuickDictionaryPack::class.java)?.takeIf { pack ->
                // Keep the values captured from the validated JSON as the final guard against a
                // future Gson adapter returning a partially initialized object.
                pack.id == id &&
                    pack.name == name &&
                    pack.type == type &&
                    pack.scope == scope
            }
        }.getOrNull()
    }

    private fun com.google.gson.JsonObject.stringValue(name: String): String? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
        return value.asString
    }

    private fun loadOriginalPackState(): QuickDictionaryPackState {
        val state = runCatching {
            val file = File(root, ORIGINAL_STATE_FILE)
            if (!file.isFile) null else GSON.fromJson(
                file.readText(Charsets.UTF_8),
                QuickDictionaryPackState::class.java,
            )
        }.getOrNull() ?: return QuickDictionaryPackState()
        val activeId = state.activePackId
        if (activeId != null && getPack(activeId) == null) {
            return state.copy(
                status = QuickDictionaryPackStatus.NOT_INSTALLED,
                activePackId = null,
            )
        }
        return state
    }

    private fun persistOriginalPackState() {
        val file = File(root, ORIGINAL_STATE_FILE)
        val temporary = File(root, "$ORIGINAL_STATE_FILE.tmp")
        runCatching {
            temporary.writeText(GSON.toJson(_originalPackState.value), Charsets.UTF_8)
            atomicReplace(temporary, file)
        }.onFailure { temporary.delete() }
    }

    private fun fileRevision(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun atomicReplace(source: File, target: File) {
        if (target.exists() && !target.delete()) {
            throw IllegalStateException("Cannot replace dictionary file")
        }
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
    }

    private fun cleanupIncompletePacks() {
        val ids = root.listFiles().orEmpty().asSequence()
            .mapNotNull { file -> PACK_FILE.find(file.name)?.groupValues?.getOrNull(1) }
            .toSet()
        ids.forEach { id ->
            val index = File(root, "$id.qtdict")
            val source = File(root, "$id.source.txt")
            val metadata = File(root, "$id.json")
            if (!index.isFile || !source.isFile || !metadata.isFile) {
                index.delete()
                source.delete()
                metadata.delete()
            } else {
                runCatching {
                    val text = source.readText(Charsets.UTF_8)
                    if (text.contains("\ttrue") || text.contains("\tfalse")) {
                        val sanitized = text.lines().joinToString("\n") { line ->
                            val delimiter = line.indexOf('=')
                            if (delimiter > 0) {
                                val k = line.substring(0, delimiter)
                                val v = cleanQuickDictionaryTarget(line.substring(delimiter + 1))
                                "$k=$v"
                            } else {
                                line
                            }
                        }
                        source.writeText(sanitized, Charsets.UTF_8)
                    }
                }
            }
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "quick_dictionary_packs"
        const val ORIGINAL_STATE_FILE = "qt-original-state.json"
        const val ORIGINAL_PRISTINE_SOURCE = "qt-original-pristine.source.txt"
        const val ORIGINAL_DISPLAY_NAME = "QT gốc"
        const val MAX_STATE_ERROR_LENGTH = 300
        val PACK_ID = Regex("pack_[a-f0-9]{32}")
        val PACK_FILE = Regex("^(pack_[a-f0-9]{32})\\.(?:qtdict|source\\.txt|json)$")

        fun sameDictionaryLane(first: QuickDictionaryType, second: QuickDictionaryType): Boolean {
            return (first == QuickDictionaryType.PHONETIC) ==
                (second == QuickDictionaryType.PHONETIC)
        }
    }
}

private object QuickDictionaryIndexBuilder {

    data class Result(
        val entryCount: Int,
        val rejectedLines: Int,
        val duplicateLines: Int,
    )

    fun build(
        input: File,
        indexFile: File,
        normalizedSourceFile: File,
        type: QuickDictionaryType,
        isExistingEntry: (String) -> Boolean,
        onProgress: (QuickDictionaryImportProgress) -> Unit,
    ): Result {
        var totalLines = 0
        var validLines = 0
        var rejectedLines = 0
        var existingDuplicateLines = 0
        var estimatedBlobBytes = 0L
        openDictionaryReader(input).use { source ->
            val reader = source.reader
            while (true) {
                val line = reader.readLine() ?: break
                totalLines++
                val parsed = parseLine(line, type)
                if (parsed == null) {
                    rejectedLines++
                } else if (isExistingEntry(parsed.first)) {
                    existingDuplicateLines++
                } else {
                    validLines++
                    val target = if (type == QuickDictionaryType.IGNORE) {
                        QUICK_DICTIONARY_IGNORE_TARGET
                    } else {
                        parsed.second
                    }
                    estimatedBlobBytes += ENTRY_HEADER_SIZE +
                        parsed.first.lowercase().length.toLong() * Char.SIZE_BYTES +
                        target.toByteArray(Charsets.UTF_8).size
                }
                if (totalLines % PROGRESS_INTERVAL == 0) {
                    onProgress(
                        QuickDictionaryImportProgress(
                            phase = QuickDictionaryImportPhase.ANALYZING,
                            processedLines = totalLines,
                            totalLines = 0,
                            importedEntries = validLines,
                            processedBytes = source.bytesRead,
                            totalBytes = input.length(),
                            duplicateLines = existingDuplicateLines,
                        )
                    )
                }
            }
        }
        val bucketCount = bucketCountFor(validLines)
        val blobOffset = HEADER_SIZE.toLong() + bucketCount.toLong() * Int.SIZE_BYTES
        require(blobOffset < Int.MAX_VALUE) { "Dictionary index is too large" }
        require(blobOffset + estimatedBlobBytes <= Int.MAX_VALUE) {
            "Dictionary index exceeded 2 GiB"
        }
        var entryCount = 0
        var inputDuplicateLines = 0
        var maxSourceChars = 0
        var cursor = blobOffset.toInt()

        RandomAccessFile(indexFile, "rw").use { index ->
            index.setLength(blobOffset)
            writeHeader(
                output = index,
                bucketCount = bucketCount,
                entryCount = 0,
                maxSourceChars = 1,
                blobOffset = blobOffset.toInt(),
            )
            val bucketPointers = ByteBuffer
                .allocateDirect(bucketCount * Int.SIZE_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
            normalizedSourceFile.bufferedWriter(Charsets.UTF_8, SOURCE_BUFFER_BYTES).use { source ->
                openDictionaryReader(input).use { sourceInput ->
                    val reader = sourceInput.reader
                    var processed = 0
                    while (true) {
                        val line = reader.readLine() ?: break
                        processed++
                        val parsed = parseLine(line, type) ?: continue
                        if (isExistingEntry(parsed.first)) continue
                        val normalizedRaw = parsed.first.lowercase()
                        val target = if (type == QuickDictionaryType.IGNORE) {
                            QUICK_DICTIONARY_IGNORE_TARGET
                        } else {
                            parsed.second
                        }
                        val insert = insert(
                            output = index,
                            bucketPointers = bucketPointers,
                            cursor = cursor,
                            source = normalizedRaw,
                            target = target,
                        )
                        if (insert.inserted) {
                            cursor = insert.nextCursor
                            entryCount++
                            maxSourceChars = maxOf(maxSourceChars, normalizedRaw.length)
                            source.append(parsed.first)
                            source.append('=')
                            source.append(parsed.second)
                            source.newLine()
                        } else {
                            inputDuplicateLines++
                        }
                        if (processed % PROGRESS_INTERVAL == 0) {
                            onProgress(
                                QuickDictionaryImportProgress(
                                    phase = QuickDictionaryImportPhase.INDEXING,
                                    processedLines = processed,
                                    totalLines = totalLines,
                                    importedEntries = entryCount,
                                    processedBytes = sourceInput.bytesRead,
                                    totalBytes = input.length(),
                                    duplicateLines = existingDuplicateLines + inputDuplicateLines,
                                )
                            )
                        }
                    }
                }
            }
            writeBucketPointers(index, bucketPointers)
            index.setLength(cursor.toLong())
            writeHeader(
                output = index,
                bucketCount = bucketCount,
                entryCount = entryCount,
                maxSourceChars = maxSourceChars,
                blobOffset = blobOffset.toInt(),
            )
        }
        onProgress(
            QuickDictionaryImportProgress(
                phase = QuickDictionaryImportPhase.INDEXING,
                processedLines = totalLines,
                totalLines = totalLines,
                importedEntries = entryCount,
                processedBytes = input.length(),
                totalBytes = input.length(),
                duplicateLines = existingDuplicateLines + inputDuplicateLines,
            )
        )
        return Result(
            entryCount = entryCount,
            rejectedLines = rejectedLines,
            duplicateLines = existingDuplicateLines + inputDuplicateLines,
        )
    }

    fun validate(indexFile: File): Boolean = runCatching {
        RandomAccessFile(indexFile, "r").use { input ->
            val magic = ByteArray(MAGIC.size)
            input.readFully(magic)
            require(magic.contentEquals(MAGIC))
            require(input.readIntLe() == FORMAT_VERSION)
            val bucketCount = input.readIntLe()
            val entryCount = input.readIntLe()
            val maxSourceChars = input.readIntLe()
            val blobOffset = input.readIntLe()
            require(bucketCount > 0 && bucketCount.countOneBits() == 1)
            require(entryCount in 1 until bucketCount)
            require(maxSourceChars in 1..MAX_SOURCE_CHARS)
            require(blobOffset == HEADER_SIZE + bucketCount * Int.SIZE_BYTES)
            require(indexFile.length() > blobOffset)
        }
        true
    }.getOrDefault(false)

    private fun writeBucketPointers(
        output: RandomAccessFile,
        pointers: ByteBuffer,
    ) {
        pointers.position(0)
        output.seek(HEADER_SIZE.toLong())
        val buffer = ByteArray(SOURCE_BUFFER_BYTES)
        while (pointers.hasRemaining()) {
            val count = minOf(buffer.size, pointers.remaining())
            pointers.get(buffer, 0, count)
            output.write(buffer, 0, count)
        }
    }

    private data class InsertResult(
        val inserted: Boolean,
        val nextCursor: Int,
    )

    private fun insert(
        output: RandomAccessFile,
        bucketPointers: ByteBuffer,
        cursor: Int,
        source: String,
        target: String,
    ): InsertResult {
        val sourceBytes = source.toByteArray(Charsets.UTF_16LE)
        val targetBytes = target.toByteArray(Charsets.UTF_8)
        require(source.length in 1..MAX_SOURCE_CHARS) { "Dictionary source is too long" }
        require(targetBytes.size <= MAX_TARGET_BYTES) { "Dictionary target is too long" }
        val hash = fnv1aUtf16(source)
        val bucketCount = bucketPointers.capacity() / Int.SIZE_BYTES
        val mask = bucketCount - 1
        var bucket = hash and mask
        repeat(bucketCount) {
            val pointerOffset = bucket * Int.SIZE_BYTES
            val pointer = bucketPointers.getInt(pointerOffset)
            if (pointer == 0) {
                bucketPointers.putInt(pointerOffset, cursor)
                output.seek(cursor.toLong())
                output.writeIntLe(hash)
                output.writeShortLe(source.length)
                output.writeShortLe(0)
                output.writeIntLe(targetBytes.size)
                output.write(sourceBytes)
                output.write(targetBytes)
                return InsertResult(
                    inserted = true,
                    nextCursor = cursor + ENTRY_HEADER_SIZE + sourceBytes.size + targetBytes.size,
                )
            }
            if (entrySourceEquals(output, pointer, hash, source)) {
                return InsertResult(inserted = false, nextCursor = cursor)
            }
            bucket = (bucket + 1) and mask
        }
        error("Dictionary hash table is full")
    }

    private fun entrySourceEquals(
        input: RandomAccessFile,
        pointer: Int,
        hash: Int,
        source: String,
    ): Boolean {
        input.seek(pointer.toLong())
        if (input.readIntLe() != hash) return false
        val length = input.readUnsignedShortLe()
        input.readUnsignedShortLe()
        input.readIntLe()
        if (length != source.length) return false
        repeat(length) { index ->
            if (input.readUnsignedShortLe().toChar() != source[index]) return false
        }
        return true
    }

    private fun writeHeader(
        output: RandomAccessFile,
        bucketCount: Int,
        entryCount: Int,
        maxSourceChars: Int,
        blobOffset: Int,
    ) {
        output.seek(0)
        output.write(MAGIC)
        output.writeIntLe(FORMAT_VERSION)
        output.writeIntLe(bucketCount)
        output.writeIntLe(entryCount)
        output.writeIntLe(maxSourceChars.coerceAtLeast(1))
        output.writeIntLe(blobOffset)
        output.writeIntLe(0)
    }

    private fun parseLine(
        sourceLine: String,
        type: QuickDictionaryType,
    ): Pair<String, String>? {
        val line = sourceLine.trim()
        if (line.isEmpty() || line.startsWith('#') || line.startsWith("//")) return null
        val split = IMPORT_SEPARATORS.asSequence()
            .mapNotNull { separator ->
                line.indexOf(separator).takeIf { it > 0 }?.let { separator to it }
            }
            .minByOrNull { it.second }
        val raw = split?.let { line.substring(0, it.second).trim() }
            ?: if (type == QuickDictionaryType.IGNORE) line else return null
        val target = split
            ?.let { cleanQuickDictionaryTarget(line.substring(it.second + it.first.length)) }
            .orEmpty()
        return when {
            raw.isEmpty() -> null
            raw.length > MAX_SOURCE_CHARS -> null
            type == QuickDictionaryType.IGNORE -> raw to ""
            type == QuickDictionaryType.PHONETIC &&
                raw.codePointCount(0, raw.length) != 1 -> null
            target.isEmpty() -> null
            else -> raw to target
        }
    }

    private fun bucketCountFor(entries: Int): Int {
        var count = 2
        while (entries.toDouble() / count > MAX_LOAD_FACTOR) {
            require(count < MAX_BUCKET_COUNT) { "Dictionary contains too many entries" }
            count = count shl 1
        }
        return count
    }

    private fun fnv1aUtf16(value: String): Int {
        var hash = FNV_OFFSET_BASIS
        value.forEach { char -> hash = (hash xor char.code) * FNV_PRIME }
        return hash
    }

    private val IMPORT_SEPARATORS = listOf("\t", "=>", "\u2192", "=", "|")
    private val MAGIC = "QTDCT001".toByteArray(Charsets.US_ASCII)
    private const val FORMAT_VERSION = 1
    private const val HEADER_SIZE = 32
    private const val ENTRY_HEADER_SIZE = 12
    private const val MAX_LOAD_FACTOR = 0.70
    private const val MAX_BUCKET_COUNT = 1 shl 25
    private const val MAX_TARGET_BYTES = 1 shl 20
    private const val MAX_SOURCE_CHARS = 256
    private const val FNV_OFFSET_BASIS = -2128831035
    private const val FNV_PRIME = 16777619
    private const val PROGRESS_INTERVAL = 5_000
    private const val SOURCE_BUFFER_BYTES = 64 * 1024
}

private class MappedDictionaryIndex private constructor(
    private val buffer: ByteBuffer,
    private val bucketCount: Int,
    private val maxSourceChars: Int,
) {

    private val hashScratch = ThreadLocal.withInitial { IntArray(maxSourceChars) }

    data class Match(
        val source: String,
        val target: String,
        val endExclusive: Int,
    )

    private val targetCache = object : LinkedHashMap<Int, String>(4_096, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean {
            return size > 4_096
        }
    }

    fun longestAt(text: String, offset: Int): Match? {
        val available = (text.length - offset).coerceAtMost(maxSourceChars)
        if (available <= 0) return null
        val hashes = checkNotNull(hashScratch.get())
        var hash = FNV_OFFSET_BASIS
        for (length in 1..available) {
            hash = (hash xor text[offset + length - 1].lowercaseChar().code) * FNV_PRIME
            hashes[length - 1] = hash
        }
        for (length in available downTo 1) {
            val entryOffset = findEntry(text, offset, length, hashes[length - 1])
            if (entryOffset >= 0) {
                return Match(
                    source = text.substring(offset, offset + length),
                    target = targetAt(entryOffset, length),
                    endExclusive = offset + length,
                )
            }
        }
        return null
    }

    fun containsExact(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isEmpty() || normalized.length > maxSourceChars) return false
        var hash = FNV_OFFSET_BASIS
        normalized.forEach { character ->
            hash = (hash xor character.lowercaseChar().code) * FNV_PRIME
        }
        return findEntry(normalized, 0, normalized.length, hash) >= 0
    }

    private fun findEntry(
        text: String,
        textOffset: Int,
        sourceLength: Int,
        hash: Int,
    ): Int {
        val mask = bucketCount - 1
        var bucket = hash and mask
        repeat(bucketCount) {
            val entryOffset = buffer.getInt(HEADER_SIZE + bucket * Int.SIZE_BYTES)
            if (entryOffset == 0) return -1
            if (entryMatches(entryOffset, text, textOffset, sourceLength, hash)) return entryOffset
            bucket = (bucket + 1) and mask
        }
        return -1
    }

    private fun entryMatches(
        entryOffset: Int,
        text: String,
        textOffset: Int,
        sourceLength: Int,
        hash: Int,
    ): Boolean {
        if (buffer.getInt(entryOffset) != hash) return false
        if (buffer.getShort(entryOffset + 4).toInt() and 0xffff != sourceLength) return false
        val sourceOffset = entryOffset + ENTRY_HEADER_SIZE
        repeat(sourceLength) { index ->
            if (buffer.getChar(sourceOffset + index * Char.SIZE_BYTES) !=
                text[textOffset + index].lowercaseChar()
            ) {
                return false
            }
        }
        return true
    }

    private fun targetAt(entryOffset: Int, sourceLength: Int): String {
        synchronized(targetCache) {
            targetCache[entryOffset]?.let { return it }
        }
        val targetLength = buffer.getInt(entryOffset + 8)
        require(targetLength in 0..MAX_TARGET_BYTES)
        val targetOffset = entryOffset + ENTRY_HEADER_SIZE + sourceLength * Char.SIZE_BYTES
        val bytes = ByteArray(targetLength)
        repeat(targetLength) { index -> bytes[index] = buffer.get(targetOffset + index) }
        return cleanQuickDictionaryTarget(bytes.toString(Charsets.UTF_8)).also { target ->
            synchronized(targetCache) { targetCache[entryOffset] = target }
        }
    }

    companion object {
        private const val HEADER_SIZE = 32
        private const val ENTRY_HEADER_SIZE = 12
        private const val FORMAT_VERSION = 1
        private const val FNV_OFFSET_BASIS = -2128831035
        private const val FNV_PRIME = 16777619
        private const val MAX_TARGET_BYTES = 1 shl 20
        private const val MAX_SOURCE_CHARS = 256
        private val MAGIC = "QTDCT001".toByteArray(Charsets.US_ASCII)

        fun openOrNull(file: File): MappedDictionaryIndex? = runCatching {
            val mapped = FileInputStream(file).channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            }.order(ByteOrder.LITTLE_ENDIAN)
            require(mapped.limit() >= HEADER_SIZE)
            MAGIC.forEachIndexed { index, byte -> require(mapped.get(index) == byte) }
            require(mapped.getInt(8) == FORMAT_VERSION)
            val bucketCount = mapped.getInt(12)
            val entryCount = mapped.getInt(16)
            val maxSourceChars = mapped.getInt(20)
            val blobOffset = mapped.getInt(24)
            require(bucketCount > 0 && bucketCount.countOneBits() == 1)
            require(entryCount in 1 until bucketCount)
            require(maxSourceChars in 1..MAX_SOURCE_CHARS)
            require(blobOffset == HEADER_SIZE + bucketCount * Int.SIZE_BYTES)
            MappedDictionaryIndex(mapped, bucketCount, maxSourceChars)
        }.getOrNull()
    }
}

private class DictionaryReader(
    val reader: BufferedReader,
    private val counter: CountingInputStream,
) : AutoCloseable {
    val bytesRead: Long
        get() = counter.bytesRead

    override fun close() = reader.close()
}

private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
    var bytesRead: Long = 0
        private set

    override fun read(): Int = super.read().also { if (it >= 0) bytesRead++ }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return super.read(buffer, offset, length).also { count ->
            if (count > 0) bytesRead += count
        }
    }
}

private fun openDictionaryReader(file: File): DictionaryReader {
    val counter = CountingInputStream(FileInputStream(file))
    val input = PushbackInputStream(counter, 3)
    val bom = ByteArray(3)
    val count = input.read(bom)
    val charset: Charset
    val consumed: Int
    when {
        count >= 3 &&
            bom[0] == 0xEF.toByte() &&
            bom[1] == 0xBB.toByte() &&
            bom[2] == 0xBF.toByte() -> {
            charset = Charsets.UTF_8
            consumed = 3
        }
        count >= 2 && bom[0] == 0xFF.toByte() && bom[1] == 0xFE.toByte() -> {
            charset = Charsets.UTF_16LE
            consumed = 2
        }
        count >= 2 && bom[0] == 0xFE.toByte() && bom[1] == 0xFF.toByte() -> {
            charset = Charsets.UTF_16BE
            consumed = 2
        }
        else -> {
            charset = Charsets.UTF_8
            consumed = 0
        }
    }
    if (count > consumed) input.unread(bom, consumed, count - consumed)
    return DictionaryReader(
        reader = InputStreamReader(
            input,
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT),
        ).buffered(64 * 1024),
        counter = counter,
    )
}

private fun RandomAccessFile.writeIntLe(value: Int) {
    write(value)
    write(value ushr 8)
    write(value ushr 16)
    write(value ushr 24)
}

private fun RandomAccessFile.writeShortLe(value: Int) {
    write(value)
    write(value ushr 8)
}

private fun RandomAccessFile.readIntLe(): Int {
    return readUnsignedByte() or
        (readUnsignedByte() shl 8) or
        (readUnsignedByte() shl 16) or
        (readUnsignedByte() shl 24)
}

private fun RandomAccessFile.readUnsignedShortLe(): Int {
    return readUnsignedByte() or (readUnsignedByte() shl 8)
}
