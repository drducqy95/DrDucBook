package io.legado.app.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.room.withTransaction
import io.legado.app.constant.BookType
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.gateway.AudiobookImportGateway
import io.legado.app.domain.model.AudiobookCreateRequest
import io.legado.app.domain.model.AudiobookImportPreview
import io.legado.app.domain.model.AudiobookTrackCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class AudiobookImportRepository(
    private val context: Context,
    private val database: AppDatabase,
) : AudiobookImportGateway {

    override suspend fun scanFiles(uris: List<Uri>): AudiobookImportPreview = withContext(Dispatchers.IO) {
        val documents = uris.distinct()
        val byName = documents.associateBy { displayName(it).lowercase() }
        buildPreview(
            uris = expandPlaylists(documents, byName),
            cueTracks = parseCueTracks(documents, byName),
        )
    }

    override suspend fun scanTree(treeUri: Uri): AudiobookImportPreview = withContext(Dispatchers.IO) {
        val documents = collectTreeDocuments(treeUri)
        val byName = documents.associateBy { displayName(it).lowercase() }
        buildPreview(
            uris = expandPlaylists(documents, byName),
            cueTracks = parseCueTracks(documents, byName),
        )
    }

    override suspend fun createBook(request: AudiobookCreateRequest): String = withContext(Dispatchers.IO) {
        val tracks = request.tracks.filter(AudiobookTrackCandidate::selected)
            .sortedWith(compareBy<AudiobookTrackCandidate> { it.trackNumber.takeIf { number -> number > 0 } ?: Int.MAX_VALUE }
                .thenBy { it.title })
        require(request.title.isNotBlank()) { "Tên audiobook không được để trống" }
        require(tracks.isNotEmpty()) { "Chưa chọn tệp audio để nhập" }
        require(database.bookChapterDao.countImportedAudiobookTracks(tracks.map(AudiobookTrackCandidate::uri)) == 0) {
            "Một hoặc nhiều tệp audio đã được nhập vào audiobook khác"
        }
        val bookUrl = "audiobook://${UUID.randomUUID()}"
        val coverPath = extractCover(tracks.first().uri, bookUrl)
        val book = Book(
            bookUrl = bookUrl,
            tocUrl = bookUrl,
            origin = BookType.localTag,
            originName = "Audiobook",
            name = request.title.trim(),
            author = request.author.trim(),
            coverUrl = coverPath,
            type = BookType.audio or BookType.local,
            totalChapterNum = tracks.size,
            latestChapterTitle = tracks.last().title,
            canUpdate = false,
        )
        val chapters = tracks.mapIndexed { index, track ->
            BookChapter(
                url = track.uri,
                title = track.title.ifBlank { "Track ${index + 1}" },
                baseUrl = track.uri,
                bookUrl = bookUrl,
                index = index,
                resourceUrl = track.uri,
                start = track.startMs,
                end = track.endMs,
            )
        }
        database.withTransaction {
            database.bookDao.insert(book)
            database.bookChapterDao.insert(*chapters.toTypedArray())
        }
        bookUrl
    }

    private fun buildPreview(
        uris: List<Uri>,
        cueTracks: List<AudiobookTrackCandidate>,
    ): AudiobookImportPreview {
        val cueSourceUris = cueTracks.map(AudiobookTrackCandidate::uri).toSet()
        val regularTracks = uris.filter(::isAudioDocument).mapIndexedNotNull { index, uri ->
            readTrack(uri, index + 1)
        }.filterNot { it.uri in cueSourceUris }
        val tracks = regularTracks + cueTracks
        require(tracks.isNotEmpty()) { "Không tìm thấy tệp audio được hỗ trợ" }
        val album = tracks.map(AudiobookTrackCandidate::album).firstOrNull(String::isNotBlank)
        val folderName = tracks.first().title.substringBeforeLast('.', "Audiobook")
        return AudiobookImportPreview(
            title = album ?: folderName,
            author = tracks.map(AudiobookTrackCandidate::artist).firstOrNull(String::isNotBlank).orEmpty(),
            tracks = tracks.sortedWith(
                compareBy<AudiobookTrackCandidate> { it.trackNumber.takeIf { number -> number > 0 } ?: Int.MAX_VALUE }
                    .thenBy { it.title }
            ),
        )
    }

    private fun readTrack(uri: Uri, fallbackTrack: Int): AudiobookTrackCandidate? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val fileName = displayName(uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf(String::isNotBlank)
                ?: fileName.substringBeforeLast('.')
            val track = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')
                ?.toIntOrNull()
                ?: naturalTrackNumber(fileName)
                ?: fallbackTrack
            AudiobookTrackCandidate(
                id = stableId(uri.toString()),
                uri = uri.toString(),
                title = title,
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),
                trackNumber = track,
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank { mimeFromName(fileName) },
            )
        } finally {
            retriever.release()
        }
    }.getOrNull()

    private fun collectTreeDocuments(treeUri: Uri): List<Uri> {
        val result = mutableListOf<Uri>()
        fun walk(parentDocumentId: String) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIndex)
                    val mime = cursor.getString(mimeIndex)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        walk(id)
                    } else {
                        result += DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    }
                }
            }
        }
        walk(DocumentsContract.getTreeDocumentId(treeUri))
        return result
    }

    private fun expandPlaylists(
        uris: List<Uri>,
        byName: Map<String, Uri> = emptyMap(),
    ): List<Uri> {
        val audio = uris.filter(::isAudioDocument).toMutableList()
        uris.filter { displayName(it).lowercase().endsWithAny(".m3u", ".m3u8") }
            .forEach { playlist ->
                context.contentResolver.openInputStream(playlist)?.bufferedReader()?.useLines { lines ->
                    lines.map(String::trim)
                        .filter { it.isNotBlank() && !it.startsWith('#') }
                        .map { line ->
                            val reference = if (line.startsWith("FILE ", ignoreCase = true)) {
                                Regex("FILE\\s+\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                                    .find(line)?.groupValues?.getOrNull(1)
                            } else line
                            reference?.let { value ->
                                runCatching { Uri.parse(value).takeIf { it.scheme != null } }.getOrNull()
                                    ?: byName[File(value).name.lowercase()]
                            }
                        }
                        .filterNotNull()
                        .forEach(audio::add)
                }
            }
        return audio.distinct()
    }

    private fun parseCueTracks(
        documents: List<Uri>,
        byName: Map<String, Uri>,
    ): List<AudiobookTrackCandidate> {
        val result = mutableListOf<AudiobookTrackCandidate>()
        documents.filter { displayName(it).lowercase().endsWith(".cue") }.forEach { cueUri ->
            val lines = context.contentResolver.openInputStream(cueUri)
                ?.bufferedReader()
                ?.use { it.readLines() }
                .orEmpty()
            var source: Uri? = null
            var title = ""
            var artist = ""
            var trackNumber = 0
            val parsed = mutableListOf<AudiobookTrackCandidate>()
            lines.map(String::trim).forEach { line ->
                when {
                    line.startsWith("FILE ", ignoreCase = true) -> {
                        val value = Regex("FILE\\s+\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                            .find(line)?.groupValues?.getOrNull(1)
                        source = value?.let { reference ->
                            runCatching { Uri.parse(reference).takeIf { it.scheme != null } }.getOrNull()
                                ?: byName[File(reference).name.lowercase()]
                        }
                    }
                    line.startsWith("TRACK ", ignoreCase = true) -> {
                        trackNumber = line.split(Regex("\\s+")).getOrNull(1)?.toIntOrNull()
                            ?: (parsed.size + 1)
                        title = "Track $trackNumber"
                        artist = ""
                    }
                    line.startsWith("TITLE ", ignoreCase = true) -> {
                        title = line.substringAfter(' ').trim().trim('"')
                    }
                    line.startsWith("PERFORMER ", ignoreCase = true) -> {
                        artist = line.substringAfter(' ').trim().trim('"')
                    }
                    line.startsWith("INDEX 01 ", ignoreCase = true) -> {
                        val trackUri = source ?: return@forEach
                        val startMs = cueTimeToMs(line.substringAfterLast(' ')) ?: return@forEach
                        val base = readTrack(trackUri, trackNumber) ?: return@forEach
                        parsed += base.copy(
                            id = stableId("${trackUri}#$startMs"),
                            title = title.ifBlank { base.title },
                            artist = artist.ifBlank { base.artist },
                            trackNumber = trackNumber,
                            startMs = startMs,
                        )
                    }
                }
            }
            parsed.forEachIndexed { index, track ->
                val next = parsed.getOrNull(index + 1)?.takeIf { it.uri == track.uri }?.startMs
                val end = next ?: track.durationMs.takeIf { it > 0L }
                    ?.let { duration -> (track.startMs ?: 0L) + duration }
                result += track.copy(
                    endMs = end,
                    durationMs = end?.let { (it - (track.startMs ?: 0L)).coerceAtLeast(0L) }
                        ?: track.durationMs,
                )
            }
        }
        return result
    }

    private fun cueTimeToMs(value: String): Long? {
        val parts = value.split(':')
        if (parts.size != 3) return null
        val minutes = parts[0].toLongOrNull() ?: return null
        val seconds = parts[1].toLongOrNull() ?: return null
        val frames = parts[2].toLongOrNull() ?: return null
        return (minutes * 60L + seconds) * 1_000L + frames * 1_000L / 75L
    }

    private fun isAudioDocument(uri: Uri): Boolean {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val name = displayName(uri).lowercase()
        return mime.startsWith("audio/") || name.endsWithAny(
            ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".flac", ".webm"
        )
    }

    private fun displayName(uri: Uri): String = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
    }.orEmpty().ifBlank { uri.lastPathSegment.orEmpty() }

    private fun extractCover(uriString: String, bookUrl: String): String? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(uriString))
            val bytes = retriever.embeddedPicture ?: return@runCatching null
            val cover = File(context.filesDir, "audiobook_covers/${stableId(bookUrl)}.jpg")
            cover.parentFile?.mkdirs()
            cover.writeBytes(bytes)
            cover.absolutePath
        } finally {
            retriever.release()
        }
    }.getOrNull()

    private fun naturalTrackNumber(name: String): Int? = Regex("(?:^|\\D)(\\d{1,4})(?:\\D|$)")
        .find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun mimeFromName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "webm" -> "audio/webm"
        else -> "audio/*"
    }

    private fun String.endsWithAny(vararg suffixes: String): Boolean = suffixes.any(::endsWith)

    private fun stableId(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .take(12)
        .joinToString("") { "%02x".format(it) }
}
