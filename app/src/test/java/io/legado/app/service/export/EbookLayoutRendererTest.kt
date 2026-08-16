package io.legado.app.service.export

import io.legado.app.domain.model.AuthoringStyle
import io.legado.app.domain.model.EbookBlockGeometry
import io.legado.app.domain.model.EbookDocument
import io.legado.app.domain.model.EbookDocumentChapter
import io.legado.app.domain.model.EbookLayoutMode
import io.legado.app.domain.model.EbookPageSize
import io.legado.app.domain.model.EbookParagraphBlock
import org.junit.Assert.assertTrue
import org.junit.Test

class EbookLayoutRendererTest {

    @Test
    fun fixedLayoutOffsetsLaterPagesAndAppliesDropCapAfterOpeningQuote() {
        val document = EbookDocument(
            layoutMode = EbookLayoutMode.FIXED_PAGE,
            pageSize = EbookPageSize(100f, 200f),
            chapters = listOf(
                EbookDocumentChapter(
                    id = "chapter",
                    title = "One",
                    blocks = listOf(
                        EbookParagraphBlock(
                            id = "quoted",
                            text = "\"Alpha",
                            geometry = EbookBlockGeometry(y = 10f, page = 1),
                        )
                    ),
                )
            ),
        )

        val rendered = EbookLayoutRenderer.render(document, AuthoringStyle(dropCap = true))

        assertTrue(rendered.chapters.single().html.contains("class=\"legado-dropcap\""))
        assertTrue(rendered.chapters.single().html.contains("top:210.0px"))
        assertTrue(rendered.css.contains("height:400.0px"))
    }
}
