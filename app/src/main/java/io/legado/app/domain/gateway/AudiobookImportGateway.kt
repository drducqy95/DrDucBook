package io.legado.app.domain.gateway

import android.net.Uri
import io.legado.app.domain.model.AudiobookCreateRequest
import io.legado.app.domain.model.AudiobookImportPreview

interface AudiobookImportGateway {
    suspend fun scanFiles(uris: List<Uri>): AudiobookImportPreview

    suspend fun scanTree(treeUri: Uri): AudiobookImportPreview

    suspend fun createBook(request: AudiobookCreateRequest): String
}
