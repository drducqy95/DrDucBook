package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.VbookImportGateway
import io.legado.app.domain.model.ImportClassification
import io.legado.app.domain.model.VbookImportAction
import io.legado.app.domain.model.VbookImportPreview
import io.legado.app.domain.model.VbookImportPreviewItem
import io.legado.app.domain.model.VbookPluginKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportVbookRegistryUseCaseTest {

    @Test
    fun partialFailureKeepsSuccessfulPluginsAndSkipsUnsafeActions() = runBlocking {
        val gateway = FakeGateway(failId = "broken")
        val useCase = ImportVbookRegistryUseCase(gateway)
        val preview = VbookImportPreview(
            classification = ImportClassification.REGISTRY,
            sourceLabel = "registry",
            items = listOf(
                item("good", VbookImportAction.INSTALL),
                item("broken", VbookImportAction.UPDATE),
                item("same", VbookImportAction.SKIP_SAME),
                item("older", VbookImportAction.DOWNGRADE_WARNING),
                item("tts", VbookImportAction.INSTALL, compatible = false),
            ),
        )

        val report = useCase.install(
            preview = preview,
            selectedPluginIds = preview.items.map { it.pluginId }.toSet(),
        )

        assertEquals(listOf("good", "broken"), gateway.attempts)
        assertEquals(1, report.installedCount)
        assertEquals(1, report.failedCount)
    }

    private fun item(
        id: String,
        action: VbookImportAction,
        compatible: Boolean = true,
    ) = VbookImportPreviewItem(
        pluginId = id,
        name = id,
        author = "author",
        version = 1,
        description = "",
        iconUrl = "",
        downloadUrl = "https://example.com/$id.zip",
        declaredKind = VbookPluginKind.TEXT,
        capabilities = emptySet(),
        action = action,
        compatible = compatible,
    )

    private class FakeGateway(
        private val failId: String,
    ) : VbookImportGateway {
        val attempts = mutableListOf<String>()

        override suspend fun preview(input: String): VbookImportPreview = error("unused")

        override suspend fun install(item: VbookImportPreviewItem): String {
            attempts += item.pluginId
            if (item.pluginId == failId) error("planned failure")
            return item.name
        }
    }
}
