package io.legado.app.ui.vbook.importer

import android.app.Application
import io.legado.app.domain.gateway.VbookImportGateway
import io.legado.app.domain.model.ImportClassification
import io.legado.app.domain.model.VbookImportAction
import io.legado.app.domain.model.VbookImportPreview
import io.legado.app.domain.model.VbookImportPreviewItem
import io.legado.app.domain.model.VbookPluginKind
import io.legado.app.domain.usecase.ImportVbookRegistryUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class VbookImportViewModelTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
    }

    @Test
    fun loadPreviewDoesNotAutoSelectPlugins() = runBlocking {
        val preview = VbookImportPreview(
            classification = ImportClassification.REGISTRY,
            sourceLabel = "test_registry",
            items = listOf(
                createItem("plugin_1", VbookImportAction.INSTALL),
                createItem("plugin_2", VbookImportAction.UPDATE),
            ),
        )
        val gateway = object : VbookImportGateway {
            override suspend fun preview(input: String): VbookImportPreview = preview
            override suspend fun install(item: VbookImportPreviewItem): String = item.name
        }
        val useCase = ImportVbookRegistryUseCase(gateway)
        val viewModel = VbookImportViewModel(application, useCase)

        viewModel.onIntent(VbookImportIntent.ChangeInput("https://example.com/registry.json"))
        viewModel.onIntent(VbookImportIntent.Preview)

        // Wait until loaded
        var attempts = 0
        while (viewModel.uiState.value.items.isEmpty() && attempts < 50) {
            kotlinx.coroutines.delay(50)
            attempts++
        }

        val state = viewModel.uiState.value
        assertEquals(2, state.items.size)
        // User requirement: preview does NOT auto-select all plugins
        assertTrue("selectedPluginIds must be empty after preview", state.selectedPluginIds.isEmpty())

        // User can manually toggle
        viewModel.onIntent(VbookImportIntent.TogglePlugin("plugin_1"))
        kotlinx.coroutines.delay(50)
        assertEquals(setOf("plugin_1"), viewModel.uiState.value.selectedPluginIds)

        // Or select all installable
        viewModel.onIntent(VbookImportIntent.SelectAllInstallable)
        kotlinx.coroutines.delay(50)
        assertEquals(setOf("plugin_1", "plugin_2"), viewModel.uiState.value.selectedPluginIds)

        // Or clear selection
        viewModel.onIntent(VbookImportIntent.ClearSelection)
        kotlinx.coroutines.delay(50)
        assertTrue(viewModel.uiState.value.selectedPluginIds.isEmpty())
    }

    private fun createItem(
        id: String,
        action: VbookImportAction,
        compatible: Boolean = true,
    ) = VbookImportPreviewItem(
        pluginId = id,
        name = "Plugin $id",
        author = "Author",
        version = 1,
        description = "Description",
        iconUrl = "",
        downloadUrl = "https://example.com/$id.zip",
        declaredKind = VbookPluginKind.TEXT,
        capabilities = emptySet(),
        action = action,
        compatible = compatible,
    )
}
