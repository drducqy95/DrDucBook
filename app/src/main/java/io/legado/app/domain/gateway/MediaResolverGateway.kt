package io.legado.app.domain.gateway

import io.legado.app.domain.model.ResolvedBookMedia

interface MediaResolverGateway {
    suspend fun resolveBookMedia(
        bookUrl: String,
        chapterIndex: Int? = null,
    ): Result<ResolvedBookMedia>
}
