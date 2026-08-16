package io.legado.app.ui.book.cache.manage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookCacheManageVisibilityTest {

    @Test
    fun onlineBookWithUncachedChaptersRemainsVisible() {
        val uncachedBook = item(totalCount = 12)

        assertTrue(uncachedBook.isVisibleInCacheManager)
        assertFalse(item(totalCount = 0).isVisibleInCacheManager)
    }

    private fun item(totalCount: Int) = BookCacheBookItem(
        bookUrl = "book",
        name = "Book",
        author = "Author",
        totalCount = totalCount,
        cachedCount = 0,
        cachedFileCount = 0,
        waitingCount = 0,
        downloadingCount = 0,
        pausedCount = 0,
        errorCount = 0,
        isNotShelf = false,
        group = 0L,
    )
}
