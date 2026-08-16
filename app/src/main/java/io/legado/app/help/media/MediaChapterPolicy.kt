package io.legado.app.help.media

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isVideo

object MediaChapterPolicy {

    fun hasVideoChapter(chapters: List<BookChapter>): Boolean {
        return chapters.any(::isVideoChapter)
    }

    fun isVideoChapter(chapter: BookChapter): Boolean {
        return isVideoUri(chapter.url) || isVideoUri(chapter.resourceUrl)
    }

    fun isVideoUri(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val normalized = value.trim()
        return VIDEO_URI_PATTERN.containsMatchIn(normalized)
    }

    fun normalizeVideoBookType(book: Book, chapters: List<BookChapter>): Boolean {
        if (book.isVideo || book.isLocal || book.isAudio || book.isImage) return false
        if (!hasVideoChapter(chapters)) return false
        val previousType = book.type
        val preservedFlags = previousType and BookType.allBookType.inv()
        book.type = preservedFlags or BookType.video
        return book.type != previousType
    }

    private val VIDEO_URI_PATTERN = Regex(
        pattern = """(?i)\.(m3u8|mpd|mp4|m4v|mov|webm|mkv)(?:[?#].*)?$"""
    )
}
