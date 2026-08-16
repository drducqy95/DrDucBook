package io.legado.app.data.repository

import io.legado.app.domain.gateway.MediaDownloadGateway
import io.legado.app.domain.model.AccountQuotaKind
import io.legado.app.domain.model.MediaDownloadRequest
import io.legado.app.domain.usecase.AccountEntitlementUseCase

class EntitledMediaDownloadGateway(
    private val delegate: MediaDownloadGateway,
    private val accountEntitlementUseCase: AccountEntitlementUseCase,
) : MediaDownloadGateway by delegate {

    override suspend fun enqueue(request: MediaDownloadRequest): String {
        accountEntitlementUseCase.consume(
            AccountQuotaKind.DOWNLOAD_CONTENT,
            listOf(request.bookUrl),
        )
        return delegate.enqueue(request)
    }
}
