package io.legado.app.ui.vbook.importer

import androidx.compose.runtime.Composable
import io.legado.app.base.BaseComposeActivity

class VbookImportActivity : BaseComposeActivity() {

    @Composable
    override fun Content() {
        VbookImportRouteScreen(
            onBackClick = { finish() },
        )
    }
}
