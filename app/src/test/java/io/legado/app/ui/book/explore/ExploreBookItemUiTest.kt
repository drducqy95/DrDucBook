package io.legado.app.ui.book.explore

import io.legado.app.data.entities.SearchBook
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ExploreBookItemUiTest {

    @Test
    fun translatedMetadataChangesUiItemEqualityForTheSameBookUrl() {
        val source = SearchBook(bookUrl = "https://example.test/book", name = "地下车库")
        val translated = source.copy(name = "Bãi đỗ xe ngầm")

        assertNotEquals(
            ExploreBookItemUi(book = source, displayBook = source),
            ExploreBookItemUi(book = source, displayBook = translated),
        )
    }
}
