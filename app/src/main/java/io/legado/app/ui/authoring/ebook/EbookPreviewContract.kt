package io.legado.app.ui.authoring.ebook

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.service.export.RenderedEbook

enum class EbookPreviewViewport { PHONE, TABLET, PAGE }

@Stable
data class EbookPreviewUiState(
    val project: AuthoringProject? = null,
    val rendered: RenderedEbook? = null,
    val selectedChapterId: String? = null,
    val viewport: EbookPreviewViewport = EbookPreviewViewport.PHONE,
    val fontScale: Float = 1f,
    val darkMode: Boolean = false,
    val loading: Boolean = true,
    val errorMessage: String? = null,
)

sealed interface EbookPreviewIntent {
    data class Load(val projectId: String) : EbookPreviewIntent
    data class SelectChapter(val chapterId: String) : EbookPreviewIntent
    data class SetViewport(val value: EbookPreviewViewport) : EbookPreviewIntent
    data class SetFontScale(val value: Float) : EbookPreviewIntent
    data object ToggleDarkMode : EbookPreviewIntent
}
