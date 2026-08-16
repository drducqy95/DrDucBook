package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.VbookImportGateway
import io.legado.app.domain.model.VbookImportAction
import io.legado.app.domain.model.VbookImportItemResult
import io.legado.app.domain.model.VbookImportPreview
import io.legado.app.domain.model.VbookImportReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class ImportVbookRegistryUseCase(
    private val gateway: VbookImportGateway,
) {
    suspend fun preview(input: String): VbookImportPreview {
        require(input.isNotBlank()) { "VBook registry URL or JSON file is required" }
        return gateway.preview(input.trim())
    }

    suspend fun install(
        preview: VbookImportPreview,
        selectedPluginIds: Set<String>,
        allowDowngrade: Boolean = false,
        onProgress: (completed: Int, total: Int, name: String) -> Unit = { _, _, _ -> },
    ): VbookImportReport {
        val selected = preview.items.filter { item ->
            item.pluginId in selectedPluginIds &&
                item.compatible &&
                item.action != VbookImportAction.SKIP_SAME &&
                (allowDowngrade || item.action != VbookImportAction.DOWNGRADE_WARNING)
        }
        val results = mutableListOf<VbookImportItemResult>()
        selected.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            val result = try {
                val installedName = gateway.install(item)
                VbookImportItemResult(item.pluginId, installedName, true, "Installed")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                VbookImportItemResult(
                    pluginId = item.pluginId,
                    name = item.name,
                    installed = false,
                    message = error.message ?: error.javaClass.simpleName,
                )
            }
            results += result
            onProgress(index + 1, selected.size, item.name)
        }
        return VbookImportReport(results)
    }
}
