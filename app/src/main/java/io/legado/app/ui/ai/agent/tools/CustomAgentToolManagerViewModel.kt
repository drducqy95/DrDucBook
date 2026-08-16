package io.legado.app.ui.ai.agent.tools

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drducbook.app.R
import io.legado.app.domain.agenttools.CustomAgentToolDraft
import io.legado.app.domain.agenttools.CustomAgentToolLifecycleState
import io.legado.app.domain.agenttools.CustomAgentToolSnapshot
import io.legado.app.domain.agenttools.CustomAgentToolTestStatus
import io.legado.app.domain.agenttools.CustomAgentToolValidationStatus
import io.legado.app.domain.gateway.CustomAgentToolGateway
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

class CustomAgentToolManagerViewModel(
    private val customAgentToolGateway: CustomAgentToolGateway,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CustomAgentToolManagerUiState(
            selectedToolId = savedStateHandle[KEY_SELECTED_TOOL_ID],
            editorVisible = savedStateHandle[KEY_EDITOR_VISIBLE] ?: false,
            manifestJson = savedStateHandle[KEY_MANIFEST_JSON] ?: DEFAULT_MANIFEST_JSON,
            fixtureArgumentsJson = savedStateHandle[KEY_FIXTURE_ARGUMENTS_JSON] ?: DEFAULT_FIXTURE_JSON,
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<CustomAgentToolManagerEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var cachedTools: List<CustomAgentToolSnapshot> = emptyList()

    init {
        viewModelScope.launch {
            customAgentToolGateway.observeTools()
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            errorMessage = error.message
                                ?: appCtx.getString(R.string.ai_custom_tool_load_failed),
                        )
                    }
                }
                .collect { tools ->
                    cachedTools = tools
                    _uiState.update { current ->
                        current.copy(
                            loading = false,
                            tools = tools.map(CustomAgentToolSnapshot::toUi).toImmutableList(),
                        )
                    }
                }
        }
    }

    fun onIntent(intent: CustomAgentToolManagerIntent) {
        when (intent) {
            CustomAgentToolManagerIntent.Refresh -> refresh()
            CustomAgentToolManagerIntent.NewDraft -> openNewDraft()
            is CustomAgentToolManagerIntent.EditLatest -> openLatestDraft(intent.toolId)
            CustomAgentToolManagerIntent.DismissEditor -> setEditorVisible(false)
            is CustomAgentToolManagerIntent.UpdateManifest -> updateManifest(intent.value)
            is CustomAgentToolManagerIntent.UpdateFixtureArguments -> updateFixture(intent.value)
            CustomAgentToolManagerIntent.SaveDraft -> saveDraft()
            is CustomAgentToolManagerIntent.ValidateLatest -> validateLatest(intent.toolId)
            is CustomAgentToolManagerIntent.RunFixture -> runFixture(intent.toolId)
            is CustomAgentToolManagerIntent.ApproveLatest -> approveLatest(intent.toolId)
            is CustomAgentToolManagerIntent.SetEnabled -> setEnabled(intent.toolId, intent.enabled)
            is CustomAgentToolManagerIntent.Rollback -> rollback(intent.toolId)
            is CustomAgentToolManagerIntent.RequestDelete -> requestDelete(intent.toolId)
            CustomAgentToolManagerIntent.ConfirmDelete -> confirmDelete()
            CustomAgentToolManagerIntent.DismissDelete -> _uiState.update { it.copy(pendingDelete = null) }
        }
    }

    private fun refresh() {
        _effects.tryEmit(CustomAgentToolManagerEffect.ShowMessage(appCtx.getString(R.string.refresh)))
    }

    private fun openNewDraft() {
        savedStateHandle[KEY_SELECTED_TOOL_ID] = null
        setEditorDraft(
            selectedToolId = null,
            manifestJson = DEFAULT_MANIFEST_JSON,
            fixtureArgumentsJson = DEFAULT_FIXTURE_JSON,
            visible = true,
        )
    }

    private fun openLatestDraft(toolId: String) {
        val latest = cachedTools.firstOrNull { it.id == toolId }?.latestVersion ?: return
        savedStateHandle[KEY_SELECTED_TOOL_ID] = toolId
        setEditorDraft(
            selectedToolId = toolId,
            manifestJson = latest.manifestJson,
            fixtureArgumentsJson = latest.fixtureArgumentsJson,
            visible = true,
        )
    }

    private fun setEditorVisible(visible: Boolean) {
        savedStateHandle[KEY_EDITOR_VISIBLE] = visible
        _uiState.update { it.copy(editorVisible = visible) }
    }

    private fun setEditorDraft(
        selectedToolId: String?,
        manifestJson: String,
        fixtureArgumentsJson: String,
        visible: Boolean,
    ) {
        savedStateHandle[KEY_SELECTED_TOOL_ID] = selectedToolId
        savedStateHandle[KEY_MANIFEST_JSON] = manifestJson
        savedStateHandle[KEY_FIXTURE_ARGUMENTS_JSON] = fixtureArgumentsJson
        savedStateHandle[KEY_EDITOR_VISIBLE] = visible
        _uiState.update {
            it.copy(
                selectedToolId = selectedToolId,
                manifestJson = manifestJson,
                fixtureArgumentsJson = fixtureArgumentsJson,
                editorVisible = visible,
            )
        }
    }

    private fun updateManifest(value: String) {
        savedStateHandle[KEY_MANIFEST_JSON] = value
        _uiState.update { it.copy(manifestJson = value) }
    }

    private fun updateFixture(value: String) {
        savedStateHandle[KEY_FIXTURE_ARGUMENTS_JSON] = value
        _uiState.update { it.copy(fixtureArgumentsJson = value) }
    }

    private fun saveDraft() {
        val state = _uiState.value
        viewModelScope.launch {
            runCatching {
                customAgentToolGateway.createDraft(
                    CustomAgentToolDraft(
                        manifestJson = state.manifestJson,
                        fixtureArgumentsJson = state.fixtureArgumentsJson,
                    )
                )
            }.onSuccess { snapshot ->
                savedStateHandle[KEY_SELECTED_TOOL_ID] = snapshot.id
                _uiState.update { it.copy(selectedToolId = snapshot.id) }
                _effects.tryEmit(
                    CustomAgentToolManagerEffect.ShowMessage(
                        appCtx.getString(R.string.ai_custom_tool_draft_saved),
                    )
                )
            }.onFailure { showError(it) }
        }
    }

    private fun validateLatest(toolId: String) {
        runToolAction(toolId, R.string.ai_custom_tool_validated) {
            customAgentToolGateway.validateLatestDraft(toolId)
        }
    }

    private fun runFixture(toolId: String) {
        runToolAction(toolId, R.string.ai_custom_tool_fixture_passed) {
            val result = customAgentToolGateway.runFixture(toolId)
            if (result.status != CustomAgentToolTestStatus.PASS) {
                error(result.message.ifBlank { appCtx.getString(R.string.ai_custom_tool_fixture_failed) })
            }
        }
    }

    private fun approveLatest(toolId: String) {
        runToolAction(toolId, R.string.ai_custom_tool_approved) {
            customAgentToolGateway.approveLatestVersion(toolId)
        }
    }

    private fun setEnabled(toolId: String, enabled: Boolean) {
        runToolAction(
            toolId = toolId,
            successMessage = if (enabled) {
                R.string.ai_custom_tool_enabled
            } else {
                R.string.ai_custom_tool_disabled
            },
        ) {
            customAgentToolGateway.setEnabled(toolId, enabled)
        }
    }

    private fun rollback(toolId: String) {
        runToolAction(toolId, R.string.ai_custom_tool_rolled_back) {
            customAgentToolGateway.rollback(toolId)
        }
    }

    private fun requestDelete(toolId: String) {
        _uiState.update { state ->
            state.copy(pendingDelete = state.tools.firstOrNull { it.id == toolId })
        }
    }

    private fun confirmDelete() {
        val target = _uiState.value.pendingDelete ?: return
        _uiState.update { it.copy(pendingDelete = null) }
        runToolAction(target.id, R.string.ai_custom_tool_deleted) {
            customAgentToolGateway.delete(target.id)
        }
    }

    private fun runToolAction(
        toolId: String,
        successMessage: Int,
        action: suspend () -> Unit,
    ) {
        if (toolId in _uiState.value.busyToolIds) return
        _uiState.update { state ->
            state.copy(busyToolIds = (state.busyToolIds + toolId).toImmutableList())
        }
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess {
                    _effects.tryEmit(
                        CustomAgentToolManagerEffect.ShowMessage(appCtx.getString(successMessage))
                    )
                }
                .onFailure { showError(it) }
            _uiState.update { state ->
                state.copy(busyToolIds = state.busyToolIds.filterNot { it == toolId }.toImmutableList())
            }
        }
    }

    private fun showError(error: Throwable) {
        _effects.tryEmit(
            CustomAgentToolManagerEffect.ShowMessage(
                error.message ?: appCtx.getString(R.string.ai_custom_tool_action_failed),
            )
        )
    }

    private companion object {
        private const val KEY_EDITOR_VISIBLE = "custom_tool_editor_visible"
        private const val KEY_SELECTED_TOOL_ID = "custom_tool_selected_id"
        private const val KEY_MANIFEST_JSON = "custom_tool_manifest_json"
        private const val KEY_FIXTURE_ARGUMENTS_JSON = "custom_tool_fixture_json"
        private const val DEFAULT_FIXTURE_JSON = """{"value":"demo"}"""
        private val DEFAULT_MANIFEST_JSON = """
            {
              "schemaVersion": 1,
              "id": "custom_echo_tool",
              "name": "Echo tool",
              "description": "Return the value field from input.",
              "version": "1.0.0",
              "inputSchema": {
                "type": "object",
                "properties": {
                  "value": { "type": "string", "description": "Value to echo" }
                },
                "required": ["value"],
                "additionalProperties": false
              },
              "outputSchema": {
                "type": "object",
                "properties": {
                  "value": { "type": "string", "description": "Echoed value" }
                },
                "required": ["value"],
                "additionalProperties": false
              },
              "capabilities": ["READ"],
              "allowedDomains": [],
              "timeoutMs": 1000,
              "maxOutputChars": 2000,
              "script": "function execute(input, context) {\n  return { value: input.value };\n}"
            }
        """.trimIndent()
    }
}

