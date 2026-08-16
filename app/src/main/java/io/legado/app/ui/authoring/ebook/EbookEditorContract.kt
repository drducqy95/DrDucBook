package io.legado.app.ui.authoring.ebook

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.EbookLayoutMode
import io.legado.app.domain.usecase.CloneBookCandidate
import io.legado.app.domain.usecase.CloneContentVariant
import io.legado.app.domain.usecase.EbookValidationIssue
import io.legado.app.service.export.EbookExportFormat
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Stable
data class EbookEditorUiState(
    val projects: ImmutableList<AuthoringProject> = persistentListOf(),
    val project: AuthoringProject? = null,
    val searchQuery: String = "",
    val selectedChapterId: String? = null,
    val selectedChapterIds: ImmutableSet<String> = persistentSetOf(),
    val chapterTitle: String = "",
    val chapterContent: String = "",
    val sourceBookUrl: String = "",
    val exportFormat: EbookExportFormat = EbookExportFormat.EPUB3,
    val isLoading: Boolean = false,
    val isDirty: Boolean = false,
    val dialog: EbookEditorDialog? = null,
    val sheet: EbookEditorSheet? = null,
    val selectedBlockId: String? = null,
    val selectedBlockIds: ImmutableSet<String> = persistentSetOf(),
    val autoRenumberChapters: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val validationIssues: ImmutableList<EbookValidationIssue> = persistentListOf(),
    val cloneCandidates: ImmutableList<CloneBookCandidate> = persistentListOf(),
    val cloneQuery: String = "",
    val cloneChapterScope: String = "",
    val cloneVariant: CloneContentVariant = CloneContentVariant.RAW,
    val cloneProvider: String = "",
    val cloneTargetLanguage: String = "vi",
    val aiInstruction: String = "",
    val aiSuggestion: String = "",
    val isGenerating: Boolean = false,
)

sealed interface EbookEditorDialog {
    data object CreateProject : EbookEditorDialog
    data class DeleteProject(val projectId: String, val title: String) : EbookEditorDialog
    data class DeleteChapter(val chapterId: String, val title: String) : EbookEditorDialog
    data class DeleteChapters(val chapterIds: List<String>, val label: String) : EbookEditorDialog
    data class RenameBlock(val blockId: String, val currentName: String) : EbookEditorDialog
    data object ConfirmLossyTextExport : EbookEditorDialog
    data class UnsavedChanges(val action: EbookEditorPendingAction) : EbookEditorDialog
}

sealed interface EbookEditorSheet {
    data object CloneDownloadedBook : EbookEditorSheet
    data object Layers : EbookEditorSheet
    data object ChapterManager : EbookEditorSheet
}

enum class EbookBlockKind { PARAGRAPH, HEADING, QUOTE, IMAGE, DIVIDER, PAGE_BREAK, CODE, LIST }

enum class EbookBlockAlignment { LEFT, HORIZONTAL_CENTER, RIGHT, TOP, VERTICAL_CENTER, BOTTOM }

enum class EbookDistributionAxis { HORIZONTAL, VERTICAL }

enum class EbookResizeHandle {
    TOP_LEFT,
    TOP,
    TOP_RIGHT,
    LEFT,
    RIGHT,
    BOTTOM_LEFT,
    BOTTOM,
    BOTTOM_RIGHT,
}

sealed interface EbookEditorPendingAction {
    data object ExitModule : EbookEditorPendingAction
    data object CloseProject : EbookEditorPendingAction
    data object CreateProject : EbookEditorPendingAction
    data class OpenProject(val projectId: String) : EbookEditorPendingAction
}

