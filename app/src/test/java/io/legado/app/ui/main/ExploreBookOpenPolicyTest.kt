package io.legado.app.ui.main

import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.book.isVideo
import io.legado.app.help.media.MediaChapterPolicy
import io.legado.app.help.book.normalizeTypeFromSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreBookOpenPolicyTest {

    @Test
    fun onlineTextBookUsesInProcessTextReader() {
        val book = Book(type = BookType.text, bookUrl = "https://example.test/book")

        assertEquals(
            ExploreBookOpenMode.TEXT_READER,
            resolveExploreBookOpenMode(book, showMangaUi = true),
        )
    }

    @Test
    fun videoUsesMediaPlayerAndAudioKeepsLegacyReader() {
        assertEquals(
            ExploreBookOpenMode.VIDEO_PLAYER,
            resolveExploreBookOpenMode(Book(type = BookType.video), showMangaUi = true),
        )
        assertEquals(
            ExploreBookOpenMode.LEGACY_READER,
            resolveExploreBookOpenMode(Book(type = BookType.audio), showMangaUi = true),
        )
    }

    @Test
    fun sourceTypeNormalizationRepairsCachedVideoSearchBookType() {
        val book = Book(type = BookType.text or BookType.notShelf, origin = "video-source")
        val source = BookSource(
            bookSourceUrl = "video-source",
            bookSourceType = BookSourceType.video,
        )

        book.normalizeTypeFromSource(source)

        assertEquals(
            ExploreBookOpenMode.VIDEO_PLAYER,
            resolveExploreBookOpenMode(book, showMangaUi = true),
        )
        assertTrue(book.isNotShelf)
    }

    @Test
    fun onlineMangaUsesLegacyReaderOnlyWhenMangaUiIsEnabled() {
        val book = Book(type = BookType.image)

        assertEquals(
            ExploreBookOpenMode.LEGACY_READER,
            resolveExploreBookOpenMode(book, showMangaUi = true),
        )
        assertEquals(
            ExploreBookOpenMode.TEXT_READER,
            resolveExploreBookOpenMode(book, showMangaUi = false),
        )
    }

    @Test
    fun readBookRouteRedirectsTextBookWithHlsChapterToMediaPlayer() {
        val book = Book(type = BookType.text or BookType.notShelf)
        val chapters = listOf(
            BookChapter(url = "https://cdn.example.test/movie/index.m3u8")
        )

        assertTrue(shouldRedirectReadBookToMediaPlayer(book, chapters))
        assertTrue(MediaChapterPolicy.normalizeVideoBookType(book, chapters))
        assertTrue(book.isVideo)
        assertTrue(book.isNotShelf)
    }
}
