package io.legado.app.model.cache

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadPacingPolicyTest {

    @Test
    fun interval_usesBaseAndBoundedRandomOffset() {
        assertEquals(1_250L, DownloadPacingPolicy.intervalMillis(1_000, 500, 250))
        assertEquals(1_500L, DownloadPacingPolicy.intervalMillis(1_000, 500, 900))
    }

    @Test
    fun interval_neverBecomesNegative() {
        assertEquals(0L, DownloadPacingPolicy.intervalMillis(-1, -1, -1))
    }
}
