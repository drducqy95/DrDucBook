package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.MediaResolverGateway
import io.legado.app.domain.model.ResolvedBookMedia

class ResolveBookMediaUseCase(
    private val mediaResolverGateway: MediaResolverGateway,
) {
    suspend fun execute(
        bookUrl: String,
        chapterIndex: Int? = null,
    ): Result<ResolvedBookMedia> {
        return mediaResolverGateway.resolveBookMedia(bookUrl, chapterIndex)
    }
}
