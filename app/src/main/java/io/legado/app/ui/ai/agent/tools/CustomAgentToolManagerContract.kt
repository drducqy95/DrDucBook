package io.legado.app.ui.ai.agent.tools

import androidx.compose.runtime.Stable
import io.legado.app.domain.agenttools.CustomAgentToolLifecycleState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class CustomAgentToolManagerUiState(
    val loading: Boolean = true,
    val tools: ImmutableList<CustomAgentToolUi> = persistentListOf(),
    val selectedToolId: String? = null,
    val editorVisible: Boolean = false,
    val manifestJson: String = "",
    val fixtureArgumentsJson: String = "{}",
    val pendingDelete: CustomAgentToolUi? = null,
    val busyToolIds: ImmutableList<String> = persistentListOf(),
    val errorMessage: String? = null,
) {
    val selectedTool: CustomAgentToolUi?
        get() = tools.firstOrNull { it.id == selectedToolId }
}

@Stable
data class CustomAgentToolUi(
    val id: String,
    val toolName: String,
    val name: String,
    val description: String,
    val enabled: Boolean,
    val activeVersionId: String?,
    val activeVersion: String?,
    val latestVersionId: String?,
    val latestVersion: String?,
    val latestLifecycle: CustomAgentToolLifecycleState,
    val latestValidationStatus: String,
    val latestValidationMessage: String,
    val latestTestStatus: String,
    val latestTestMessage: String,
    val latestTestOutputJson: String?,
    val fixtureArgumentsJson: String,
    val versionCount: Int,
    val canValidate: Boolean,
    val canRunFixture: Boolean,
    val canApprove: Boolean,
    val canEnable: Boolean,
    val canDisable: Boolean,
    val canRollback: Boolean,
)

sealed interface CustomAgentToolManagerIntent {
    data object Refresh : CustomAgentToolManagerIntent
    data object NewDraft : CustomAgentToolManagerIntent
    data class EditLatest(val toolId: String) : CustomAgentToolManagerIntent
    data object DismissEditor : CustomAgentToolManagerIntent
    data class UpdateManifest(val value: String) : CustomAgentToolManagerIntent
    data class UpdateFixtureArguments(val value: String) : CustomAgentToolManagerIntent
    data object SaveDraft : CustomAgentToolManagerIntent
    data class ValidateLatest(val toolId: String) : CustomAgentToolManagerIntent
    data class RunFixture(val toolId: String) : CustomAgentToolManagerIntent
    data class ApproveLatest(val toolId: String) : CustomAgentToolManagerIntent
    data class SetEnabled(val toolId: String, val enabled: Boolean) : CustomAgentToolManagerIntent
    data class Rollback(val toolId: String) : CustomAgentToolManagerIntent
    data class RequestDelete(val toolId: String) : CustomAgentToolManagerIntent
    data object ConfirmDelete : CustomAgentToolManagerIntent
    data object DismissDelete : CustomAgentToolManagerIntent
}

sealed interface CustomAgentToolManagerEffect {
    data class ShowMessage(val message: String) : CustomAgentToolManagerEffect
}