private fun CustomAgentToolSnapshot.toUi(): CustomAgentToolUi {
    val latest = latestVersion
    val active = activeVersion
    val ordered = versions.sortedByDescending { it.createdAt }
    val activeIndex = ordered.indexOfFirst { it.id == activeVersionId }
    val latestLifecycle = latest?.lifecycleState ?: CustomAgentToolLifecycleState.DRAFT
    return CustomAgentToolUi(
        id = id,
        toolName = toolName,
        name = name,
        description = description,
        enabled = enabled,
        activeVersionId = activeVersionId,
        activeVersion = active?.version,
        latestVersionId = latest?.id,
        latestVersion = latest?.version,
        latestLifecycle = latestLifecycle,
        latestValidationStatus = latest?.validationStatus.orEmpty(),
        latestValidationMessage = latest?.validationMessage.orEmpty(),
        latestTestStatus = latest?.testStatus.orEmpty(),
        latestTestMessage = latest?.testMessage.orEmpty(),
        latestTestOutputJson = latest?.testOutputJson,
        fixtureArgumentsJson = latest?.fixtureArgumentsJson ?: "{}",
        versionCount = versions.size,
        canValidate = latest != null && latestLifecycle == CustomAgentToolLifecycleState.DRAFT,
        canRunFixture = latest != null &&
            latestLifecycle == CustomAgentToolLifecycleState.VALIDATED &&
            latest.validationStatus == CustomAgentToolValidationStatus.VALID,
        canApprove = latest != null &&
            latestLifecycle == CustomAgentToolLifecycleState.VALIDATED &&
            latest.validationStatus == CustomAgentToolValidationStatus.VALID &&
            latest.testStatus == CustomAgentToolTestStatus.PASS,
        canEnable = !enabled && active?.approved == true,
        canDisable = enabled,
        canRollback = activeIndex >= 0 && ordered.drop(activeIndex + 1).any { it.valid && it.approved },
    )
}
