package io.legado.app.ui.vbook.importer

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.VbookCapability
import io.legado.app.domain.model.VbookImportAction
import io.legado.app.domain.model.VbookPluginKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Stable
data class VbookImportItemUi(
    val pluginId: String,
    val name: String,
    val author: String,
    val version: Int,
    val description: String,
    val iconUrl: String,
    val declaredKind: VbookPluginKind,
    val capabilities: ImmutableSet<VbookCapability>,
    val action: VbookImportAction,
    val compatible: Boolean,
    val compatibilityMessage: String?,
)

@Stable
data class VbookImportUiState(
    val input: String = DEFAULT_VBOOK_REGISTRY_URL,
    val searchQuery: String = "",
    val loading: Boolean = false,
    val installing: Boolean = false,
    val progressCompleted: Int = 0,
    val progressTotal: Int = 0,
    val progressName: String = "",
    val sourceLabel: String = "",
    val rejectedItemCount: Int = 0,
    val items: ImmutableList<VbookImportItemUi> = persistentListOf(),
    val selectedPluginIds: ImmutableSet<String> = persistentSetOf(),
)

sealed interface VbookImportIntent {
    data class ChangeInput(val value: String) : VbookImportIntent
    data class ChangeSearch(val value: String) : VbookImportIntent
    data object PickJsonFile : VbookImportIntent
    data class FileSelected(val uri: String) : VbookImportIntent
    data object Preview : VbookImportIntent
    data class TogglePlugin(val pluginId: String) : VbookImportIntent
    data object SelectAllInstallable : VbookImportIntent
    data object ClearSelection : VbookImportIntent
    data object InstallSelected : VbookImportIntent
}

sealed interface VbookImportEffect {
    data object OpenJsonFilePicker : VbookImportEffect
    data class ShowMessage(val message: String) : VbookImportEffect
}

const val DEFAULT_VBOOK_REGISTRY_URL =
    "https://www.vbookext.me/api/registry/vbook-fd1246b6.json"
