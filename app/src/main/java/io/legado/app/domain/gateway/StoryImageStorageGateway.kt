package io.legado.app.domain.gateway

interface StoryImageStorageGateway {
    suspend fun save(
        bookUrl: String,
        subjectKey: String,
        bytes: ByteArray,
        mimeType: String,
    ): String
}
