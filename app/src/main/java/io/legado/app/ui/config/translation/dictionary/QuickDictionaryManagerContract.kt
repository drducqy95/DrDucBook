package io.legado.app.ui.config.translation.dictionary

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import io.legado.app.domain.model.QuickDictionaryCatalog
import io.legado.app.domain.model.QuickDictionaryEntry
import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.domain.model.QuickDictionaryUniverse
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class QuickDictionaryProjectUi(
    val key: String,
    val name: String,
    val author: String,
)

@Stable
data class QuickDictionaryPackUi(
    val id: String,
    val name: String,
    val type: QuickDictionaryType,
    val scope: QuickDictionaryScope,
    val scopeKey: String,
    val entryCount: Int,
    val indexBytes: Long,
)

@Stable
data class QuickDictionaryRowUi(
    val id: Long? = null,
    val catalogId: String? = null,
    val raw: String,
    val hanViet: String = "",
    val target: String = "",
    val type: QuickDictionaryType,
    val bundled: Boolean = false,
)

@Stable
data class QuickDictionaryEditorUi(
    val id: Long? = null,
    val raw: String = "",
    val hanViet: String = "",
    val target: String = "",
    val type: QuickDictionaryType,
    val scope: QuickDictionaryScope,
    val scopeKey: String,
    val saving: Boolean = false,
    @StringRes val errorRes: Int? = null,
)

@Stable
data class QuickDictionaryManagerUiState(
    val loading: Boolean = true,
    val catalogs: ImmutableList<QuickDictionaryCatalog> = persistentListOf(),
    val packs: ImmutableList<QuickDictionaryPackUi> = persistentListOf(),
    val rows: ImmutableList<QuickDictionaryRowUi> = persistentListOf(),
    val totalCustomEntries: Int = 0,
    val projects: ImmutableList<QuickDictionaryProjectUi> = persistentListOf(),
    val universes: ImmutableList<QuickDictionaryUniverse> = persistentListOf(),
    val selectedType: QuickDictionaryType = QuickDictionaryType.VIETPHRASE,
    val selectedCatalogId: String = "",
    val selectedScope: QuickDictionaryScope = QuickDictionaryScope.GLOBAL,
    val selectedScopeKey: String = "",
    val searchQuery: String = "",
    val editor: QuickDictionaryEditorUi? = null,
    val selectionText: String? = null,
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val importing: Boolean = false,
    val importProcessed: Int = 0,
    val importSucceeded: Int = 0,
    val importProcessedBytes: Long = 0,
    val importTotalBytes: Long = 0,
    val importDuplicates: Int = 0,
)

sealed interface QuickDictionaryManagerIntent {
    data class Initialize(
        val projectKey: String?,
        val initialText: String?,
    ) : QuickDictionaryManagerIntent

    data class SelectType(val type: QuickDictionaryType) : QuickDictionaryManagerIntent
    data class SelectCatalog(val id: String, val type: QuickDictionaryType) : QuickDictionaryManagerIntent
    data class SelectScope(val scope: QuickDictionaryScope) : QuickDictionaryManagerIntent
    data class SelectScopeKey(val key: String) : QuickDictionaryManagerIntent
    data class Search(val query: String) : QuickDictionaryManagerIntent
    data object Add : QuickDictionaryManagerIntent
    data class Edit(val row: QuickDictionaryRowUi) : QuickDictionaryManagerIntent
    data object CloseEditor : QuickDictionaryManagerIntent
    data class UpdateRaw(val value: String) : QuickDictionaryManagerIntent
    data class UpdateHanViet(val value: String) : QuickDictionaryManagerIntent
    data class UpdateTarget(val value: String) : QuickDictionaryManagerIntent
    data class UpdateEditorType(val type: QuickDictionaryType) : QuickDictionaryManagerIntent
    data class UpdateEditorScope(val scope: QuickDictionaryScope) : QuickDictionaryManagerIntent
    data class UpdateEditorScopeKey(val key: String) : QuickDictionaryManagerIntent
    data object Save : QuickDictionaryManagerIntent
    data class Delete(val id: Long) : QuickDictionaryManagerIntent
    data object RequestImportFile : QuickDictionaryManagerIntent
    data object OpenDownloadCatalog : QuickDictionaryManagerIntent
    data class ImportFile(val fileName: String?, val localPath: String) : QuickDictionaryManagerIntent
    data object ImportFileFailed : QuickDictionaryManagerIntent
    data class DeletePack(val id: String) : QuickDictionaryManagerIntent
    data class UpdateSelection(val start: Int, val end: Int) : QuickDictionaryManagerIntent
    data object AddSelection : QuickDictionaryManagerIntent
    data object CloseSelection : QuickDictionaryManagerIntent
}

sealed interface QuickDictionaryManagerEffect {
    data object OpenImportFile : QuickDictionaryManagerEffect
    data class OpenUrl(val url: String) : QuickDictionaryManagerEffect
    data class ShowMessage(
        @StringRes val messageRes: Int,
        val count: Int? = null,
        val secondaryCount: Int? = null,
    ) : QuickDictionaryManagerEffect
}

internal fun QuickDictionaryEntry.toManagerRow() = QuickDictionaryRowUi(
    id = id,
    raw = raw,
    hanViet = hanViet,
    target = target,
    type = type,
)
