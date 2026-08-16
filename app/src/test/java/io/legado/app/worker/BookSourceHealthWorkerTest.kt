package io.legado.app.worker

import org.junit.Assert.assertEquals
import org.junit.Test
import io.legado.app.domain.sourcehealth.SourceCheckProfile

class BookSourceHealthWorkerTest {

    @Test
    fun buildInputDataStaysEmptyForDashboardChecks() {
        assertEquals(
            SourceCheckProfile.QUICK.name,
            BookSourceHealthWorker.buildInputData(null).getString(BookSourceHealthWorker.KEY_PROFILE),
        )
        assertEquals(null, BookSourceHealthWorker.buildInputData(null).getString(BookSourceHealthWorker.KEY_SOURCE_URL))
        assertEquals(
            BookSourceHealthWorker.MANUAL_WORK_NAME + ":" + SourceCheckProfile.QUICK.name,
            BookSourceHealthWorker.manualWorkName(null),
        )
    }

    @Test
    fun buildInputDataCarriesTargetSourceUrl() {
        val sourceUrl = "https://example.com/source"

        val inputData = BookSourceHealthWorker.buildInputData(
            sourceUrl = sourceUrl,
            profile = SourceCheckProfile.STANDARD,
        )

        assertEquals(sourceUrl, inputData.getString(BookSourceHealthWorker.KEY_SOURCE_URL))
        assertEquals(
            SourceCheckProfile.STANDARD.name,
            inputData.getString(BookSourceHealthWorker.KEY_PROFILE),
        )
        assertEquals(
            BookSourceHealthWorker.MANUAL_WORK_NAME + ":" + SourceCheckProfile.STANDARD.name + ":" + sourceUrl.hashCode(),
            BookSourceHealthWorker.manualWorkName(sourceUrl, SourceCheckProfile.STANDARD),
        )
    }
}
