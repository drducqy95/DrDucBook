package io.legado.app.ui.authoring.writing

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AuthoringProject
import io.legado.app.domain.model.PreWritingSectionKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class WritingUiState(
    val projects: ImmutableList<AuthoringProject> = persistentListOf(),
    val project: AuthoringProject? = null,
    val searchQuery: String = "",
    val selectedChapterId: String? = null,
    val chapterTitle: String = "",
    val chapterContent: String = "",
    val replaceQuery: String = "",
    val replaceWith: String = "",
    val searchResultCount: Int = 0,
    val aiInstruction: String = "",
    val aiSuggestion: String = "",
    val isLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val isDirty: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val autosaveState: WritingAutosaveState = WritingAutosaveState.IDLE,
    val dialog: WritingDialog? = null,
    val workspaceMode: WritingWorkspaceMode = WritingWorkspaceMode.MANUSCRIPT,
    val selectedPreWritingSection: PreWritingSectionKey = PreWritingSectionKey.PREMISE,
    val preWritingContent: String = "",
)

enum class WritingWorkspaceMode { PREWRITING, OUTLINE, MANUSCRIPT, VALIDATE }

enum class WritingAutosaveState { IDLE, PENDING, SAVING, SAVED, ERROR }

sealed interface WritingDialog {
    data object CreateProject : WritingDialog
    data class DeleteProject(val projectId: String, val title: String) : WritingDialog
    data class DeleteChapter(val chapterId: String, val title: String) : WritingDialog
    data class UnsavedChanges(val action: WritingPendingAction) : WritingDialog
}

sealed interface WritingPendingAction {
    data object ExitModule : WritingPendingAction
    data object CloseProject : WritingPendingAction
    data object CreateProject : WritingPendingAction
    data class OpenProject(val projectId: String) : WritingPendingAction
}

sealed interface WritingIntent {
    data object BackPressed : WritingIntent
    data object CloseProject : WritingIntent
    data class UpdateSearchQuery(val value: String) : WritingIntent
    data class OpenProject(val projectId: String) : WritingIntent
    data object ShowCreateProject : WritingIntent
    data class CreateProject(val title: String) : WritingIntent
    data object DismissDialog : WritingIntent
    data object RequestDeleteProject : WritingIntent
    data object ConfirmDeleteProject : WritingIntent
    data object DuplicateProject : WritingIntent
    data class UpdateProjectTitle(val value: String) : WritingIntent
    data class UpdateProjectAuthor(val value: String) : WritingIntent
    data class UpdateProjectDescription(val value: String) : WritingIntent
    data class SelectChapter(val chapterId: String) : WritingIntent
    data object AddChapter : WritingIntent
    data object DuplicateChapter : WritingIntent
    data object RequestDeleteChapter : WritingIntent
    data object ConfirmDeleteChapter : WritingIntent
    data class MoveChapter(val direction: Int) : WritingIntent
    data class UpdateChapterTitle(val value: String) : WritingIntent
    data class UpdateChapterContent(val value: String) : WritingIntent
    data class UpdateReplaceQuery(val value: String) : WritingIntent
    data class UpdateReplaceWith(val value: String) : WritingIntent
    data object ReplaceNext : WritingIntent
    data object ReplaceAll : WritingIntent
    data object Undo : WritingIntent
    data object Redo : WritingIntent
    data object RequestImage : WritingIntent
    data class ImagePicked(val displayName: String, val bytes: ByteArray) : WritingIntent
    data class SelectWorkspaceMode(val value: WritingWorkspaceMode) : WritingIntent
    data class SelectPreWritingSection(val value: PreWritingSectionKey) : WritingIntent
    data class UpdatePreWritingContent(val value: String) : WritingIntent
    data class UpdateAiInstruction(val value: String) : WritingIntent
    data object GenerateStoryBlueprint : WritingIntent
    data object ApproveStoryBlueprint : WritingIntent
    data object GenerateNarrativePlan : WritingIntent
    data object ApproveNarrativePlan : WritingIntent
    data object Save : WritingIntent
    data object FlushAutosave : WritingIntent
    data object GenerateWithAi : WritingIntent
    data object ApplyAiSuggestion : WritingIntent
    data object DismissAiSuggestion : WritingIntent
    data object SaveAndContinue : WritingIntent
    data object DiscardAndContinue : WritingIntent
}

sealed interface WritingEffect {
    data class ShowMessage(val message: String) : WritingEffect
    data object OpenImagePicker : WritingEffect
    data object NavigateBack : WritingEffect
}
