package io.legado.app.domain.gateway

import io.legado.app.domain.model.VbookImportPreview
import io.legado.app.domain.model.VbookImportPreviewItem

interface VbookImportGateway {
    suspend fun preview(input: String): VbookImportPreview

    suspend fun install(item: VbookImportPreviewItem): String
}
