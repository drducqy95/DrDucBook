package io.legado.app.domain.gateway

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.domain.sourcehealth.SourceCheckProbeResult
import io.legado.app.domain.sourcehealth.SourceCheckProfile

interface BookSourceHealthProbeGateway {
    suspend fun probe(
        source: BookSource,
        profile: SourceCheckProfile = SourceCheckProfile.FULL,
    ): SourceCheckProbeResult
}

interface RssSourceHealthProbeGateway {
    suspend fun probe(
        source: RssSource,
        profile: SourceCheckProfile = SourceCheckProfile.FULL,
    ): SourceCheckProbeResult
}

interface VbookSourceHealthProbeGateway {
    fun supports(source: BookSource): Boolean

    suspend fun probe(
        source: BookSource,
        profile: SourceCheckProfile = SourceCheckProfile.FULL,
    ): SourceCheckProbeResult
}