sealed interface EbookEditorIntent {
    data object BackPressed : EbookEditorIntent
    data object CloseProject : EbookEditorIntent
    data class UpdateSearchQuery(val value: String) : EbookEditorIntent
    data class OpenProject(val projectId: String) : EbookEditorIntent
    data object ShowCreateProject : EbookEditorIntent
    data class CreateProject(val title: String) : EbookEditorIntent
    data object DismissDialog : EbookEditorIntent
    data object RequestDeleteProject : EbookEditorIntent
    data object ConfirmDeleteProject : EbookEditorIntent
    data object ShowCloneDownloadedBook : EbookEditorIntent
    data object ShowLayers : EbookEditorIntent
    data object ShowChapterManager : EbookEditorIntent
    data object DismissSheet : EbookEditorIntent
    data class UpdateSourceBookUrl(val value: String) : EbookEditorIntent
    data class UpdateCloneQuery(val value: String) : EbookEditorIntent
    data class UpdateCloneChapterScope(val value: String) : EbookEditorIntent
    data class UpdateCloneVariant(val value: CloneContentVariant) : EbookEditorIntent
    data class UpdateCloneProvider(val value: String) : EbookEditorIntent
    data class UpdateCloneTargetLanguage(val value: String) : EbookEditorIntent
    data class SelectCloneCandidate(val bookUrl: String) : EbookEditorIntent
    data object CloneDownloadedBook : EbookEditorIntent
    data class UpdateTitle(val value: String) : EbookEditorIntent
    data class UpdateAuthor(val value: String) : EbookEditorIntent
    data class UpdateDescription(val value: String) : EbookEditorIntent
    data class UpdateLanguage(val value: String) : EbookEditorIntent
    data class SelectChapter(val chapterId: String) : EbookEditorIntent
    data class ToggleChapterSelection(val chapterId: String) : EbookEditorIntent
    data object AddChapter : EbookEditorIntent
    data object RequestDeleteChapter : EbookEditorIntent
    data object RequestDeleteSelectedChapters : EbookEditorIntent
    data object ConfirmDeleteChapter : EbookEditorIntent
    data class MoveChapter(val direction: Int) : EbookEditorIntent
    data class UpdateChapterTitle(val value: String) : EbookEditorIntent
    data class UpdateChapterSubtitle(val value: String) : EbookEditorIntent
    data class UpdateChapterPageBreakBefore(val value: Boolean) : EbookEditorIntent
    data class SetAutoRenumberChapters(val value: Boolean) : EbookEditorIntent
    data class UpdateChapterContent(val value: String) : EbookEditorIntent
    data class UpdateFontFamily(val value: String) : EbookEditorIntent
    data class UpdateFontSize(val value: Int) : EbookEditorIntent
    data class UpdateLineHeight(val value: Int) : EbookEditorIntent
    data class UpdateDropCap(val value: Boolean) : EbookEditorIntent
    data class SetLayoutMode(val value: EbookLayoutMode) : EbookEditorIntent
    data class InsertBlock(val kind: EbookBlockKind) : EbookEditorIntent
    data class SelectBlock(val blockId: String?) : EbookEditorIntent
    data class ToggleBlockSelection(val blockId: String) : EbookEditorIntent
    data class SelectNextBlock(val direction: Int) : EbookEditorIntent
    data class UpdateSelectedBlockText(val value: String) : EbookEditorIntent
    data class MoveSelectedBlock(val dx: Float, val dy: Float) : EbookEditorIntent
    data class ResizeSelectedBlock(val dw: Float, val dh: Float) : EbookEditorIntent
    data class ResizeSelectedBlockFromHandle(
        val handle: EbookResizeHandle,
        val dx: Float,
        val dy: Float,
    ) : EbookEditorIntent
    data class RotateSelectedBlock(val degrees: Float) : EbookEditorIntent
    data class AlignSelectedBlocks(val alignment: EbookBlockAlignment) : EbookEditorIntent
    data class DistributeSelectedBlocks(val axis: EbookDistributionAxis) : EbookEditorIntent
    data object DuplicateSelectedBlock : EbookEditorIntent
    data object DeleteSelectedBlock : EbookEditorIntent
    data object ToggleSelectedBlockLock : EbookEditorIntent
    data object ToggleSelectedBlockVisibility : EbookEditorIntent
    data class MoveSelectedBlockLayer(val direction: Int) : EbookEditorIntent
    data class MoveSelectedReadingOrder(val direction: Int) : EbookEditorIntent
    data class ShowRenameBlock(val blockId: String) : EbookEditorIntent
    data class RenameBlock(val blockId: String, val value: String) : EbookEditorIntent
    data object Undo : EbookEditorIntent
    data object Redo : EbookEditorIntent
    data object Validate : EbookEditorIntent
    data object Preview : EbookEditorIntent
    data object SplitChapter : EbookEditorIntent
    data object MergeWithNextChapter : EbookEditorIntent
    data class UpdateAiInstruction(val value: String) : EbookEditorIntent
    data object GenerateBlockWithAi : EbookEditorIntent
    data object ApplyAiSuggestion : EbookEditorIntent
    data object DismissAiSuggestion : EbookEditorIntent
    data object RequestImage : EbookEditorIntent
    data class ImagePicked(val displayName: String, val bytes: ByteArray) : EbookEditorIntent
    data class SelectExportFormat(val value: EbookExportFormat) : EbookEditorIntent
    data object Save : EbookEditorIntent
    data object Export : EbookEditorIntent
    data object ConfirmExport : EbookEditorIntent
    data object SaveAndContinue : EbookEditorIntent
    data object DiscardAndContinue : EbookEditorIntent
}

sealed interface EbookEditorEffect {
    data class ShowMessage(val message: String) : EbookEditorEffect
    data object OpenImagePicker : EbookEditorEffect
    data class ShareFile(val path: String, val mimeType: String) : EbookEditorEffect
    data object NavigateBack : EbookEditorEffect
    data class NavigatePreview(val projectId: String) : EbookEditorEffect
}
