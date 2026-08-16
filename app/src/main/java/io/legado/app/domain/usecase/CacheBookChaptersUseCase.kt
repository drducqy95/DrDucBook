package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.BookCacheDownloadGateway
import io.legado.app.model.cache.CacheDownloadRequest
import io.legado.app.model.cache.CacheDownloadSource
import io.legado.app.model.cache.ChapterSelection
import io.legado.app.domain.model.AccountQuotaKind

class CacheBookChaptersUseCase(
    private val bookCacheDownloadGateway: BookCacheDownloadGateway,
    private val accountEntitlementUseCase: AccountEntitlementUseCase,
) {

    suspend fun execute(bookUrl: String, chapterIndices: Iterable<Int>): Int {
        val indices = chapterIndices.distinct()
        if (indices.isEmpty()) return 0
        accountEntitlementUseCase.consume(AccountQuotaKind.DOWNLOAD_CONTENT, listOf(bookUrl))
        bookCacheDownloadGateway.start(
            CacheDownloadRequest(
                bookUrl = bookUrl,
                selection = ChapterSelection.Indices(indices.toSet()),
                source = CacheDownloadSource.Manual,
            )
        )
        return indices.size
    }

    suspend fun executeRange(bookUrl: String, startIndex: Int, endIndex: Int): Int {
        if (endIndex < startIndex) return 0
        accountEntitlementUseCase.consume(AccountQuotaKind.DOWNLOAD_CONTENT, listOf(bookUrl))
        bookCacheDownloadGateway.start(
            CacheDownloadRequest(
                bookUrl = bookUrl,
                selection = ChapterSelection.Range(startIndex, endIndex),
                source = CacheDownloadSource.Manual,
            )
        )
        return endIndex - startIndex + 1
    }
}
