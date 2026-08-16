package io.legado.app.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.domain.model.AuthoringChapter
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.AuthoringProjectKind
import io.legado.app.domain.model.blockPlainText
import io.legado.app.domain.model.resolveEbookDocument
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranslationAuthoringIntegrationTest {

    @Test
    fun finalizedChapterContentMaterializesIntoEbookBlocksWithoutMutation() {
        val finalTranslation = "Chương một\n\nNội dung đã chốt."
        val project = AuthoringProject(
            id = "project-1",
            kind = AuthoringProjectKind.EBOOK_EDITOR,
            title = "Bản dịch",
            chapters = listOf(AuthoringChapter("chapter-1", "Chương 1", finalTranslation, 1, 1)),
            createdAt = 1,
            updatedAt = 1,
        )

        val document = project.resolveEbookDocument()

        assertEquals(finalTranslation, project.chapters.single().content)
        assertEquals(
            "Chương một\nNội dung đã chốt.",
            document.chapters.single().blocks.joinToString("\n", transform = ::blockPlainText),
        )
    }
}
