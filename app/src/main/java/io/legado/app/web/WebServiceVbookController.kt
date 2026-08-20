package io.legado.app.web

import io.legado.app.domain.model.VbookImportAction
import io.legado.app.domain.webservice.WebServiceVbookRegistryImportResponse
import io.legado.app.domain.usecase.ImportVbookRegistryUseCase
import org.koin.core.context.GlobalContext

object WebServiceVbookController {
    suspend fun import(
        payload: String,
        commit: Boolean,
        selectedPluginIds: List<String>?,
        allowDowngrade: Boolean,
    ): WebServiceVbookRegistryImportResponse {
        val useCase = GlobalContext.get().get<ImportVbookRegistryUseCase>()
        val preview = useCase.preview(payload)
        val installable = preview.items.filter {
            it.compatible && it.action in setOf(VbookImportAction.INSTALL, VbookImportAction.UPDATE)
        }
        val selected = (selectedPluginIds?.toSet() ?: installable.map { it.pluginId }.toSet())
            .intersect(installable.map { it.pluginId }.toSet())
        val report = if (commit && selected.isNotEmpty()) {
            useCase.install(
                preview = preview,
                selectedPluginIds = selected,
                allowDowngrade = allowDowngrade,
            )
        } else null
        return WebServiceVbookRegistryImportResponse(
            sourceLabel = preview.sourceLabel,
            classification = preview.classification.name,
            total = preview.items.size,
            compatible = installable.size,
            rejected = preview.rejectedItemCount,
            selected = selected.size,
            installed = report?.installedCount ?: 0,
            failed = report?.failedCount ?: 0,
            committed = commit,
        )
    }
}
