package io.legado.app.domain.gateway

import io.legado.app.data.entities.BookSource

interface BookSourceProbeGateway {
    suspend fun probe(source: BookSource)
}
