package io.legado.app.ui.widget.components.book

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchBookItemTextTest {

    @Test
    fun introDisplayCollapsesWhitespaceWithoutDeletingWordBoundaries() {
        assertEquals(
            "Là sở thay huyền trường gần nội thay vào tù",
            "Là sở thay\nhuyền trường   gần\tnội thay vào tù"
                .normalizeSearchBookIntroForDisplay(),
        )
    }
}
